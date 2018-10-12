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

package org.s4s0l.betelgeuse.akkaauth

import org.s4s0l.betelgeuse.akkaauth.client.AuthClient
import org.s4s0l.betelgeuse.akkaauth.client.impl.AuthClientImpl
import org.s4s0l.betelgeuse.akkaauth.manager._
import org.s4s0l.betelgeuse.akkaauth.manager.impl.{AuthManagerImpl, TokenFactoryImpl}
import org.s4s0l.betelgeuse.akkacommons.http.BgHttp

/**
  * @author Marcin Wielgus
  */
trait BgAuthProvider[A]
  extends BgAuthBase[A]
    with BgHttp {

  override lazy val bgAuthClient: AuthClient[A] = {
    new AuthClientImpl[A](
      bgTokenVerifier,
      jwtAttributeMapper,
      authManager.resolveApiToken
    )
  }

  lazy val userManager: UserManager = null
  lazy val passwordManager: PasswordManager = null
  lazy val tokenManager: TokenManager = null
  lazy val authManager: AuthManager[A] = new AuthManagerImpl[A](
    userManager = userManager,
    tokenManager = tokenManager,
    passwordManager = passwordManager,
    additional = jwtAttributeMapper,
    tokenFactory = new TokenFactoryImpl(bgAuthKeys.publicKey, bgAuthKeys.privateKey, config)
  )

  override protected def jwtAttributeMapper: AdditionalUserAttrsManager[A]
}
