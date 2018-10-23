
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
