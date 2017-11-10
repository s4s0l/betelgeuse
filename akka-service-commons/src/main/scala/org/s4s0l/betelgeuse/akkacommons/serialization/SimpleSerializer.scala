/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 13:42
 *
 */

package org.s4s0l.betelgeuse.akkacommons.serialization

/**
  * @author Marcin Wielgus
  */
trait SimpleSerializer {

  def fromString[T](bytes: String, manifest: Class[T]): T

  def toString(obj: AnyRef): String

  def toBinary(o: AnyRef): Array[Byte]

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef

}
