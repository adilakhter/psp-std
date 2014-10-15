package psp
package std

import SizeInfo._, api._
import lowlevel.CircularBuffer

object FlattenIndexedSlice {
  def foreachTakeWhile[A](xs: Foreach[A], f: A => Unit, p: Predicate[A]): Int = {
    var taken = 0
    xs foreach { x =>
      if (p(x)) f(x) sideEffect (taken += 1)
      else return taken
    }
    taken
  }
  def foreachDropWhile[A](xs: Foreach[A], f: A => Unit, p: Predicate[A]): Int = {
    var dropping = true
    var dropped  = 0
    xs foreach { x =>
      if (dropping && p(x)) dropped += 1
      else {
        if (dropping) dropping = false
        f(x)
      }
    }
    dropped
  }

  def unapply[A](xs: View[A]): Option[(View[A], IndexRange)] = xs match {
    case xs: IndexedView[_,_]  => Some(xs -> xs.indices)
    case LabeledView(xs, _)    => unapply(xs)
    case Sized(xs, Size(n))    => Some(xs -> indexRange(0, n))
    case Mapped(xs, f)         => unapply(xs) map { case (xs, range) => (xs map f, range) }
    case DroppedR(xs, Size(n)) => unapply(xs) map { case (xs, range) => (xs, range dropRight n) }
    case TakenR(xs, Size(n))   => unapply(xs) map { case (xs, range) => (xs, range takeRight n) }
    case Dropped(xs, Size(n))  => unapply(xs) map { case (xs, range) => (xs, range drop n) }
    case Taken(xs, Size(n))    => unapply(xs) map { case (xs, range) => (xs, range take n) }
    case Sliced(xs, indices)   => unapply(xs) map { case (xs, range) => (xs, range slice indices) }
    case _                     => xs.sizeInfo.precisely map (size => xs -> indexRange(0, size))
  }
}

final class LinearView[A0, Repr](repr: Repr, val tc: Foreachable[Repr] { type A = A0 }) extends AtomicView[A0, Repr] {
  def description = ""
  def sizeInfo    = SizeInfo.unknown
  def viewRange   = IndexRange.full

  @inline def foreach(f: A => Unit): Unit = foreachSlice(IndexRange.full)(f)
  def foreachSlice(range: IndexRange)(f: A => Unit): Unit = {
    var i = Index.zero
    (tc foreach repr) { x =>
      recordCall(x)
      if (range contains i) f(x)
      i = i.next
      if (i >= range.end) return
    }
  }
}

final class IndexedView[A0, Repr](repr: Repr, val tc: DirectAccess[Repr] { type A = A0 }, val viewRange: IndexRange) extends AtomicView[A0, Repr] with Direct[A0] with HasPreciseSize {
  def size: Precise                                       = SizeInfo.min(tc length repr, viewRange.size)
  def elemAt(i: Index): A                                 = recordCall(tc.elemAt(repr)(i))
  def contains(x: A): Boolean                             = this exists (_ == x)
  def foreach(f: A => Unit): Unit                         = foreachSlice(size.toIndexRange)(f)
  def foreachSlice(range: IndexRange)(f: A => Unit): Unit = {
    val combined = viewRange slice range
    assert(size containsRange combined, s"($repr/size=$size).foreachSlice($range)($f)")
    combined foreach (i => f(elemAt(i)))
  }
  def description                                         = ""
}

final case class LabeledView[+A, Repr](prev: BaseView[A, Repr], label: String) extends BaseView[A, Repr] {
  def foreach(f: A => Unit): Unit = prev foreach f

  def description = label
  def isAtomic    = prev.isAtomic
  def sizeInfo    = prev.sizeInfo
  def calls       = prev.calls
  def viewChain   = newSeq(this) ++ (prev.viewChain drop 1)
  def viewRange   = prev.viewRange
}

sealed trait BaseView[+A, Repr] extends Any with View[A] {
  type MapTo[+X] = BaseView[X, Repr]
  def isAtomic: Boolean
  def viewRange: IndexRange

