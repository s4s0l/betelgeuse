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

package org.s4s0l.betelgeuse.akkaauth.manager.impl

import java.security.{MessageDigest, SecureRandom}

import org.bouncycastle.crypto.generators.BCrypt
import org.s4s0l.betelgeuse.akkaauth.manager.HashProvider
import org.s4s0l.betelgeuse.akkaauth.manager.HashProvider.HashedValue

/** Provides bouncy castle Bcrypt hashing implementation of strings
  *
  * https://en.wikipedia.org/wiki/Bcrypt
  *
  * Awesome so answer https://security.stackexchange.com/questions/17421/how-to-store-salt
  *
  * @param logRounds log2 of the number of rounds of hashing to apply
  *                  the work factor therefore increases as
  *                  2**log_rounds.
  */
class BcryptProvider(logRounds: Int, randomProvider: SecureRandom) extends HashProvider {

  override def hashPassword(password: String): HashedValue = {
    val salt = new Array[Byte](16)
    randomProvider.nextBytes(salt)
    val hash = BCrypt.generate(password.getBytes("UTF-8"), salt, logRounds)
    HashedValue(hash, salt)
  }

  override def checkPassword(hash: HashedValue, password: String): Boolean = {
    val calculatedHash = BCrypt.generate(password.getBytes("UTF-8"),  hash.salt, logRounds)
    MessageDigest.isEqual(hash.hash, calculatedHash)
  }
}