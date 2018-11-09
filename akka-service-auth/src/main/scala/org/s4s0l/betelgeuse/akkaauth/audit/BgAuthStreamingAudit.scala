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

import akka.Done
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.producer.ProducerRecord
import org.s4s0l.betelgeuse.akkaauth.BgAuthBase
import org.s4s0l.betelgeuse.akkaauth.audit.StreamingAuditDto.ServiceInfo
import org.s4s0l.betelgeuse.akkacommons.streaming.BgStreaming

import scala.concurrent.Future

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

  lazy val bgAuthStreamingAuditTopic: String = config.getString("bg.auth.streaming.topic")


  def bgAuthStreamingAuditSink: Sink[StreamingAuditDto, Future[Done]] = {
    val value = createDefaultKafkaAccess[String, StreamingAuditDto]
    closeOnShutdown(value)
    Flow[StreamingAuditDto].map { dto =>
      new ProducerRecord(bgAuthStreamingAuditTopic, dto.id, dto)
    }.toMat(value.producer.sink())(Keep.right)
  }

  lazy val bgAuthStreamingOnEvent: StreamingAuditDto => Unit = {
    val queueSource = Source.queue[StreamingAuditDto](config.getInt("bg.auth.streaming.buffer-size"), OverflowStrategy.fail)
    val (queue, done) = queueSource.toMat(bgAuthStreamingAuditSink)(Keep.both).run()
    done.onComplete {
      case scala.util.Success(_) =>
        LOGGER.info("Audit producer stream stopped")
      case scala.util.Failure(ex) =>
        LOGGER.error("Audit producer stream failed", ex)
        shutdown()
    }
    queue.offer
  }

  lazy val bgAuthStreamingAudit: StreamingAudit[A] = new StreamingAudit[A](
    ServiceInfo(serviceId.systemName, serviceInfo.instance.toString),
    jwtAttributeMapper,
    bgAuthStreamingOnEvent
  )

  override protected def initialize(): Unit = {
    super.initialize()
    bgAuthStreamingAudit
  }
}