  final def ++[A1 >: A](that: View[A1]): MapTo[A1]   = Joined(this, that)
  final def collect[B](pf: A ?=> B): MapTo[B]        = Collected(this, pf)
  final def count(p: Predicate[A]): Precise          = takeWhile(p) |> (v => v.sizeInfo match { case x: Precise => x ; case _ => v.force[pVector[A]].size })
  final def drop(n: Int): MapTo[A]                   = Dropped(this, Size(n))
  final def dropRight(n: Int): MapTo[A]              = DroppedR(this, Size(n))
  final def dropWhile(p: Predicate[A]): MapTo[A]     = DropWhile(this, p)
  final def filter(p: Predicate[A]): MapTo[A]        = Filtered(this, p)
  final def filterNot(p: Predicate[A]): MapTo[A]     = Filtered(this, (x: A) => !p(x))
  final def flatMap[B](f: A => Foreach[B]): MapTo[B] = FlatMapped(this, f)
  final def map[B](f: A => B): MapTo[B]              = Mapped(this, f)
  final def sized(size: Size): MapTo[A]              = Sized(this, size)
  final def slice(range: IndexRange): MapTo[A]       = Sliced(this, range)
  final def take(n: Int): MapTo[A]                   = Taken(this, Size(n))
  final def takeRight(n: Int): MapTo[A]              = TakenR(this, Size(n))
  final def takeWhile(p: Predicate[A]): MapTo[A]     = TakenWhile(this, p)
  final def withFilter(p: Predicate[A]): MapTo[A]    = Filtered(this, p)

  final def force[That](implicit z: Builds[A, That]): That = z build this
  final def build(implicit z: Builds[A, Repr]): Repr       = force[Repr]

  override def toString = viewChain.pvec.reverse map (_.description) mkString " "
}

sealed abstract class AtomicView[A0, Repr] extends View.Atomic[A0] with BaseView[A0, Repr] with CountCalls {
  val tc: Walkable[Repr]
  type A = tc.A
  val counter = new RecorderCounter()

  def viewRange: IndexRange
  def foreachSlice(range: IndexRange)(f: A => Unit): Unit
  def isAtomic  = true
  def viewChain: Foreach[View[_]] = newSeq(this)
}

sealed abstract class CompositeView[A, B, Repr](val description: String, val sizeEffect: Unary[SizeInfo]) extends View.Composite[A, B] with BaseView[B, Repr] {
  def prev: View[A]

  def isAtomic                    = false
  def viewChain: Foreach[View[_]] = newSeq(this) ++ prev.viewChain
  def sizeInfo                    = sizeEffect(prev.sizeInfo)

  lazy val viewRange: IndexRange = {
    def loop[C](xs: View[C]): IndexRange = xs match {
      case Dropped(xs, Size(n))           => loop(xs) drop n
      case Taken(xs, Size(n))             => loop(xs) take n
      case Sliced(xs, range)              => loop(xs) slice range
      case DropWhile(xs, p: Predicate[C]) => loop(xs) drop (xs count p).sizeValue
      case _                              => xs.viewRange
    }
    loop(this)
  }

  final def foreach(f: B => Unit): Unit = {
    def loop[C](xs: View[C])(f: C => Unit): Unit = xs match {
      case LabeledView(xs, _)              => loop[C](xs)(f)
      case Sized(xs, size)                 => loop[C](xs)(f)
      case Mapped(xs, g)                   => loop(xs)(g andThen f)
      case FlatMapped(xs, g)               => loop(xs)(x => g(x) foreach f)
      case Filtered(xs, p: Predicate[C])   => loop(xs)(x => if (p(x)) f(x))
      case TakenWhile(xs, p: Predicate[C]) => FlattenIndexedSlice.foreachTakeWhile(xs, f, p)
      case DropWhile(xs, p: Predicate[C])  => FlattenIndexedSlice.foreachDropWhile(xs, f, p) //loop(Dropped(xs, xs count p))(f)
      case Collected(xs, pf)               => loop(xs)(x => if (pf isDefinedAt x) f(pf(x)))
      case FlattenIndexedSlice(xs, range)  => foreachSlice(xs, f, range)
      case Joined(xs, ys)                  => loop(xs)(f) ; loop(ys)(f)
      case DroppedR(xs, Size(n))           => foreachDropRight(xs, f, Size(n))
      case TakenR(xs, Size(n))             => foreachTakeRight(xs, f, Size(n))
      case Dropped(xs, Size(n))            => foreachSlice(xs, f, IndexRange.full drop n)
      case Taken(xs, Size(n))              => foreachSlice(xs, f, IndexRange.full take n)
      case Sliced(xs, range)               => foreachSlice(xs, f, range)
      case xs: View[_]                     => xs foreach f
      case xs                              => abort(pp"Unexpected view class ${xs.shortClass}")
    }

    if (!sizeInfo.isZero)
      loop(this)(f)
  }

