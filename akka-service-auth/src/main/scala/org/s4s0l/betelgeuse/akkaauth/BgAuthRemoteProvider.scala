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

import akka.actor.{Actor, ActorRef, Props}
import org.s4s0l.betelgeuse.akkaauth.client.TokenVerifier.{TokenInvalidException, TokenProcessingError}
import org.s4s0l.betelgeuse.akkaauth.common.RemoteApi.ResolveApiTokenResponse.{ResolveApiTokenResponseNotOk, ResolveApiTokenResponseOk}
import org.s4s0l.betelgeuse.akkaauth.common.RemoteApi.{GetPublicKeyRequest, GetPublicKeyResponse, ResolveApiTokenRequest}
import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionist

import scala.concurrent.ExecutionContext

/**
  * @author Marcin Wielgus
  */
trait BgAuthRemoteProvider[A]
  extends BgAuthProvider[A]
    with BgClusteringReceptionist {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgAuthRemoteProvider[_]])

  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("Initializing...")
    clusteringReceptionistExtension.register(remoteAuthManagerFacade())
    LOGGER.info("Initializing done.")
  }

  private def remoteAuthManagerFacade(): ActorRef =
    system.actorOf(
      Props(new Actor() {
        override def receive: Receive = {
          case GetPublicKeyRequest() =>
            sender() ! GetPublicKeyResponse(bgAuthKeys.publicKeyBase64)
          case ResolveApiTokenRequest(token) =>
            import akka.pattern.pipe
            implicit val ec: ExecutionContext = context.dispatcher
            bgAuthManager.resolveApiToken(token)
              .map(it => ResolveApiTokenResponseOk(it))
              .recover {
                case TokenInvalidException(reason) =>
                  ResolveApiTokenResponseNotOk(reason)
                case ex: Throwable =>
                  LOGGER.error("Error processing remote api token verification", ex)
                  ResolveApiTokenResponseNotOk(TokenProcessingError(ex.getMessage))
              }
              .pipeTo(sender())
        }
      }),
      "bg-auth-manager"
    )
}
