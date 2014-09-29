package psp

import java.nio.{ file => jnf }
import jnf.{ attribute => jnfa }
import scala.collection.{ generic => scg, mutable => scm, immutable => sci }
import scala.sys.process.{ Process, ProcessBuilder }
import psp.std.api._

package std {
  trait PackageLow {
    // A weaker variation of Shown - use Show[A] if one can be found and toString otherwise.
    implicit def showableToTryShown[A](x: A)(implicit shows: TryShow[A]): TryShown = new TryShown(shows show x)
  }
}

package object std extends psp.std.PackageImplicits with psp.std.api.PackageAliases with PackageLow {
  implicit def opsSizeImpl(x: Size): Size.Impl = Size(x.value)

  // The typesafe non-toString-using show"..." interpolator.
  implicit def opsApiShowInterpolator(sc: StringContext): ShowInterpolator = new ShowInterpolator(sc)

  // Continuing the delicate dance against scala's hostile-to-correctness intrinsics.
  implicit def showableToShown[A: Show](x: A): Shown = Shown(implicitly[Show[A]] show x)

  implicit def orderToImpl[A](ord: Order[A]): Order.Impl[A] = new Order.Impl(ord.compare)

  implicit class ArrayViewOps[A](val repr: Array[A]) {
    def m: IndexedView[A, Array[A]] = new DirectAccess.ArrayIs[A] wrap repr
  }
  implicit class StringViewOps[A](val repr: String) {
    def m: IndexedView[Char, String] = DirectAccess.StringIs wrap repr
  }
  implicit class ApiOrderOps[A](val ord: Order[A]) extends AnyVal {
    def reverse: Order[A]          = Order[A]((x, y) => ord.compare(x, y).flip)
    def on[B](f: B => A): Order[B] = Order[B]((x, y) => ord.compare(f(x), f(y)))
  }


  final val PspList              = linear.List
  final val NoSize: Size         = Size.undefined
  final val NoPath: Path         = path("")
  final val NoFile: jFile        = jFile("")
  final val NoUri: jUri          = jUri("")
  final val NoFileTime: FileTime = jnfa.FileTime fromMillis MinLong
  final val EOL                  = sys.props.getOrElse("line.separator", "\n")
  final val MaxInt               = Int.MaxValue
  final val MinInt               = Int.MinValue
  final val MaxLong              = Long.MaxValue
  final val MinLong              = Long.MinValue
  final val NoIndex              = Index.undefined
  final val NoNth                = Nth.undefined
  final val NumericRange         = sci.NumericRange
  final val ScalaNil             = sci.Nil
  final val CTag                 = scala.reflect.ClassTag

  // Our types.

  type ForeachableType[A0, Repr, CC0[X]] = Foreachable[Repr] {
    type A = A0
    type CC[B] = CC0[B]
  }
  type DirectAccessType[A0, Repr, CC0[X]] = DirectAccess[Repr] {
    type A = A0
    type CC[B] = CC0[B]
  }

  type PspList[A] = psp.std.linear.List[A]

  def fileSeparator      = java.io.File.separator
  def classpathSeparator = java.io.File.pathSeparator
  def NameTransformer    = scala.reflect.NameTransformer

  def now(): FileTime                                       = jnfa.FileTime fromMillis System.currentTimeMillis
  def newTempDir(prefix: String, attrs: AnyFileAttr*): Path = jnf.Files.createTempDirectory(prefix, attrs: _*)

  def path(s: String, ss: String*): Path = ss.foldLeft(jnf.Paths get s)(_ resolve _)

  def javaHome: jFile                           = jFile(scala.util.Properties.javaHome)
  def openInApp(app: String, file: jFile): Unit = execute("open", "-a", app, file.getAbsolutePath)
  def openSafari(file: jFile): Unit             = openInApp("Safari", file)
  def openChrome(file: jFile): Unit             = openInApp("Google Chrome", file)

  def newProcess(line: String): ProcessBuilder      = Process(line)
  def newProcess(args: Seq[String]): ProcessBuilder = Process(args)
  def executeLine(line: String): Int                = Process(line).!
  def execute(args: String*): Int                   = Process(args.toSeq).!

  def eqBy[A]    = new Ops.EqBy[A]
  def orderBy[A] = new Ops.OrderBy[A]
  def showBy[A]  = new Ops.ShowBy[A]

  def ?[A](implicit value: A): A                     = value
  def Try[A](body: => A): scala.util.Try[A]          = scala.util.Try[A](body)
  def andFalse(x: Unit): Boolean                     = false
  def andTrue(x: Unit): Boolean                      = true
  def asExpected[A](body: Any): A                    = body.asInstanceOf[A]
  def classTag[T: CTag] : CTag[T]                    = implicitly[CTag[T]]
  def dateTime(): String                             = new java.text.SimpleDateFormat("yyyyMMdd-HH-mm-ss") format new jDate
  def defaultCharset                                 = java.nio.charset.Charset.defaultCharset
  def fail(msg: String): Nothing                     = throw new RuntimeException(msg)
  def fromUTF8(xs: Array[Byte]): String              = new String(scala.io.Codec fromUTF8 xs)
  def nanoTime: Long                                 = System.nanoTime
  def nullAs[A] : A                                  = null.asInstanceOf[A]
  def printResult[A](msg: String)(result: A): A      = try result finally println(s"$msg: $result")
  def showResult[A: Show](msg: String)(result: A): A = try result finally println("$msg: ${result.to_s}")
  def regex(re: String): Regex                       = Regex(re)
  def ordering[A: Order] : Ordering[A]               = ?[Order[A]].toScalaOrdering

  def convertSeq[A, B](xs: List[A])(implicit conversion: A => B): List[B]     = xs map conversion
  def convertSeq[A, B](xs: Vector[A])(implicit conversion: A => B): Vector[B] = xs map conversion
  def convertSeq[A, B](xs: Seq[A])(implicit conversion: A => B): Seq[B]       = xs map conversion

  def scmSeq[A](xs: A*): scm.Seq[A]                       = scm.Seq(xs: _*)
  def scmSet[A](xs: A*): scm.Set[A]                       = scm.Set(xs: _*)
  def scmMap[K, V](kvs: (K, V)*): scm.Map[K, V]           = scm.Map[K, V](kvs: _*)
  def sciSeq[A](xs: A*): sci.Seq[A]                       = sci.Seq(xs: _*)
  def sciSet[A](xs: A*): sci.Set[A]                       = sci.Set(xs: _*)
  def sciMap[K, V](kvs: (K, V)*): sci.Map[K, V]           = sci.Map[K, V](kvs: _*)
  def listBuilder[A](xs: A*): Builder[A, List[A]]         = sci.List.newBuilder[A] ++= xs
  def arrayBuilder[A: CTag](xs: A*): Builder[A, Array[A]] = scala.Array.newBuilder[A] ++= xs
  def vectorBuilder[A](xs: A*): Builder[A, Vector[A]]     = sci.Vector.newBuilder[A] ++= xs

  // Java.
  def jMap[K, V](xs: (K, V)*): jMap[K, V] = new jHashMap[K, V] doto (b => for ((k, v) <- xs) b.put(k, v))
  def jSet[A](xs: A*): jSet[A]            = new jHashSet[A] doto (b => xs foreach b.add)
  def jList[A](xs: A*): jArrayList[A]     = new jArrayList[A] doto (b => xs foreach b.add)
  def jFile(s: String): jFile             = path(s).toFile
  def jUri(x: String): jUri               = java.net.URI create x
  def jUrl(x: String): jUrl               = jUri(x).toURL
  def jClassOf[T: CTag] : Class[_ <: T]   = classTag[T].runtimeClass.asInstanceOf[Class[_ <: T]]

  // OrderedMap is our own creation since SortedMap is way overspecified
  // and LinkedHashMap is too slow and only comes in a mutable variety.
  def orderedMap[K, V](kvs: (K, V)*): OrderedMap[K, V]                 = new OrderedMap[K, V](kvs map (_._1), kvs.toMap)
  def orderedMap[K, V](keys: Seq[K], map: Map[K, V]): OrderedMap[K, V] = new OrderedMap[K, V](keys, map)

  def show[A: Show] : Show[A]        = ?
  // def readInto[A] : Read.ReadInto[A] = Read.into[A]

  def precise(n: Int): Precise = Precise(Size(n))
  def bounded(lo: Size, hi: SizeInfo): SizeInfo = hi match {
    case hi: Atomic     => bounded(lo, hi)
    case Bounded(_, hi) => bounded(lo, hi)
  }
  def bounded(lo: SizeInfo, hi: SizeInfo): SizeInfo = lo match {
    case Precise(lo)    => bounded(lo, hi)
    case Bounded(lo, _) => bounded(lo, hi)
    case Infinite       => Infinite
  }
  def bounded(lo: Size, hi: Atomic): SizeInfo = hi match {
    case Precise(n) if n < lo  => SizeInfo.Empty
    case Precise(n) if n == lo => hi
    case _                     => Bounded(lo, hi)
  }

  def contextLoader(): ClassLoader                       = noNull(Thread.currentThread.getContextClassLoader, nullLoader)
  def decodeName(s: String): String                      = scala.reflect.NameTransformer decode s
  def each[A](xs: GTOnce[A]): Foreach[A]                 = Foreach traversable xs
  def failEmpty(operation: String): Nothing              = throw new NoSuchElementException(s"$operation on empty collection")
  def index(x: Int): Index                               = Index(x)
  def indexRange(start: Int, end: Int): IndexRange       = IndexRange.until(Index(start), Index(end))
  def labelpf[T, R](label: String)(pf: T ?=> R): T ?=> R = new LabeledPartialFunction(pf, label)
  def loaderOf[A: ClassTag] : ClassLoader                = noNull(jClassOf[A].getClassLoader, nullLoader)
  def errLog(msg: String): Unit                          = Console.err println msg
  def noNull[A](value: A, orElse: => A): A               = if (value == null) orElse else value
  def nth(x: Int): Nth                                   = Nth(x)
  def nullLoader(): ClassLoader                          = NullClassLoader
  def nullStream(): InputStream                          = NullInputStream
  def offset(x: Int): Offset                             = Offset(x)
  def resource(name: String): Array[Byte]                = Try(contextLoader) || loaderOf[this.type] fold (_ getResourceAsStream name slurp, _ => Array.empty)
  def resourceString(name: String): String               = fromUTF8(resource(name))
  def timed[A](body: => A): A                            = nanoTime |> (start => try body finally errLog("Elapsed: %.3f ms" format (nanoTime - start) / 1e6))
  def unknownSize: SizeInfo                              = SizeInfo.Unknown

  // String arrangements.
  def tabular[A](rows: Seq[A], join: Seq[String] => String)(columns: (A => String)*): String = {
    val cols   = columns.toVector
    val widths = cols map (f => rows map f map (_.length) max)
    def one(width: Int, value: String): String = (
      if (width == 0 || value == "") ""
      else ("%-" + width + "s") format value
    )
    rows map (row => join(widths zip (cols map (_ apply row)) map { case (w, v) => one(w, v) })) mkString "\n"
  }
}
