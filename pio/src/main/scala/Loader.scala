package psp
package std
package pio

import api._, StdEq._, StdShow._

final class PolicyLoader(val classMap: ExMap[String, Bytes]) extends ClassLoader {
  private val keys        = classMap.keyVector
  private val instanceMap = scmMap[String, jClass]()
  private val errorMap    = scmMap[String, LinkageError]()
  private def isNoClassDefFoundError(t: Throwable) = t match {
    case _: NoClassDefFoundError => true
    case _                       => false
  }

  def totalClasses = classMap.size
  def names        = keys
  def classes      = names map (x => findClass(x))
  def errors       = errorMap.toMap
  def missing      = errorMap.m.toExMap filterValues isNoClassDefFoundError keys
  def otherErrors  = errorMap.m.toExMap filterValues !(isNoClassDefFoundError _)
  def totalBytes   = classMap.values.map(_.length).foldl(0)(_ + _)

  def define(name: String): jClass               = define(name, classMap(name))
  def define(name: String, bytes: Bytes): jClass = defineClass(name, bytes, 0, bytes.length, null)

  private def doDefine(name: String): jClass = try define(name) catch {
    case t: LinkageError => errorMap(name) = t ; null
    case t: Throwable    => println(pp"Caught $t") ; null
  }

  override def findClass(name: String): jClass = instanceMap.getOrElse(name,
    findLoadedClass(name) match {
      case cl: jClass                     => cl
      case _ if !(classMap contains name) => super.findClass(name)  /** NoClassDefFound */
      case _                              => doDefine(name) doto (instanceMap(name) = _)
    }
  )

  override def toString = s"Loader($totalClasses classes, $totalBytes bytes)"
}
