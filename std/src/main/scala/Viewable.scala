package psp
package std

import api._, exp._, StdShow._

/** Op and Operable
 *
 *  It's tricky to abstract smoothly over the type constructor of a collection
 *  and its elements simultaneously.
 */
sealed trait Op[-A, +B] extends ShowSelf {
  def to_s = this.apply[Op.ConstString]("")(Operable.OperableString)
}

trait Operable[M[X]] {
  def apply[A, B](xs: M[A])(op: Op[A, B]): M[B]
}

object Op {
  type ConstString[X] = String

  implicit class OpOps[A, B](op: Op[A, B]) {
    def apply[M[X]](xs: M[A])(implicit z: Operable[M]): M[B] = z(xs)(op)
    def ~[C](that: Op[B, C]): Op[A, C]                       = Compose[A, B, C](op, that)
  }
  implicit def liftPositional[A](x: Positional): Pos[A] = Pos[A](x)

  sealed trait Positional
  final case class Take(n: Precise)        extends Positional
  final case class Drop(n: Precise)        extends Positional
  final case class TakeRight(n: Precise)   extends Positional
  final case class DropRight(n: Precise)   extends Positional
  final case class Slice(range: VdexRange) extends Positional

  final case class Initial[A](label: String)  extends Op[A, A]
  final case class Pos[A](op: Positional)     extends Op[A, A]
  final case class TakeWhile[A](p: ToBool[A]) extends Op[A, A]
  final case class DropWhile[A](p: ToBool[A]) extends Op[A, A]
  final case class Filter[A](p: ToBool[A])    extends Op[A, A]

  final case class Collect[A, B](pf: A ?=> B)                 extends Op[A, B]
  final case class Maps[A, B](f: A => B)                      extends Op[A, B]
  final case class FlatMap[A, B](f: A => View[B])             extends Op[A, B]
  final case class Compose[A, B, C](p: Op[A, B], q: Op[B, C]) extends Op[A, C]
}

object Operable {
  import Op._, all._

  implicit object OperableString extends Operable[ConstString] {
    def str(in: String, name: String, arg: Any): String = {
      val arg_s = arg match {
        case x: ShowDirect           => x.to_s
        case x: scala.Function1[_,_] => "<f>"
        case _                       => "" + arg
      }
      "%s %7s %-8s".format(in, name, arg_s)
    }

    def apply[A, B](in: String)(op: Op[A, B]): String = op match {
      case Initial(label)    => label
      case Pos(Take(n))      => str(in, "take", n)
      case Pos(Drop(n))      => str(in, "drop", n)
      case Pos(TakeRight(n)) => str(in, "takeR", n)
      case Pos(DropRight(n)) => str(in, "dropR", n)
      case Pos(Slice(range)) => str(in, "slice", range)
      case TakeWhile(p)      => str(in, "takeW", p)
      case DropWhile(p)      => str(in, "dropW", p)
      case Filter(p)         => str(in, "filter", p)
      case Collect(pf)       => str(in, "collect", pf)
      case Maps(f)           => str(in, "map", f)
      case FlatMap(f)        => str(in, "flatMap", f)
      case Compose(o1, o2)   => apply(apply(in)(o1))(o2)
    }
  }
  // No matter what hints we provide, scala won't figure out that
  // for a particular Op[A, B], A and B are the same type. So we
  // always have to cast.
  implicit object OperableView extends Operable[View] {
    def apply[A, B](xs: View[A])(op: Op[A, B]): View[B] = {
      val res: View[_] = op match {
        case Initial(_)        => xs
        case Pos(Take(n))      => xs take n
        case Pos(Drop(n))      => xs drop n
        case Pos(TakeRight(n)) => xs takeRight n
        case Pos(DropRight(n)) => xs dropRight n
        case Pos(Slice(range)) => xs slice range
        case TakeWhile(p)      => xs takeWhile p
        case DropWhile(p)      => xs dropWhile p
        case Filter(p)         => xs filter p
        case Collect(pf)       => xs collect pf
        case Maps(f)           => xs map f
        case FlatMap(f)        => xs flatMap f
        case Compose(o1, o2)   => apply(apply(xs)(o1))(o2)
      }
      cast(res)
    }
  }
}