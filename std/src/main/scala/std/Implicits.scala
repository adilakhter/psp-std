package psp
package std

import api._, exp._

trait AllImplicit extends StdEmpty with StdViewers with StdConverters with StdOps with StdAlgebra {
  import java.util.AbstractMap.SimpleImmutableEntry

  // Values.
  implicit def cleaverJMapEntry[A, B]: Cleaver[jMapEntry[A, B], A, B]      = cleaver(new SimpleImmutableEntry(_, _), _.getKey, _.getValue)
  implicit def cleaverPair[A, B]: Cleaver[A -> B, A, B]                    = cleaver[A -> B, A, B](((_, _)), fst, snd)
  implicit def cleaverTriple[A, B, C]: Cleaver[Triple[A, B, C], A, B -> C] = cleaver((x, y) => ((x, fst(y), snd(y))), _._1, x => pair(x._2, x._3))
  implicit def cleaverScalaList[A]: Cleaver[sciList[A], A, sciList[A]]     = cleaver(_ :: _, _.head, _.tail)
  implicit def cleaverPspList[A]: Cleaver[Plist[A], A, Plist[A]]           = cleaver(_ :: _, _.head, _.tail)
  implicit def conforms[A]: (A <:< A)                                      = new conformance[A]
  implicit def defaultRenderer: FullRenderer                               = new FullRenderer(minElements = Size(3), maxElements = Size(10))

  // Conversions.
  implicit def longToPrecise(x: Long): Precise                     = Size(x)
  implicit def funToPartialFunction[A, B](f: Fun[A, B]): A ?=> B   = f.toPartial
  implicit def apiViewToIdView[A](xs: View[A]): IdView[A, View[A]] = new IdView(xs)
  implicit def hasShowToDoc[A](x: A)(implicit z: Show[A]): Doc     = Doc(x)
}

trait StdOps0 {
  implicit def unconvertViewToRepr[A, R](xs: View[A])(implicit z: Builds[A, R]): R = z build xs
}
trait StdOps1 extends StdOps0 {
  implicit def opsAlreadyView[A](x: View[A]): ViewOps[A, View[A]]             = new ViewOps(x)
  implicit def opsView[A, R](xs: R)(implicit z: ViewsAs[A, R]): ViewOps[A, R] = new ViewOps(z viewAs xs)
  implicit def opsView2D[A](x: View2D[A]): View2DOps[A]                       = new View2DOps(x)
  implicit def opsWrapString(x: String): Pstring                              = new Pstring(x)
}
trait StdOps extends StdOps1 {
  implicit def opsIdView[A, R](xs: R)(implicit ev: R <:< Each[A]): ViewOps[A, R] = new ViewOps(new IdView(ev(xs)))
}
