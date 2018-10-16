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

package org.s4s0l.betelgeuse.akkaauth.client

import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.common.{AuthInfo, SerializedToken}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait TokenVerifier[A] {

  def verify(serializedToken: SerializedToken)
            (implicit ec: ExecutionContext,
             timeout: Timeout,
             sender: ActorRef = ActorRef.noSender)
  : Future[AuthInfo[A]]

}

object TokenVerifier {

  case class TokenInvalidException(reason: TokenInvalidReason)
    extends Exception(reason.msg)

  sealed trait TokenInvalidReason {
    def msg: String
  }

  case class TokenExpired() extends TokenInvalidReason {
    val msg = "Token expired"
  }

  case class TokenFormatError(message: String) extends TokenInvalidReason {
    val msg = s"Token format error: $message"
  }

  case class TokenProcessingError(message: String) extends TokenInvalidReason {
    val msg = s"Error during token processing: $message"
  }

  case class TokenSignatureError() extends TokenInvalidReason {
    val msg = s"Token signature Error"
  }

  case class TokenRevoked() extends TokenInvalidReason {
    val msg = s"Token revoked"
  }

  case class TokenUnknownIssuer() extends TokenInvalidReason {
    val msg = s"Token issuer error"
  }

}
