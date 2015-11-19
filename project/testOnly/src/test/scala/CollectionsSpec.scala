package psp
package tests

import scala.collection.immutable.StringOps
import std._, api._,StdEq._, StdShow._
import Prop.forAll

class SpireSpec extends ScalacheckBundle {
  def bundle = "Reliance on spire"
  import spire._, algebra._, implicits._
  import spire.syntax.literals.si._

  val y = big"123 456 789 987 654 321"       // BigInt
  val z = dec"1 234 456 789.123456789098765" // BigDecimal

  def props = vec(
    expectValue(y * 3)(Array(y, y, y).inPlace.shuffle.m.sum),
    expectValue(y * y * y)(Array(y, y, y).inPlace.shuffle.m.product)
  )
}

class ADTSpec extends ScalacheckBundle {
  def bundle = "ADTs defined in psp-api"

  val f1 = Fun((_: Int) * 2)
  val f2 = f1 mapOut (_ * 3)
  val f3 = f2 filterIn (_ <= 2)
  val f4 = f3 defaulted (_ => 99)
  val xs = vec(1, 2, 3)

  var seen = ""
  val m1 = f1.traced(
    x => seen += s"f($x): ",
    x => seen += s"$x "
  )
  lazy val m1trace = {
    xs mapNow m1
    seen.trim
  }
  def props = vec(
    "size.+ is commutative"   -> commutative[Size](_ + _),
    "size.max is associative" -> associative[Size](_ max _),
    "size.max is commutative" -> commutative[Size](_ max _),
    "size.min is associative" -> associative[Size](_ min _),
    "size.min is commutative" -> commutative[Size](_ min _),
    seqShows("2, 4, 6", xs map f1),
    seqShows("6, 12, 18", xs map f2),
    seqShows("6, 12", xs collect f3),
    seqShows("6, 12", xs collect f4),
    seqShows("6, 12, 99", xs map f4),
    showsAs("18", f2 get 3),
    showsAs("-", f3 get 3),
    showsAs("-", f4 get 3),
    showsAs("99", f4(3)),
    showsAs("f(1): 2 f(2): 4 f(3): 6", m1trace)
  )
}

class StringExtensions extends ScalacheckBundle {
  def bundle = "String Extensions"

  def s = "123 monkey dog ^^.* hello mother 456"
  def scalaOps(s: String)  = new StringOps(s)
  def policyOps(s: String) = new PspStringOps(s)

  def newProp[A: Eq](f: StringOps => A, g: String => A): Prop = forAll((s: String) => sameBehavior(f(scalaOps(s)), g(s)))

  def newProp2[B] = new {
    def apply[R](f: (StringOps, B) => R)(g: (String, B) => R)(implicit z1: Arb[B], z2: Eq[R]): Prop =
      forAll((s: String, x: B) => sameBehavior(f(scalaOps(s), x), g(s, x)))
  }

  // dropRight and takeRight have the domain limited because of a scala bug with
  // take/dropRight with values around MinInt.
  def mostInts = arb[Int] filter (_ > MinInt + 5000)

  def props: Direct[NamedProp] = Direct(
    "stripSuffix" -> newProp2[String](_ stripSuffix _)(_ stripSuffix _),
    "stripPrefix" -> newProp2[String](_ stripPrefix _)(_ stripPrefix _),
    "take"        -> newProp2[Int](_ take _)(_ take _.size build),
    "drop"        -> newProp2[Int](_ drop _)(_ drop _.size build),
    "takeRight"   -> newProp2[Int](_ takeRight _)(_ takeRight _.size build)(mostInts, ?),
    "dropRight"   -> newProp2[Int](_ dropRight _)(_ dropRight _.size build)(mostInts, ?),
    "toInt"       -> newProp[Int](_.toInt, _.toInt),
    "tail"        -> newProp[String](_.tail, _.tail.force),
    "head"        -> newProp(_.head, _.head),
    "drop"        -> newProp[Char](_.head, _.head),
    "reverse"     -> newProp[String](_.reverse, _.reverse.force)
  )
}

class GridSpec extends ScalacheckBundle {
  def bundle = "Policy, Grid Operations"

