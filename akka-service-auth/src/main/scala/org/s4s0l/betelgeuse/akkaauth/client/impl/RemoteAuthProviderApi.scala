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

/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkaauth.client.impl

import org.s4s0l.betelgeuse.akkaauth.client.TokenVerifier.TokenInvalidException
import org.s4s0l.betelgeuse.akkaauth.common.RemoteApi.ResolveApiTokenResponse.{ResolveApiTokenResponseNotOk, ResolveApiTokenResponseOk}
import org.s4s0l.betelgeuse.akkaauth.common.RemoteApi.{GetPublicKeyRequest, GetPublicKeyResponse, ResolveApiTokenRequest, ResolveApiTokenResponse}
import org.s4s0l.betelgeuse.akkaauth.common.{RemoteApi, SerializedToken}
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class RemoteAuthProviderApi(remoteManagerTarget: ActorTarget) extends RemoteApi {
  override def resolveToken(token: SerializedToken)
                           (implicit ec: ExecutionContext)
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

  override def getPublicKey()
                           (implicit ec: ExecutionContext)
  : Future[RemoteApi.GetPublicKeyResponse] = {
    remoteManagerTarget.?(GetPublicKeyRequest())(3.seconds)
      .map(_.asInstanceOf[GetPublicKeyResponse])
  }

}
