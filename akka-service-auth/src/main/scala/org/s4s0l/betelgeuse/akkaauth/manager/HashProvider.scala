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
package org.s4s0l.betelgeuse.akkaauth.manager

import org.s4s0l.betelgeuse.akkaauth.manager.HashProvider.HashedValue

/**
  * beware of timing attacks when implementing password verifications
  * for more details go to
  *
  * https://security.stackexchange.com/questions/83660/simple-string-comparisons-not-secure-against-timing-attacks
  */
trait HashProvider {
  def hashPassword(password: String): HashedValue

  def checkPassword(hash: HashedValue, password: String): Boolean
}

object HashProvider{
  case class HashedValue(hash: Array[Byte], salt:  Array[Byte])
}
