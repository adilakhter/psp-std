package psp
package std
package ops

import api._, StdEq._, StdShow._
import java.io.BufferedInputStream

final class DocSeqOps(xs: Direct[Doc]) {
  def joinLines: String = xs mapNow (_.render) mk_s EOL
}
final class DocOps(val lhs: Doc) extends AnyVal {
  def doc: Doc                             = lhs
  def render(implicit z: Renderer): String = z show lhs
  def isEmpty: Boolean                     = lhs eq emptyValue[Doc]

  def ~(rhs: Doc): Doc   = Doc.Cat(lhs, rhs)
  def <>(rhs: Doc): Doc  = if (lhs.isEmpty) rhs else if (rhs.isEmpty) lhs else lhs ~ rhs
  def <+>(rhs: Doc): Doc = if (lhs.isEmpty) rhs else if (rhs.isEmpty) lhs else lhs ~ " " ~ rhs
}
final class ExMapOps[K, V](xs: ExMap[K, V]) {
  type Entry = K -> V

  def keys: View[K]          = keySet.m
  def values: View[V]        = keyVector map xs.lookup
  def keySet: ExSet[K]       = xs.lookup.keys
  def keyVector: Vec[K]      = keys.toVec
  def entries: ZipView[K, V] = keyVector mapZip xs.lookup

  def filterValues(p: ToBool[V]): ExMap[K, V] = xs filterKeys (k => p(xs(k)))
}
final class ExSetOps[A](xs: ExSet[A]) {
  def add(x: A): ExSet[A]                = xs + x
  def filter(p: ToBool[A]): ExSet[A]     = ExSet.Filtered(xs, p)
  def union(that: ExSet[A]): ExSet[A]    = ExSet.Union(xs, that)
  def mapOnto[B](f: A => B): ExMap[A, B] = ExMap(xs, Fun(f))
}

final class TimesBuilder(val times: Precise) {
  def const[A](elem: A): Each[A]   = Each const elem take times
  def eval[A](body: => A): Each[A] = Each continually body take times
}

final class PreciseOps(val size: Precise) {
  def toInt: Int          = size.getInt
  def times               = new TimesBuilder(size)
  def indices: IndexRange = indexRange(0, size.getInt)
  def lastIndex: Index    = Index(size.get - 1)  // effectively maps both undefined and zero to no index.

  def + (n: Precise): Precise              = size + n.get
  def - (n: Precise): Precise              = size - n.get
  def containsIndex(index: Index): Boolean = indices containsInt index.getInt

  def min(rhs: Precise): Precise = if (size <= rhs) size else rhs
  def max(rhs: Precise): Precise = if (size >= rhs) size else rhs

  @inline def foreachIndex(f: Index => Unit): Unit  = if (size.get > 0L) lowlevel.ll.foreachConsecutive(0, lastIndex.getInt, i => f(Index(i)))
  @inline def foreachIntIndex(f: Int => Unit): Unit = if (size.get > 0L) lowlevel.ll.foreachConsecutive(0, lastIndex.getInt, f)
}

final class InputStreamOps(val in: InputStream) extends AnyVal {
  def buffered: BufferedInputStream = in match {
    case in: BufferedInputStream => in
    case _                       => new BufferedInputStream(in)
  }
  def slurp(): Array[Byte]             = lowlevel.Streams slurp buffered
  def slurp(len: Precise): Array[Byte] = lowlevel.Streams.slurp(buffered, len)
}

final class SizeOps(val lhs: Size) extends AnyVal {
  import Size._, StdEq._

  def getInt: Int = lhs match {
    case Finite(n) => n.toInt
    case s         => illegalArgumentException(s)
  }
  def isNonZero     = loBound =!= Zero
  def isZero        = lhs === Zero
  def atLeast: Size = Size.Range(lhs, Infinite)
  def atMost: Size  = Size.Range(Zero, lhs)

  def loBound: Atomic = lhs match {
    case Bounded(lo, _) => lo
    case x: Atomic      => x
  }

  def slice(range: IndexRange): Size = (this - range.startInt) min range.size

  /** For instance taking the union of two sets. The new size is
   *  at least the size of the larger operand, but at most the sum
   *  of the two sizes.
   */
  def union(rhs: Size): Size     = Size.Range(lhs max rhs, lhs + rhs)
  def intersect(rhs: Size): Size = Size.Range(Size.Zero, lhs min rhs)
  def diff(rhs: Size): Size      = Size.Range(lhs - rhs, lhs)

  def + (rhs: Size): Size = (lhs, rhs) match {
    case (Finite(l), Finite(r))                   => Finite(l + r)
    case (Infinite, Finite(_))                    => Infinite
    case (Finite(_), Infinite)                    => Infinite
    case (Infinite, Infinite)                     => Infinite
    case (Size.Range(l1, h1), Size.Range(l2, h2)) => Size.Range(l1 + l2, h1 + h2)
  }
  def - (rhs: Size): Size = (lhs, rhs) match {
    case (Finite(l), Finite(r))                   => Finite(l - r)
    case (Finite(_), Infinite)                    => Zero
    case (Infinite, Finite(_))                    => Infinite
    case (Infinite, Infinite)                     => Unknown
    case (Size.Range(l1, h1), Size.Range(l2, h2)) => Size.Range(l1 - h2, h1 - l2)
  }
  def min(rhs: Size): Size = Size.min(lhs, rhs)
  def max(rhs: Size): Size = Size.max(lhs, rhs)
}

final class FunOps[A, B](val f: Fun[A, B]) extends AnyVal {
  outer =>

  def get(x: A): Option[B]            = if (f isDefinedAt x) Some(f(x)) else None
  def getOr(key: A, alt: => B): B     = get(key) getOrElse alt
  def orElse(g: Fun[A, B]): Fun[A, B] = OrElse(f, g)
  def mapIn[C](g: C => A): Fun[C, B]  = AndThen(Fun(g), f)
  def mapOut[C](g: B => C): Fun[A, C] = AndThen(f, Fun(g))

  def defaulted(g: A => B): Defaulted[A, B] = f match {
    case Defaulted(_, u) => Defaulted(g, u)
    case _               => Defaulted(g, f)
  }

  def filterIn(p: A => Boolean): FilterIn[A, B] = f match {
    case FilterIn(p0, u) => FilterIn(x => p0(x) && p(x), u)
    case _               => FilterIn(p, f)
  }

  def traced(in: A => Unit, out: B => Unit): Fun[A, B] = ( f
    .   mapIn[A] { x => in(x) ; x }
    .  mapOut[B] { x => out(x) ; x }
  )
  def memoized: Fun[A, B] = {
    val cache = scala.collection.mutable.Map[A, B]()
    Opaque[A, B](x => cache.getOrElseUpdate(x, f(x))) filterIn (x => (cache contains x) || (f isDefinedAt x))
  }
}
