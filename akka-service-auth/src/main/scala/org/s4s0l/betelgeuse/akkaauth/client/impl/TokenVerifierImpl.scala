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

package org.s4s0l.betelgeuse.akkaauth.client.impl

import java.security.PublicKey
import java.util.Date

import akka.actor.ActorRef
import akka.util.Timeout
import com.fasterxml.jackson.core.JsonProcessingException
import org.s4s0l.betelgeuse.akkaauth.client.ClientExceptions._
import org.s4s0l.betelgeuse.akkaauth.client.TokenVerifier
import org.s4s0l.betelgeuse.akkaauth.client.TokenVerifier.{TokenExpired, TokenFormatError, TokenProcessingError}
import org.s4s0l.betelgeuse.akkaauth.client.impl.TokenVerifierImpl.JwtAttributes
import org.s4s0l.betelgeuse.akkaauth.common
import org.s4s0l.betelgeuse.akkaauth.common.TokenType._
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer}
import pdi.jwt.exceptions.JwtException
import pdi.jwt.{JwtAlgorithm, JwtJson4s}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class TokenVerifierImpl[A](publicKey: PublicKey, attrsUnmarshaller: Map[String, String] => A)
                          (implicit serializer: JacksonJsonSerializer)
  extends TokenVerifier[A] {

  private def throwFormatEx(message: String) =
    throw TokenInvalidException(TokenFormatError(message))

  override def verify(token: common.SerializedToken)
                     (implicit ec: ExecutionContext,
                      timeout: Timeout,
                      sender: ActorRef = ActorRef.noSender)
  : Future[common.AuthInfo[A]] = {
    val decodingResult = JwtJson4s
      .decodeAll(token.token, publicKey, Seq(JwtAlgorithm.RS256))
      .map { it =>
        val (_, c, _) = it
        val content = serializer.simpleFromString[JwtAttributes](c.content)
        val info = common.AuthInfo[A](
          common.UserInfo[A](
            login = content.login,
            userId = UserId(c.subject.getOrElse(throwFormatEx("subject missing"))),
            grants = content.roles.map(Grant(_)).toSet,
            attributes = attrsUnmarshaller(content.attributes)
          ),
          TokenInfo(
            expiration = new Date(c.expiration.getOrElse(throwFormatEx("expiration missing"))),
            issuedAt = new Date(c.issuedAt.getOrElse(throwFormatEx("issuedAt missing"))),
            issuer = c.issuer.orElse(throwFormatEx("issuer missing")),
            tokenType = content.tokenType match {
              case `accessTokenName` =>
                AccessToken(
                  TokenId(c.jwtId.getOrElse(throwFormatEx("jwtId missing"))),
                  token
                )
              case `refreshTokenName` =>
                AccessToken(
                  TokenId(c.jwtId.getOrElse(throwFormatEx("jwtId missing"))),
                  token
                )
            }
          )
        )
        if (info.tokenInfo.expiration.getTime < System.currentTimeMillis()) {
          throw TokenInvalidException(TokenExpired())
        }
        info
      }
    Future.fromTry(decodingResult.recover {
      case ex: JwtException => throw TokenInvalidException(TokenProcessingError(ex.getMessage))
      case ex: JsonProcessingException => throw TokenInvalidException(TokenFormatError(ex.getMessage))
    })
  }

}

object TokenVerifierImpl {


  case class JwtAttributes(
                            tokenType: String,
                            login: Option[String],
                            roles: List[String],
                            attributes: Map[String, String]
                          ) extends JacksonJsonSerializable


}
