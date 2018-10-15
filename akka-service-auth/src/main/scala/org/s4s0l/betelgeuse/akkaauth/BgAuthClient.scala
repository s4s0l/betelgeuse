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

import akka.Done
import org.s4s0l.betelgeuse.akkaauth.client.AuthClient
import org.s4s0l.betelgeuse.akkaauth.client.impl.RemoteKeyTokenVerifier.Verifier
import org.s4s0l.betelgeuse.akkaauth.client.impl.{AuthClientImpl, RemoteAuthProviderApi, RemoteKeyTokenVerifier}
import org.s4s0l.betelgeuse.akkaauth.common.RemoteApi
import org.s4s0l.betelgeuse.akkacommons.BgServiceId
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BgClusteringClient
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget

import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
trait BgAuthClient[A]
  extends BgAuthBase[A]
    with BgClusteringClient {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgAuthClient[_]])

  protected def bgAuthProviderServiceId: BgServiceId

  private lazy val internals = new {
    LOGGER.info("Lazy Initializing ...")
    private val remoteManagerTarget: ActorTarget =
      clusteringClientExtension
        .client(bgAuthProviderServiceId)
        .toActorTarget(s"/user/bg-auth-manager")

    private val remoteApi: RemoteApi = new RemoteAuthProviderApi(remoteManagerTarget)
    val keyAvailable: Promise[Done] = Promise()
    private val bgTokenVerifier: Verifier[A] =
      RemoteKeyTokenVerifier.start(remoteApi, keyAvailable, jwtAttributeMapper.unMarshallAttrs)

    clusteringClientExtension
      .client(bgAuthProviderServiceId).whenAvailable {
      bgTokenVerifier.init()
    }

    val bgAuthClient: AuthClient[A] =
      new AuthClientImpl[A](
        bgTokenVerifier,
        token => remoteApi.resolveToken(token)
      )
    LOGGER.info("Lazy Initializing done.")
  }

  override lazy val bgAuthClient: AuthClient[A] = internals.bgAuthClient

  def bgAuthOnPublicKeyAvailable(cb: => Unit): Unit = {
    internals.keyAvailable.future.onComplete {
      case Success(Done) => cb
      case Failure(ex) =>
        LOGGER.error("Should not happen: key available promise failed.", ex)
    }
  }

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    bgAuthClient
    LOGGER.info("Initializing done.")
  }

}
