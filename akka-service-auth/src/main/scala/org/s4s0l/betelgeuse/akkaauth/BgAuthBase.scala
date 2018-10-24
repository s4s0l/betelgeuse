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

import akka.NotUsed
import com.softwaremill.session._
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkaauth.client.AuthClient
import org.s4s0l.betelgeuse.akkaauth.common.AdditionalAttrsManager
import org.s4s0l.betelgeuse.akkacommons.BgService

import scala.util.Try

/**
  * @author Marcin Wielgus
  */
private[akkaauth] trait BgAuthBase[A]
  extends BgService
    with BgAuthClientDirectives[A] {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgAuthBase[_]])

  override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with auth-client.conf with fallback to...")
    ConfigFactory.parseResources("auth-client.conf").withFallback(super.customizeConfiguration)
  }

  def bgAuthClient: AuthClient[A]

  private[akkaauth] implicit lazy val sessionManager: SessionManager[NotUsed] = {
    val sessionConfig: SessionConfig = SessionConfig.default("do not use _ do not use _ do not use _ do not use _ do not use _ do not use _ do not use _ ")
    new SessionManager[NotUsed](sessionConfig)(new SessionEncoder[NotUsed]() {
      override def encode(t: NotUsed, nowMillis: Long, config: SessionConfig): String =
        throw new IllegalStateException("session manager cannot be used")

      override def decode(s: String, config: SessionConfig): Try[DecodeResult[NotUsed]] =
        throw new IllegalStateException("session manager cannot be used")
    })
  }

  protected def jwtAttributeMapper: AdditionalAttrsManager[A]


}
