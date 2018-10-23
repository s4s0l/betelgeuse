/*
 * Copyright© 2018 the original author or authors.
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

package org.s4s0l.betelgeuse.utils

import scala.annotation.implicitNotFound
import scala.language.implicitConversions

/**
  *
  * Very cool, comes from
  *
  * https://stackoverflow.com/questions/4404024/how-to-simulate-an-assign-once-var-in-scala/4407534#4407534
  *
  * @author Marcin Wielgus
  */
class SetOnce[T] {
  private[this] var value: Option[T] = None
  def isSet: Boolean = value.isDefined
  def ensureSet(): Unit = { if (value.isEmpty) throwISE("uninitialized value") }
  def apply(): T = { ensureSet(); value.get }
  def :=(finalValue: T)(implicit credential: SetOnceCredential): Unit = {
    value = Some(finalValue)
  }
  def allowAssignment: SetOnceCredential = {
    if (value.isDefined) throwISE("final value already set")
    else new SetOnceCredential
  }
  private def throwISE(msg: String): Nothing = throw new IllegalStateException(msg)

  @implicitNotFound(msg = "This value cannot be assigned without the proper credential token.")
  class SetOnceCredential private[SetOnce]
}

object SetOnce {
  implicit def unwrap[A](wrapped: SetOnce[A]): A = wrapped()
}
