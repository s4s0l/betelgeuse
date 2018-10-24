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

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkaauth.client.AuthClient
import org.s4s0l.betelgeuse.akkaauth.client.impl.AuthClientImpl
import org.s4s0l.betelgeuse.akkaauth.common.KeyManager
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.UserDetailedInfo
import org.s4s0l.betelgeuse.akkaauth.manager._
import org.s4s0l.betelgeuse.akkaauth.manager.impl._
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
trait BgAuthProvider[A]
  extends BgAuthBase[A]
    with BgClusteringSharding {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgAuthProvider[_]])

  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with auth-provider.conf with fallback to...")
    ConfigFactory.parseResources("auth-provider.conf").withFallback(super.customizeConfiguration)
  }

  private lazy val internals = new {
    LOGGER.info("Lazy Initializing ...")
    val authKeys: KeyManager = new KeyManager
    private val tokenFactory = new TokenFactoryImpl[A](authKeys.publicKey, authKeys.privateKey, jwtAttributeMapper)
    val userManager: UserManager = UserManagerImpl.start
    val passwordManager: PasswordManager = PasswordManagerImpl.start
    val tokenManager: TokenManager = TokenManagerImpl.start
    val authManager: AuthManager[A] = new AuthManagerImpl[A](
      userManager = userManager,
      tokenManager = tokenManager,
      passwordManager = passwordManager,
      tokenFactory = tokenFactory,
      beforeUserCreate = bgAuthBeforeUserCreateHook
    )
    val authClient = new AuthClientImpl[A](
      tokenFactory,
      authManager
    )
    LOGGER.info("Lazy Initializing done.")
  }

  def bgAuthKeys: KeyManager = internals.authKeys

  def bgAuthUserManager: UserManager = internals.userManager

  def bgAuthPasswordManager: PasswordManager = internals.passwordManager

  def bgAuthTokenManager: TokenManager = internals.tokenManager

  def bgAuthManager: AuthManager[A] = internals.authManager

  override def bgAuthClient: AuthClient[A] = internals.authClient

  override protected def jwtAttributeMapper: AdditionalUserAttrsManager[A]

  def bgAuthBeforeUserCreateHook: UserDetailedInfo => Future[UserDetailedInfo] = Future.successful

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    bgAuthClient
    bgAuthManager
    LOGGER.info("Initializing done.")
  }
}