  def primePartition = (Indexed from 2).m mpartition (xs => _ % xs.head == 0)
  def primePartitionGrid(n: Int): View2D[Int]   = primePartition take n.size map (_ take n.size)
  def primePartitionGrid_t(n: Int): View2D[Int] = primePartition.transpose take n.size map (_ take n.size)
  def showGrid(xss: View2D[Int]): String = {
    val yss = xss mmap (_.render)
    val width = yss.flatMap(x => x).mapNow(_.length).m.max.size
    (yss mmap (x => width.leftFormatString format x) map (_ mk_s " ") mk_s "\n").trim.trimLines
  }
  def primePartition6 = sm"""
    |2   4   6   8   10  12
    |3   9   15  21  27  33
    |5   25  35  55  65  85
    |7   49  77  91  119 133
    |11  121 143 187 209 253
    |13  169 221 247 299 377
  """
  def primePartition6_t = sm"""
    |2   3   5   7   11  13
    |4   9   25  49  121 169
    |6   15  35  77  143 221
    |8   21  55  91  187 247
    |10  27  65  119 209 299
    |12  33  85  133 253 377
  """

  def props = Direct(
    seqShows("[ 2, 4, 6, ... ], [ 3, 9, 15, ... ], [ 5, 25, 35, ... ]", primePartition take 3.size),
    showsAs(primePartition6, showGrid(primePartitionGrid(6))),
    showsAs(primePartition6_t, showGrid(primePartitionGrid_t(6)))
  )
}

class ViewBasic extends ScalacheckBundle {
  def bundle = "Views, Basic"

  def plist   = list(1, 2, 3)
  def pvector = vec(1, 2, 3)
  def parray  = Array(1, 2, 3)
  def pseq    = Each.elems(1, 2, 3)
  def punfold = Indexed from 1

  case class Bippy(s: String, i: Int) {
    override def toString = s
  }

  // Testing different kinds of "distinct" calls.
  val s1 = new Bippy("abc", 1)
  val s2 = new Bippy("abc", 2)
  val s3 = new Bippy("def", 3)
  val s4 = new Bippy("def", 3)
  val strs = sciVector(s1, s2, s3, s4)

  def closure    = transitiveClosure(parray)(x => view(x.init.force, x.tail.force))
  def closureBag = closure flatMap (x => x) toBag // That's my closure bag, baby
  def xxNumbers  = (Indexed from 0).m grep """^(.*)\1""".r

  def props: Direct[NamedProp] = Direct(
    showsAs("[ 1, 2, 3 ]", plist),
    showsAs("[ 1, 2, 3 ]", pvector),
    showsAs("[ 1, 2, 3 ]", parray),
    showsAs("[ 1, 2, 3, 1, 2, 3 ]", plist.m ++ plist.m force),
    showsAs("[ 1, 2, 3, 1, 2, 3 ]", pvector ++ pvector force),
    showsAs("[ 1, 2, 3, 1, 2, 3 ]", parray ++ parray force),
    showsAs("[ 1, 2, 3, 1, 2, 3 ]", parray.m ++ parray.m force),
    // showsAs("[ 1, 2, 3, ... ]", punfold),
    // showsAs("[ 1, 2, 3 ], [ 1, 2 ], [ 1 ], [  ], [ 2 ], [ 2, 3 ], [ 3 ]", closure mk_s ", "),
    // showsAs("1 -> 3, 2 -> 4, 3 -> 3", closureBag.entries mk_s ", "),
    seqShows("1 -> 0, 2 -> 1, 3 -> 2", pvector.m.mapWithIndex(_ -> _)),
    seqShows("11, 22, 33, 44", indexRange(1, 50) grep """(.)\1""".r),
    seqShows("99, 1010, 1111", xxNumbers slice (8 takeNext 3.size).asIndices),
    expectValue[Size](4.size)(strs.byRef.distinct.force.size),
    expectValue[Size](3.size)(strs.byEquals.distinct.force.size),
    expectValue[Size](2.size)(strs.byString.distinct.force.size)
  )
}

class ViewSplitZip extends ScalacheckBundle {
  def bundle = "Views, Split/Zip"

  def pdirect = 1 to 6 toDirect
  def span    = pdirect span (_ <= 3)
  def mod     = pdirect partition (_ % 2 == 0)
  def zipped  = mod.left zip mod.right
  def sums    = zipped map (_ + _)

