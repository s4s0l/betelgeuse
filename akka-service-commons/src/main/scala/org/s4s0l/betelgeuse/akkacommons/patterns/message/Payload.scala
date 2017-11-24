/*
 * Copyright© 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.message

import java.nio.charset.{Charset, StandardCharsets}

import akka.util.ByteString

import scala.language.implicitConversions

/**
  * Payload for messages. Could be just ByteString, but bs is copying string.
  * This payload wraps either bytestring or string, and allows conversion between both.
  * User of this class is not aware whether it was created with string or bytestring and
  * can use it as both.
  * @author Marcin Wielgus
  */
class Payload private (val contents:Either[ByteString,String]) {

  implicit lazy val asBytes:ByteString = {
    contents.left.getOrElse(ByteString(contents.right.get, StandardCharsets.UTF_8))
  }

  implicit lazy val asString:String = {
    contents.right.getOrElse(contents.left.get.decodeString(StandardCharsets.UTF_8))
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[Payload]

  override def equals(other: Any): Boolean = other match {
    case that: Payload =>
      (that canEqual this) &&
        contents == that.contents
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(contents)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object Payload {

  implicit def apply(bytes:ByteString): Payload = new Payload(Left(bytes))
  implicit def apply(bytes:Array[Byte]): Payload = new Payload(Left(ByteString(bytes)))
  implicit def apply(string:String): Payload = new Payload(Right(string))

}
