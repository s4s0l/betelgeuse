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
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.fasterxml.jackson.annotation.{JsonInclude, JsonSubTypes, JsonTypeInfo}
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

  @JsonInclude(Include.NON_NULL)
  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes(Array(
    new Type(name = "expired", value = classOf[TokenExpired]),
    new Type(name = "format", value = classOf[TokenFormatError]),
    new Type(name = "processing", value = classOf[TokenProcessingError]),
    new Type(name = "signature", value = classOf[TokenSignatureError]),
    new Type(name = "revoked", value = classOf[TokenRevoked]),
    new Type(name = "issuer", value = classOf[TokenUnknownIssuer]),
  ))
  sealed trait TokenInvalidReason {
    def msg: String
  }

  case class TokenExpired() extends TokenInvalidReason {
    def msg = "Token expired"
  }

  case class TokenFormatError(message: String) extends TokenInvalidReason {
    def msg = s"Token format error: $message"
  }

  case class TokenProcessingError(message: String) extends TokenInvalidReason {
    def msg = s"Error during token processing: $message"
  }

  case class TokenSignatureError() extends TokenInvalidReason {
    def msg = s"Token signature Error"
  }

  case class TokenRevoked() extends TokenInvalidReason {
    def msg = s"Token revoked"
  }

  case class TokenUnknownIssuer() extends TokenInvalidReason {
    def msg = s"Token issuer error"
  }

}
