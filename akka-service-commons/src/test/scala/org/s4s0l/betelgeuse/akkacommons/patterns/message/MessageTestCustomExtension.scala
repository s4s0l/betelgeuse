/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.message

import org.s4s0l.betelgeuse.akkacommons.patterns.message.Message.ForwardHeaderProvider
import org.s4s0l.betelgeuse.akkacommons.patterns.message.MessageHeaders.{HeaderAccessors, HeaderSetter}

/**
  * Sample of how to add own headers and rules for them conviniently
  *
  * @author Marcin Wielgus
  */
object MessageTestCustomExtension {

  val HEADER_CUSTOM_A = "A"

  val HEADER_CUSTOM_B = "B"

  implicit val defaultForward: ForwardHeaderProvider = () => Seq(HEADER_CUSTOM_B)

  /**
    * This is tricky templating, watch out
    *
    */
  implicit class HeaderSetterExt[T <: HeaderAccessors with HeaderSetter[T]](wrapped: HeaderAccessors with HeaderSetter[T]) extends HeaderAccessors with HeaderSetter[T] {

    override val headers: Map[String, String] = wrapped.headers

    override def withHeaders(map: Map[String, String]): T = wrapped.withHeaders(map)

    def b: String = wrapped.headers(HEADER_CUSTOM_B)

    def a: String = wrapped.headers(HEADER_CUSTOM_A)

    def bOpt: Option[String] = wrapped.headers.get(HEADER_CUSTOM_B)

    def aOpt: Option[String] = wrapped.headers.get(HEADER_CUSTOM_A)

    def withA(a: String): T = withHeader(HEADER_CUSTOM_A, a)

    override def withHeader(key: String, value: String): T = wrapped.withHeader(key, value)

    def withB(b: String): T = withHeader(HEADER_CUSTOM_B, b)
  }

}