  def props: Direct[NamedProp] = Direct(
    showsAs("[ 1, 2, 3 ]", span.left),
    showsAs("[ 4, 5, 6 ]", span.right),
    showsAs("[ 2, 4, 6 ]", mod.left),
    showsAs("[ 1, 3, 5 ]", mod.right),
    showsAs("[ 2, 4, 6 ]", zipped.lefts),
    showsAs("[ 1, 3, 5 ]", zipped.rights),
    showsAs("[ 2 -> 1, 4 -> 3, 6 -> 5 ]", zipped),
    showsAs("[ 20 -> 1, 40 -> 3, 60 -> 5 ]", zipped mapLeft (_ * 10)),
    showsAs("[ 3, 7, 11 ]", sums),
    showsAs("[ 3 ]", zipped filterLeft (_ == 4) rights),
    showsAs("[ 2, 4, 6, 1, 3, 5 ]", mod.rejoin),
    showsAs("6 -> 5", zipped findLeft (_ == 6)),
    showsAs("-", zipped findLeft (_ == 8))
  )
}

class CollectionsSpec extends ScalacheckBundle {
  def bundle = "Type Inference, General"

  val arr  = Array[Int](1, 2, 3)
  val smap = sciMap("a" -> 1, "b" -> 2, "c" -> 3)
  val sseq = sciSeq("a" -> 1, "b" -> 2, "c" -> 3)
  val svec = sciVector("a" -> 1, "b" -> 2, "c" -> 3)
  val sset = sciSet("a" -> 1, "b" -> 2, "c" -> 3)
  val jseq = jList("a" -> 1, "b" -> 2, "c" -> 3)
  val jset = jSet("a" -> 1, "b" -> 2, "c" -> 3)
  val jmap = jMap("a" -> 1, "b" -> 2, "c" -> 3)

  def paired[A](x: A): (A, Int) = x -> ("" + x).length

  def props: Vec[NamedProp] = policyProps ++ vec(
    expectTypes[String](
      "abc" map identity build,
      "abc" map (_.toInt.toChar) build,
      "abc" map (_.toInt) map (_.toChar) build,
      "abc" flatMap (_.toString * 3) build,
      "abc" flatMap (_.toString * 3) build,
      "abc" map identity flatMap ("" + _) build
    ),
    expectTypes[Array[Int]](
      arr.inPlace map identity,
      arr.inPlace.reverse,
      arr ++ arr,
      arr.m ++ arr.m force,
      arr.m.build,
      arr flatMap (x => vec(x)) build,
      arr flatMap (x => list(x)) build,
      arr flatMap (x => view(x)) build,
      arr.m flatMap (x => vec(x)) build,
      arr.m flatMap (x => list(x)) build,
      arr.m flatMap (x => view(x)) build,
      arr.flatMap(x => view(x)).force[Array[Int]],
      arr.m.flatMap(x => vec(x)).force[Array[Int]]
    ),
    expectTypes[sciSet[_]](
      sset map identity,
      sset.m build,
      sset.m map identity build,
      sset.m.map(fst) map paired build
    ),
    expectTypes[sciMap[_, _]](
      (smap map identity).force[sciMap[_, _]],
      smap.m build,
      smap.m map identity build,
      smap.m map fst map identity map paired build
    ),
    expectTypes[scSeq[_]](
      sseq map identity,
      sseq.m build,
      sseq.m map identity build,
      sseq.m.map(fst).map(paired).force[scSeq[_]]
    ),
    expectTypes[sciVector[_]](
      svec map identity,
      svec.m.build,
      svec.m map identity build,
      svec.m.map(fst).map(paired).force[sciVector[_]]
    ),
    expectTypes[jList[_]](
      jseq map identity build,
      jseq.m.build,
      jseq.m map identity build,
      jseq.m.map(fst).map(paired).force[jList[_]]
    ),
    expectTypes[jSet[_]](
      jset map identity build,
      jset.m build,
      jset.m map identity build,
      jset.m.map(fst) map paired build
    ),
    expectTypes[jMap[_, _]](
      (jmap map identity).force[jMap[_, _]],
      jmap.m build,
      jmap.m map identity build,
      jmap.m map fst map identity map paired build
    )
  )

  def policyProps: Vec[NamedProp] = {
    import StdEq._
    val pset = set("a" -> 1, "b" -> 2, "c" -> 3)
    val pvec = vec("a" -> 1, "b" -> 2, "c" -> 3)

    vec(
      expectTypes[ExSet[_]](
        pset.build,
        pset map identity build,
        pset map fst map paired build,
        pset union pset
      ),
      expectTypes[Vec[_]](
        pvec mapNow identity,
        pvec ++ pvec,
        pvec.m ++ pvec.m build,
        pvec.tail.force,
        pvec.m.build,
        pvec.m map identity build,
        pvec.m.map(fst).map(paired).force[Vec[_]]
      )
    )
  }
}