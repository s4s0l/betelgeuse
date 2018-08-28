
package org.s4s0l.betelgeuse.utils


import scala.language.implicitConversions

/**
  * @author Marcin Wielgus
  */
class Lazy[A](f: => A, private var option: Option[A] = None) {

  def apply(): A = option match {
    case Some(a) => a
    case None => val a = f; option = Some(a); a
  }

  def toOption: Option[A] = option

}

object Lazy {
  def apply[A](f: => A): Lazy[A] = new Lazy(f)

  implicit def toOption[A](l: Lazy[A]): Option[A] = l.toOption
}
