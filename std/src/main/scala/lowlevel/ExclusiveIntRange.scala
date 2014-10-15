package psp
package std
package lowlevel

import api._
import ExclusiveIntRange._

final class ExclusiveIntRange private (val bits: Long) extends AnyVal with Direct[Int] {
  def start: Int   = bits.lbits
  def end: Int     = bits.rbits
  def last: Int    = end - step
  def step: Int    = math.signum(end - start)
  def length: Int  = math.abs(end - start)
  def size         = Precise(length)
  def isDescending = end < start
  def isAscending  = start < end

  private def nSteps(n: Int): Int = step * n

  def reverse: ExclusiveIntRange               = create(end - step, start - step)
  def drop(n: Int): ExclusiveIntRange          = if (n <= 0) this else if (n >= length) empty else create(start + nSteps(n), end)
  def dropRight(n: Int): ExclusiveIntRange     = if (n <= 0) this else if (n >= length) empty else create(start, end - nSteps(n))
  def take(n: Int): ExclusiveIntRange          = if (n <= 0) empty else if (n >= length) this else create(start, start + nSteps(n))
  def takeRight(n: Int): ExclusiveIntRange     = if (n <= 0) empty else if (n >= length) this else create(end - nSteps(n), end)
  def slice(s: Int, e: Int): ExclusiveIntRange = if (e <= 0 || e <= s) empty else drop(s) take (e - s)
  def slice(r: IndexRange): ExclusiveIntRange  = slice(r.start.indexValue, r.end.indexValue)

  def suffixLength(p: Predicate[Int]): Int = reverse prefixLength p
  def prefixLength(p: Predicate[Int]): Int = {
    var elem = start
    var result = 0
    while (elem != end) {
      if (!p(elem)) return result
      result += 1
      elem += step
    }
    result
  }
  def dropWhile(p: Int => Boolean): ExclusiveIntRange = drop(prefixLength(p))
  def takeWhile(p: Int => Boolean): ExclusiveIntRange = {
    var cur = start
    while (cur < end && p(cur)) cur += 1
    start until cur
  }
  def >> (n: Int): ExclusiveIntRange = create(start + n, end + n)
  def << (n: Int): ExclusiveIntRange = create(start - n, end - n)

  def elemAt(i: Index): Int = start + i.indexValue
  def contains(x: Int): Boolean = if (isAscending) start <= x && x < end else start >= x && x > end

  @inline def foreach(f: Int => Unit): Unit = foreachInt(start, end, step, f)

  override def toString = if (start == end) "<empty>" else s"[$start..$end)"
}

object ExclusiveIntRange {
  val empty = new ExclusiveIntRange(0L)

  /** Can't refer directly to fields because scala bloats all the bytecode
   *  going through getters.
   */
  @inline def foreachInt(start: Int, end: Int, step: Int, f: Int => Unit): Unit = {
    var elem = start
    while (elem != end) {
      f(elem)
      elem += step
    }
  }

  /** This apply squashes any end <= start to the empty range.
   *  Only the create method (or reverse) will create a descending range.
   */
  def apply(start: Int, end: Int): ExclusiveIntRange  = if (end <= start) empty else create(start, end)
  def create(start: Int, end: Int): ExclusiveIntRange = if (start == end) empty else create(start join end)
  def create(bits: Long): ExclusiveIntRange           = new ExclusiveIntRange(bits)
}
