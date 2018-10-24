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
package org.s4s0l.betelgeuse.akkaauth.common

import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.client.TokenResolver
import org.s4s0l.betelgeuse.akkaauth.client.TokenVerifier.TokenInvalidReason
import org.s4s0l.betelgeuse.akkaauth.common.RemoteApi.GetPublicKeyResponse
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable

import scala.concurrent.{ExecutionContext, Future}

/**
  * Api exposed by auth provider for auth client services.
  *
  * @author Marcin Wielgus
  */
trait RemoteApi extends TokenResolver {

  def getPublicKey()
                  (implicit ec: ExecutionContext,
                   timeout: Timeout,
                   sender: ActorRef = ActorRef.noSender)
  : Future[GetPublicKeyResponse]
}

object RemoteApi {

  case class ResolveApiTokenRequest(token: SerializedToken)
    extends JacksonJsonSerializable

  sealed trait ResolveApiTokenResponse
    extends JacksonJsonSerializable

  object ResolveApiTokenResponse {

    case class ResolveApiTokenResponseOk(token: SerializedToken)
      extends ResolveApiTokenResponse

    case class ResolveApiTokenResponseNotOk(reason: TokenInvalidReason)
      extends ResolveApiTokenResponse

  }

  case class GetPublicKeyRequest()
    extends JacksonJsonSerializable

  case class GetPublicKeyResponse(base64Key: String)
    extends JacksonJsonSerializable

}
