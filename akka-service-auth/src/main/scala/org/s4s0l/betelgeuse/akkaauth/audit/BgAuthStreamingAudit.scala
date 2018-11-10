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

package org.s4s0l.betelgeuse.akkaauth.audit

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkaauth.BgAuthBase
import org.s4s0l.betelgeuse.akkaauth.audit.StreamingAuditDto.ServiceInfo
import org.s4s0l.betelgeuse.akkacommons.streaming.BgStreaming

/**
  * @author Marcin Wielgus
  */
trait BgAuthStreamingAudit[A]
  extends BgStreaming {
  this: BgAuthBase[A] =>

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgAuthStreamingAudit[_]])

  override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with auth-streaming.conf with fallback to...")
    ConfigFactory.parseResources("auth-streaming.conf").withFallback(super.customizeConfiguration)
  }

  private lazy val bgAuthAuditGlobalProducer: Events2Streams[StreamingAuditDto] = {
    val access = createDefaultKafkaAccess[String, StreamingAuditDto]()
    closeOnShutdown(access)
    Events2Streams[String, StreamingAuditDto](
      access.producer,
      "auth-audit-producer",
      config.getConfig("bg.auth.streaming"),
      it => Some(it.id)
    )
  }

  protected lazy val bgAuthStreamingAudit: StreamingAudit[A] = new StreamingAudit[A](
    ServiceInfo(serviceId.systemName, serviceInfo.instance.toString),
    jwtAttributeMapper,
    bgAuthAuditGlobalProducer.publishAsync
  )

  override protected def initialize(): Unit = {
    super.initialize()
    bgAuthStreamingAudit
  }
}

