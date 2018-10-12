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
import org.s4s0l.betelgeuse.akkaauth.client.TokenVerifier.TokenInvalidException
import org.s4s0l.betelgeuse.akkaauth.client.impl.AuthClientImpl
import org.s4s0l.betelgeuse.akkaauth.common.ResolveApiTokenResponse.{ResolveApiTokenResponseNotOk, ResolveApiTokenResponseOk}
import org.s4s0l.betelgeuse.akkaauth.common.{ResolveApiTokenRequest, ResolveApiTokenResponse, SerializedToken}
import org.s4s0l.betelgeuse.akkacommons.BgServiceId
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BgClusteringClient

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
trait BgAuthClient[A]
  extends BgAuthBase[A]
    with BgClusteringClient {

  override lazy val bgAuthClient: AuthClient[A] = {
    new AuthClientImpl[A](
      bgTokenVerifier,
      jwtAttributeMapper,
      resolveTokenRemotely
    )
  }

  private def resolveTokenRemotely(token: SerializedToken)
  : Future[SerializedToken] = {
    remoteManagerTarget.?(ResolveApiTokenRequest(token))(3.seconds)
      .map(_.asInstanceOf[ResolveApiTokenResponse])
      .map {
        case ResolveApiTokenResponseOk(resultingToken) =>
          resultingToken
        case ResolveApiTokenResponseNotOk(reason) =>
          throw TokenInvalidException(reason)
      }
  }

  private lazy val remoteManagerTarget = clusteringClientExtension
    .client(bgAuthProviderServiceId)
    .toActorTarget(s"/user/bgAuthManager")

  protected def bgAuthProviderServiceId: BgServiceId

}
