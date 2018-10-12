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

import akka.http.scaladsl.model.MediaTypes
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkaauth.client.impl.TokenVerifierImpl
import org.s4s0l.betelgeuse.akkaauth.client.impl.TokenVerifierImpl.JwtAttributes
import org.s4s0l.betelgeuse.akkaauth.common
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.TokenFactory
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtHeader, JwtJson4s}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class TokenFactoryImpl(publicKey: PublicKey,
                       privateKey: PrivateKey,
                       config: Config)
                      (implicit serializer: JacksonJsonSerializer)
  extends TokenVerifierImpl(publicKey)
    with TokenFactory {

  private val issuer = config.getString("bg.auth.jwt.issuer")
  private val keyId = config.getString("bg.auth.jwt.keys.id")
  private val loginTokenValidity = config.getDuration("bg.auth.jwt.login-time")

  override def issueLoginToken[A](userInfo: common.UserInfo[A],
                                  marshaller: A => Map[String, String])
                                 (implicit ec: ExecutionContext)
  : Future[common.AuthInfo[A]] = {
    val now = new Date()
    val expiry = loginTokenValidity
      .addTo(now.toInstant)
    val nanos = expiry.getLong(ChronoField.NANO_OF_SECOND)
    val epochSeconds = expiry.getLong(ChronoField.INSTANT_SECONDS)
    val expiryDate = Date.from(Instant.ofEpochSecond(epochSeconds, nanos))
    issueToken(userInfo, now, expiryDate, marshaller)
  }

  override def issueApiToken[A](userInfo: common.UserInfo[A],
                                expiration: Date,
                                marshaller: A => Map[String, String])
                               (implicit ec: ExecutionContext)
  : Future[common.AuthInfo[A]] = {
    issueToken(userInfo, new Date(), expiration, marshaller)
  }

  private def issueToken[A](userInfo: common.UserInfo[A],
                            now: Date,
                            expiration: Date,
                            marshaller: A => Map[String, String])
                           (implicit ec: ExecutionContext)
  : Future[common.AuthInfo[A]] = {
    Future.successful {
      val roles = userInfo.grants.map(_.name)
      val serialized = serializer.simpleToString(JwtAttributes(
        accessTokenName,
        userInfo.login,
        roles.toList.sorted,
        marshaller(userInfo.attributes)
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
