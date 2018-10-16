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

package org.s4s0l.betelgeuse.akkaauth.manager.impl

import java.security.{PrivateKey, PublicKey}
import java.time.Instant
import java.time.temporal.ChronoField
import java.util.{Date, UUID}

import akka.actor.ActorRef
import akka.http.scaladsl.model.MediaTypes
import akka.util.Timeout
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkaauth.client.impl.TokenVerifierImpl
import org.s4s0l.betelgeuse.akkaauth.client.impl.TokenVerifierImpl.JwtAttributes
import org.s4s0l.betelgeuse.akkaauth.common
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.UserDetailedInfo
import org.s4s0l.betelgeuse.akkaauth.manager.{AdditionalUserAttrsManager, TokenFactory}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtHeader, JwtJson4s}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class TokenFactoryImpl[A](publicKey: PublicKey,
                          privateKey: PrivateKey,
                          attrsUnmarshaller: AdditionalUserAttrsManager[A])
                         (implicit serializer: JacksonJsonSerializer, config: Config)
  extends TokenVerifierImpl(publicKey, attrsUnmarshaller.unMarshallAttrs)
    with TokenFactory[A] {

  private val issuer = config.getString("bg.auth.jwt.issuer")
  private val keyId = config.getString("bg.auth.jwt.keys.id")
  private val loginTokenValidity = config.getDuration("bg.auth.jwt.login-time")

  override def issueLoginToken(userDetails: UserDetailedInfo,
                               grants: Set[Grant])
                              (implicit ec: ExecutionContext,
                               timeout: Timeout,
                               sender: ActorRef = ActorRef.noSender)
  : Future[common.AuthInfo[A]] = {
    val now = new Date()
    val expiry = loginTokenValidity
      .addTo(now.toInstant)
    val nanos = expiry.getLong(ChronoField.NANO_OF_SECOND)
    val epochSeconds = expiry.getLong(ChronoField.INSTANT_SECONDS)
    val expiryDate = Date.from(Instant.ofEpochSecond(epochSeconds, nanos))
    issueToken(userDetails, grants, now, expiryDate)
  }

  override def issueApiToken(userDetails: UserDetailedInfo,
                             grants: Set[Grant],
                             expiration: Date)
                            (implicit ec: ExecutionContext,
                             timeout: Timeout,
                             sender: ActorRef = ActorRef.noSender)
  : Future[common.AuthInfo[A]] = {
    issueToken(userDetails, grants, new Date(), expiration)
  }

  private def toUserInfo(userDetails: UserDetailedInfo,
                         grants: Set[Grant])
                        (implicit ec: ExecutionContext,
                         timeout: Timeout,
                         sender: ActorRef = ActorRef.noSender)
  : Future[UserInfo[A]] = {
    attrsUnmarshaller.mapAttrsToToken(userDetails)
      .map { tokenInfo =>
        common.UserInfo[A](
          userDetails.login,
          userDetails.userId,
          grants,
          tokenInfo
        )
      }
  }

  private def issueToken(userDetails: UserDetailedInfo,
                         grants: Set[Grant],
                         now: Date,
                         expiration: Date)
                        (implicit ec: ExecutionContext,
                         timeout: Timeout,
                         sender: ActorRef = ActorRef.noSender)
  : Future[common.AuthInfo[A]] = {

    toUserInfo(userDetails, grants).map { userInfo =>
      val roles = userInfo.grants.map(_.name)
      val serialized = serializer.simpleToString(JwtAttributes(
        accessTokenName,
        userInfo.login,
        roles.toList.sorted,
        attrsUnmarshaller.marshallAttrs(userInfo.attributes)
      ))
      val jwtHeader = JwtHeader(
        algorithm = Some(JwtAlgorithm.RS256),
        typ = Some("JWT"),
        contentType = Some(MediaTypes.`application/json`.toString()),
        keyId = Some(keyId))
      val jwtId = UUID.randomUUID().toString
      val jwtClaim = JwtClaim(
        content = serialized,
        issuer = Some(issuer),
        expiration = Some(expiration.getTime),
        issuedAt = Some(now.getTime),
        jwtId = Some(jwtId)
      )
      val encoded = JwtJson4s.encode(jwtHeader, jwtClaim, privateKey)
      common.AuthInfo[A](
        userInfo,
        TokenInfo(
          expiration = expiration,
          issuedAt = now,
          issuer = Some(issuer),
          tokenType =
            AccessToken(
              TokenId(jwtId),
              SerializedToken(encoded)
            )
        )
      )
    }
  }


}
