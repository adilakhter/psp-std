package psp
package std

import api._

object Eq {
  final class Impl[-A](val f: (A, A) => Boolean) extends AnyVal with Eq[A] { def equiv(x: A, y: A) = f(x, y) }
  def native[A](): Impl[A]                    = new Impl[A](_ == _)
  def apply[A](f: (A, A) => Boolean): Impl[A] = new Impl[A](f)
}
object HashEq {
  def apply[A](cmp: (A, A) => Boolean, hashFn: A => Int): Impl[A] = new Impl[A](cmp, hashFn)

  def universal[A]           = apply[A](_ == _, _.##)
  def reference[A <: AnyRef] = apply[A](_ eq _, System.identityHashCode)
  def shown[A: Show]         = apply[A](_.to_s == _.to_s, _.to_s.##)
  def native[A](eqs: Eq[A])  = apply[A](eqs.equiv, _.##)

  final case class Wrap[A: HashEq](value: A) {
    override def hashCode = value.hash
    override def equals(x: Any): Boolean = x match {
      case Wrap(that) => value === that.asInstanceOf[A]
      case _          => false
    }
    override def toString = pp"$value"
  }
  final class Impl[-A](cmp: (A, A) => Boolean, h: A => Int) extends HashEq[A] {
    def equiv(x: A, y: A) = cmp(x, y)
    def hash(x: A)        = h(x)
  }
}
object OrderEq {
  final class Impl[-A](val f: (A, A) => Cmp) extends AnyVal with OrderEq[A] {
    def equiv(x: A, y: A)   = f(x, y) == Cmp.EQ
    def compare(x: A, y: A) = f(x, y)
  }
  def apply[A](f: (A, A) => Cmp): OrderEq[A] = new Impl[A](f)
}
object PartialOrderEq {
  final class Impl[-A](val f: (A, A) => PCmp) extends AnyVal with PartialOrderEq[A] {
    def equiv(x: A, y: A)          = f(x, y) == PCmp.EQ
    def partialCompare(x: A, y: A) = f(x, y)
  }
  def apply[A](f: (A, A) => PCmp): PartialOrderEq[A] = new Impl[A](f)
}