  private def foreachSlice[A](xs: View[A], f: A => Unit, range: IndexRange): Unit = {
    var i = Index.zero
    def isDone = range.end <= i
    def runThrough(xs: Foreach[A]): Unit = {
      xs foreach { x =>
        if (range contains i) f(x)
        i = i.next
        if (isDone) return
      }
    }

    xs match {
      case xs: AtomicView[_, _] => xs.foreachSlice(range)(f.castTo[xs.A => Unit])
      case m: Mapped[a, _, _]   => foreachSlice[a](m.prev, m.f andThen f, range)
      case xs: Direct[A]        => range foreach (i => f(xs elemAt i))
      case Joined(ys1, ys2) =>
        ys1.sizeInfo match {
          case Precise(n) if n < range.start.indexValue   => ys2 slice (range << n) foreach f
          case Precise(n) if n.size.lastIndex > range.end => ys1 slice range foreach f
          case _                                          => runThrough(ys1) ; if (!isDone) runThrough(ys2)
        }

      case _              => runThrough(xs)
    }
  }

  private def foreachTakeRight[A](xs: Foreach[A], f: A => Unit, n: Size): Unit =
    (CircularBuffer[A](n) ++= xs) foreach f

  private def foreachDropRight[A](xs: Foreach[A], f: A => Unit, n: Size): Unit =
    xs.foldl(CircularBuffer[A](n))((buf, x) => if (buf.isFull) try buf finally f(buf push x) else buf += x)

  def calls = this match {
    case Joined(xs, ys) => xs.calls + ys.calls
    case _              => prev.calls
  }
}

final case class Sized      [A   , Repr](prev: View[A], size: Size)         extends CompositeView[A, A, Repr](pp"sized $size",  _ => size)
final case class Joined     [A   , Repr](prev: View[A], ys: View[A])        extends CompositeView[A, A, Repr](pp"++ $ys",       _ + ys.sizeInfo)
final case class Filtered   [A   , Repr](prev: View[A], p: Predicate[A])    extends CompositeView[A, A, Repr](pp"filter $p",    _.atMost)
final case class Sliced     [A   , Repr](prev: View[A], range: IndexRange)  extends CompositeView[A, A, Repr](pp"slice $range", _ slice range)
final case class Dropped    [A   , Repr](prev: View[A], n: Size)            extends CompositeView[A, A, Repr](pp"drop $n",      _ - n)
final case class DroppedR   [A   , Repr](prev: View[A], n: Size)            extends CompositeView[A, A, Repr](pp"dropR $n",     _ - n)
final case class Taken      [A   , Repr](prev: View[A], n: Size)            extends CompositeView[A, A, Repr](pp"take $n",      _ min n)
final case class TakenR     [A   , Repr](prev: View[A], n: Size)            extends CompositeView[A, A, Repr](pp"takeR $n",     _ min n)
final case class TakenWhile [A   , Repr](prev: View[A], p: Predicate[A])    extends CompositeView[A, A, Repr](pp"takeW $p",     _.atMost)
final case class DropWhile  [A   , Repr](prev: View[A], p: Predicate[A])    extends CompositeView[A, A, Repr](pp"dropW $p",     _.atMost)
final case class Mapped     [A, B, Repr](prev: View[A], f: A => B)          extends CompositeView[A, B, Repr](pp"map $f",       x => x)
final case class FlatMapped [A, B, Repr](prev: View[A], f: A => Foreach[B]) extends CompositeView[A, B, Repr](pp"flatMap $f",   x => if (x.isZero) x else Unknown)
final case class Collected  [A, B, Repr](prev: View[A], pf: A ?=> B)        extends CompositeView[A, B, Repr](pp"collect $pf",  _.atMost)
