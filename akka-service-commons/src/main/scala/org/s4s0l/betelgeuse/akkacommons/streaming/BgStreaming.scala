/*
 * Copyright© 2017 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.streaming

import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkacommons.BgService

import scala.reflect.ClassTag

/**
  * Maps config to [[StreamingAccess]]
  *
  * @author Maciej Flak
  */
trait BgStreaming extends BgService {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgStreaming])

  implicit def streamingExtension: BgStreamingExtension = BgStreamingExtension(system)

  def getKafkaAccess[K, V](name: String)(implicit k: ClassTag[K], v: ClassTag[V], serializer: KafkaSerializers = streamingExtension.defaultSerializers): StreamingAccess[K, V] = getKafkaAccessForConfigKey(s"streaming.context.additional.kafka.$name")


  private def getKafkaAccessForConfigKey[K, V](name: String)(implicit k: ClassTag[K], v: ClassTag[V], serializer: KafkaSerializers): StreamingAccess[K, V] = BgStreamingExtension(system).buildStreamingAccess[K, V](config.getConfig(name))

  def defaultKafkaAccess[K, V]()(implicit k: ClassTag[K], v: ClassTag[V], serializer: KafkaSerializers = streamingExtension.defaultSerializers): StreamingAccess[K, V] = getKafkaAccessForConfigKey[K, V]("streaming.context")(k, v, serializer)


  abstract override def customizeConfiguration: Config = {
    LOGGER.info("Customize config with streaming.conf with fallback to...")
    val url = if (this.serviceInfo.docker) s"${systemName}_kafka:9092" else "127.0.0.1:9092"
    val customConfig = ConfigFactory.parseString(
      s"""
         |streaming.context.bootstrap-servers="$url"
      """.stripMargin)
    ConfigFactory.parseResources("streaming.conf")
      .withFallback(customConfig)
      .withFallback(super.customizeConfiguration)
  }


  override protected def initialize(): Unit = {
    super.initialize()
    LOGGER.info("initializing")
    system.registerExtension(BgStreamingExtension)
    LOGGER.info("initialized")
  }

}
