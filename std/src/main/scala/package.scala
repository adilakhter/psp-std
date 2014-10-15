package psp

import java.nio.{ file => jnf }
import jnf.{ attribute => jnfa }
import scala.{ collection => sc }
import sc.{ mutable => scm, immutable => sci }
import scala.sys.process.{ Process, ProcessBuilder }
import psp.std.api._
import psp.std.lowlevel._

package object std extends psp.std.StdPackage {
  type sMap[K, +V] = sciMap[K, V]
  type sList[+A]   = sciList[A]
  type sSet[A]     = sciSet[A]
  type sVector[+A] = sciVector[A]
  type sSeq[+A]    = scSeq[A]

  type pSeq[+A]    = Foreach[A]
  type pVector[+A] = Direct[A]
  type pMap[K, +V] = PolicyMap[K, V]
  type pList[A]    = PolicyList[A]
  type pSet[A]     = PolicySet[A]

  // Inlinable.
  final val InputStreamBufferSize = 8192
  final val MaxInt                = scala.Int.MaxValue
  final val MaxLong               = scala.Long.MaxValue
  final val MinInt                = scala.Int.MinValue
  final val MinLong               = scala.Long.MinValue
  final val PositiveInfinity      = scala.Double.PositiveInfinity

  // DMZ.
  // final val +:      = psp.dmz.+:
  final val :+      = psp.dmz.:+
  final val ::      = psp.dmz.::
  final val Array   = psp.dmz.Array
  final val Console = psp.dmz.Console
  final val List    = psp.dmz.List
  final val Map     = psp.dmz.Map
  final val Option  = psp.dmz.Option
  final val Seq     = psp.dmz.Seq
  final val Set     = psp.dmz.Set
  final val Some    = psp.dmz.Some
  final val System  = psp.dmz.System
  final val Try     = psp.dmz.Try
  final val Tuple2  = psp.dmz.Tuple2
  final val Vector  = psp.dmz.Vector
  final val math    = psp.dmz.math
  final val sys     = psp.dmz.sys
  final val Success = psp.dmz.Success
  final val Failure = psp.dmz.Failure

  final val BigDecimal      = scala.math.BigDecimal
  final val BigInt          = scala.math.BigInt
  final val ClassTag        = scala.reflect.ClassTag
  final val NameTransformer = scala.reflect.NameTransformer
  final val Nil             = sci.Nil
  final val None            = scala.None
  final val Ordering        = scala.math.Ordering
  final val PolicyList      = psp.std.linear.List
  final val StringContext   = scala.StringContext
  final val sConsole        = scala.Console
  final val scBitSet        = sc.BitSet
  final val scIndexedSeq    = sc.IndexedSeq
  final val scIterable      = sc.Iterable
  final val scIterator      = sc.Iterator
  final val scLinearSeq     = sc.LinearSeq
  final val scMap           = sc.Map
  final val scSeq           = sc.Seq
  final val scSet           = sc.Set
  final val scTraversable   = sc.Traversable
  final val sciBitSet       = sci.BitSet
  final val sciIndexedSeq   = sci.IndexedSeq
  final val sciIterable     = sci.Iterable
  final val sciLinearSeq    = sci.LinearSeq
  final val sciList         = sci.List
  final val sciMap          = sci.Map
  final val sciNumericRange = sci.NumericRange
  final val sciRange        = sci.Range
  final val sciSeq          = sci.Seq
  final val sciSet          = sci.Set
  final val sciStream       = sci.Stream
  final val sciTraversable  = sci.Traversable
  final val sciVector       = sci.Vector
  final val scmMap          = scm.Map
  final val scmSeq          = scm.Seq
  final val scmSet          = scm.Set
  final val scmWrappedArray = scm.WrappedArray

  final val ConstantTrue         = newPredicate[Any](_ => true)
  final val ConstantFalse        = newPredicate[Any](_ => false)
  final val CTag                 = scala.reflect.ClassTag
  final val EOL                  = sys.props.getOrElse("line.separator", "\n")
  final val NoFile: jFile        = jFile("")
  final val NoPath: Path         = path("")
  final val NoUri: jUri          = jUri("")
  final val NoFileTime: FileTime = jnfa.FileTime fromMillis MinLong
  final val NoIndex              = Index.undefined
  final val NoNth                = Nth.undefined
  final val NoSize: Size         = Size.undefined

  type PolicyList[A]                      = psp.std.linear.List[A]
  type Walks[A0, Repr]                    = Walkable[Repr] { type A = A0 }
  type ForeachableType[A0, Repr, CC0[X]]  = Foreachable[Repr]  { type A = A0 ; type CC[B] = CC0[B] }
  type DirectAccessType[A0, Repr, CC0[X]] = DirectAccess[Repr] { type A = A0 ; type CC[B] = CC0[B] }

  def classOf[A: CTag](): Class[_ <: A]     = classTag[A].runtimeClass.castTo[Class[_ <: A]]
  def classLoaderOf[A: CTag](): ClassLoader = classOf[A].getClassLoader
  def nullLoader(): ClassLoader             = NullClassLoader
  def loaderOf[A: CTag] : ClassLoader       = noNull(classLoaderOf[A], nullLoader)
  def resource(name: String): Array[Byte]   = Try(noNull(currentThread.getContextClassLoader, nullLoader)) || loaderOf[this.type] fold (_ getResourceAsStream name slurp, _ => Array.empty)
  def resourceString(name: String): String  = utf8(resource(name)).to_s

  implicit def viewifyString(x: String): BaseView[Char, String]    = x.m
  implicit def viewifyArray[A](x: Array[A]): BaseView[A, Array[A]] = x.m[DirectAccess] // must give this type argument explicitly.
  implicit def unViewifyString(x: View[Char]): String              = x.force[String]
  implicit def unViewifyArray[A: CTag](x: View[A]): Array[A]       = x.force[Array[A]]

  implicit def convertIntensional[K, V](x: Intensional[K, V]): K ?=> V = { case k if x contains k => x(k) }
  implicit def convertPolicySeq[A, B](xs: pSeq[A])(implicit conversion: A => B): pSeq[B] = xs map (x => conversion(x))
  implicit def scalaSeqToPSeq[A](x: scSeq[A]): pVector[A] = x.pvec

  implicit class GeneratorOps[A](val g: Generator.Gen[A]) extends AnyVal {
    import Generator._

    def nonEmpty     = !isEmpty
    def isEmpty      = g id_== Empty
    def size: Size   = Size(fold(0)((res, _) => res + 1))
    def tail: Gen[A] = if (g.isEmpty) Empty else g(_ => ())

    def take(n: Int): Gen[A]               = taken(g, n)
    def drop(n: Int): Gen[A]               = dropped(g, n)
    def zip[B](h: Gen[B]): Gen[(A, B)]     = Zipped(g, h)
    def map[B](f: A => B): Gen[B]          = mapped[A, B](g, f)
    def flatMap[B](f: A => Gen[B]): Gen[B] = flatten(g map f)

    def memo: Gen[A] = g match {
      case x: IteratorGenerator[_] => x.memo
      case _                       => g
    }
    def ++[A1 >: A](h: Gen[A1]): Gen[A1]          = concat[A1](g, h)
    def intersperse[A1 >: A](h: Gen[A1]): Gen[A1] = Interspersed(g, h)
    def cyclic: Gen[A]                            = memo |> (c => Cyclic(c, c))

    // @tailrec
    def foreach(f: A => Unit): Unit = if (nonEmpty) g(f) foreach f

    @inline def reduce(f: (A, A) => A): A = {
      var nonEmpty = false
      var first = nullAs[A]
      val result = g(x => try first = x finally nonEmpty = true).fold(first)(f)
      if (nonEmpty) result else abort("empty.reduce")
    }
    @inline def fold[B](zero: B)(f: (B, A) => B): B = {
      def loop(gen: Gen[A], in: B): B = {
        var out = in
        val next = gen(x => out = f(out, x))
        if (next.isEmpty) out else loop(next, out)
      }
      loop(g, zero)
    }
    @inline def withFilter(p: A => Boolean): Gen[A] = filtered(g, p)
    @inline def filter(p: A => Boolean): Gen[A]     = filtered(g, p)
  }

  def assert(assertion: Boolean): Unit                 = if (!assertion) assertionError("assertion failed")
  def assert(assertion: Boolean, msg: => Any): Unit    = if (!assertion) assertionError(s"assertion failed: $msg")
  def require(requirement: Boolean): Unit              = if (!requirement) illegalArgumentException("requirement failed")
  def require(requirement: Boolean, msg: => Any): Unit = if (!requirement) illegalArgumentException(s"requirement failed: $msg")
  def ??? : Nothing                                    = throw new NotImplementedError
  def identity[A](x: A): A                             = x
  def implicitly[A](implicit x: A): A                  = x
  def locally[A](x: A): A                              = x

  def echoErr[A](x: A)(implicit z: TryShow[A]): Unit     = Console echoErr (z show x)
  def println[A](x: A)(implicit z: TryShow[A]): Unit     = Console echoOut (z show x)
  def printResult[A: TryShow](msg: String)(result: A): A = result doto (r => println(pp"$msg: $r"))
  def showResult[A: Show](msg: String)(result: A): A     = result doto (r => println(pp"$msg: $r"))

  def installedProviders: List[FileSystemProvider] = java.nio.file.spi.FileSystemProvider.installedProviders.asScala.toList

  // Operations involving the filesystem.
  def path(s: String, ss: String*): Path                                     = ss.foldLeft(jnf.Paths get s)(_ resolve _)
  def newTempDir(prefix: String, attrs: AnyFileAttr*): Path                  = jnf.Files.createTempDirectory(prefix, attrs: _*)
  def newTempFile(prefix: String, suffix: String, attrs: AnyFileAttr*): Path = jnf.Files.createTempFile(prefix, suffix, attrs: _*)

  // Operations involving external processes.
  def newProcess(line: String): ProcessBuilder      = Process(line)
  def newProcess(args: Seq[String]): ProcessBuilder = Process(args)
  def executeLine(line: String): Int                = Process(line).!
  def execute(args: String*): Int                   = Process(args.toSeq).!

  def openSafari(path: Path): Unit = open.Safari(path)
  def openChrome(path: Path): Unit = open.`Google Chrome`(path)

  object open extends Dynamic {
    def applyDynamic(name: String)(args: TryShown*): String = Process(Seq("open", "-a", name) ++ args.map(_.to_s)).!!
  }

  def summonZero[A](implicit z: Zero[A]): Zero[A] = z

  def show[A: Show] : Show[A]        = ?

  def eqBy[A]     = new ops.EqBy[A]
  def hashBy[A]   = new ops.HashBy[A]
  def hashEqBy[A] = new ops.HashEqBy[A]
  def orderBy[A]  = new ops.OrderBy[A]
  def showBy[A]   = new ops.ShowBy[A]

  // Operations involving encoding/decoding of string data.
  def utf8(xs: Array[Byte]): Utf8   = new Utf8(xs)
  def decodeName(s: String): String = s.mapSplit('.')(NameTransformer.decode)
  def encodeName(s: String): String = s.mapSplit('.')(NameTransformer.encode)

  // Operations involving time and date.
  def formattedDate(format: String)(date: jDate): String = new java.text.SimpleDateFormat(format) format date
  def dateTime(): String                                 = formattedDate("yyyyMMdd-HH-mm-ss")(new jDate)
  def now(): FileTime                                    = jnfa.FileTime fromMillis milliTime
  def timed[A](body: => A): A                            = nanoTime |> (start => try body finally echoErr("Elapsed: %.3f ms" format (nanoTime - start) / 1e6))

  // Operations involving classes, classpaths, and classloaders.
  def manifest[A: Manifest] : Manifest[A]             = implicitly[Manifest[A]]
  def classTag[T: CTag] : CTag[T]                     = implicitly[CTag[T]]

  // Operations involving Null, Nothing, and casts.
  def abortTrace(msg: String): Nothing     = new RuntimeException(msg) |> (ex => try throw ex finally ex.printStackTrace)
  def abort(msg: String): Nothing          = runtimeException(msg)
  def noNull[A](value: A, orElse: => A): A = if (value == null) orElse else value
  def nullAs[A] : A                        = asExpected[A](null)
  def asExpected[A](body: Any): A          = body.castTo[A]

  def intRange(start: Int, end: Int): ExclusiveIntRange = ExclusiveIntRange(start, end)
  def nthRange(start: Int, end: Int): ExclusiveIntRange = ExclusiveIntRange(start, end + 1)
  def indexRange(start: Int, end: Int): IndexRange      = IndexRange(start, end)

  def optImplicit[A](implicit value: A = null): Option[A] = if (value == null) None else Some(value)

  def ?[A](implicit value: A): A                = value
  def andFalse(x: Unit): Boolean                = false
  def andTrue(x: Unit): Boolean                 = true
  def direct[A](xs: A*): Direct[A]              = Direct fromScala xs.toVector
  def each[A](xs: sCollection[A]): Foreach[A]   = Foreach fromScala xs
  def nullStream(): InputStream                 = NullInputStream
  def offset(x: Int): Offset                    = Offset(x)
  def option[A](p: Boolean, x: => A): Option[A] = if (p) Some(x) else None
  def ordering[A: Order] : Ordering[A]          = ?[Order[A]].toScalaOrdering
  def regex(re: String): Regex                  = Regex(re)

  // implicit def nilToSeq[A](x: scala.Nil.type): pSeq[A] = Nil.pseq

  def convertSeq[A, B](xs: sList[A])(implicit conversion: A => B): sList[B]     = xs map conversion
  def convertSeq[A, B](xs: sVector[A])(implicit conversion: A => B): sVector[B] = xs map conversion
  def convertSeq[A, B](xs: sSeq[A])(implicit conversion: A => B): sSeq[B]       = xs map conversion

  def mapBuilder[K, V](xs: (K, V)*): Builder[(K, V), sMap[K, V]] = sciMap.newBuilder[K, V] ++= xs
  def setBuilder[A](xs: A*): Builder[A, sSet[A]]                 = sciSet.newBuilder[A] ++= xs
  def listBuilder[A](xs: A*): Builder[A, sList[A]]               = sciList.newBuilder[A] ++= xs
  def arrayBuilder[A: CTag](xs: A*): Builder[A, Array[A]]        = scala.Array.newBuilder[A] ++= xs
  def vectorBuilder[A](xs: A*): Builder[A, sVector[A]]           = sciVector.newBuilder[A] ++= xs
  def mapToList[K, V](): scmMap[K, sList[V]]                     = scmMap[K, sciList[V]]() withDefaultValue Nil

  def pmapBuilder[K, V]() = mapBuilder[K, V]() mapResult (m => newMap(m.toSeq: _*))

  // Java.
  // def jIterable[A](body: => jIterator[A]): BiIterable[A] = BiIterable[A](body)
  def jMap[K, V](xs: (K, V)*): jMap[K, V]                = new jHashMap[K, V] doto (b => for ((k, v) <- xs) b.put(k, v))
  def jSet[A](xs: A*): jSet[A]                           = new jHashSet[A] doto (b => xs foreach b.add)
  def jList[A](xs: A*): jList[A]                         = java.util.Arrays.asList(xs: _* )
  def jFile(s: String): jFile                            = path(s).toFile
  def jUri(x: String): jUri                              = java.net.URI create x
  def jUrl(x: String): jUrl                              = jUri(x).toURL

  def concurrentMap[K, V](): ConcurrentMapWrapper[K, V]           = new ConcurrentMapWrapper[K, V](new ConcurrentHashMap[K, V], None)
  def concurrentMap[K, V](default: V): ConcurrentMapWrapper[K, V] = new ConcurrentMapWrapper[K, V](new ConcurrentHashMap[K, V], Some(default))

  // PolicyMap is our own creation since SortedMap is way overspecified
  // and LinkedHashMap is too slow and only comes in a mutable variety.
  def newMap[K, V](kvs: (K, V)*): pMap[K, V]                      = PolicyMap[K, V](kvs.m.toPolicyVector.map(_._1), kvs.toMap)
  def newMap[K, V](keys: pVector[K], lookup: K ?=> V): pMap[K, V] = PolicyMap[K, V](keys, lookup)
  def newCmp(difference: Long): Cmp                               = if (difference < 0) Cmp.LT else if (difference > 0) Cmp.GT else Cmp.EQ
  def newList[A](xs: A*): pList[A]                                = PolicyList(xs: _*)
  def newSet[A: HashEq](xs: A*): pSet[A]                          = PolicySet(Direct.elems(xs: _*))
  def newVector[A](xs: A*): pVector[A]                            = Direct.elems(xs: _*)
  def newSeq[A](xs: A*): pSeq[A]                                  = newVector[A](xs: _*)
  def newPredicate[A](f: Predicate[A]): Predicate[A]              = f

  def newArray[A: CTag](size: Size): Array[A] = new Array[A](size.sizeValue)
}
