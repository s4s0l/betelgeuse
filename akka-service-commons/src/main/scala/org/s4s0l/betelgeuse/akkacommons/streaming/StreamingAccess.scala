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

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, ProducerSettings}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.s4s0l.betelgeuse.akkacommons.serialization.SimpleSerializer

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag


/**
  * @author Maciej Flak
  */
sealed trait StreamingAccess[K, V] {

  val consumer: KafkaConsumer[K, V]

  val producer: KafkaProducer[K, V]

}


class KafkaAccess[K, V](config: Config)(implicit k: ClassTag[K], v: ClassTag[V], system: ActorSystem, ser: KafkaSerializers, ec: ExecutionContext, mat: ActorMaterializer) extends StreamingAccess[K, V] {

  require(k.runtimeClass != classOf[Nothing], "key type cannot be Nothing! For instance use getKafkaAccess[String, String](\"kafka1\") not getKafkaAccess(\"kafka1\")")
  require(v.runtimeClass != classOf[Nothing], "value type cannot be Nothing! For instance use getKafkaAccess[String, String](\"kafka1\") not getKafkaAccess(\"kafka1\")")

  import KafkaSerializers._
  import org.s4s0l.betelgeuse.utils.AllUtils._

  /**
    * reactive kafka provides reasonable defaults for kafka settings at key "akka.kafka.consumer"/"akka.kafka.producer"
    * use customConfPath to point at custom configuration for this Access
    *
    * @param path - path at system config where consumer/producer exist
    * @return config for consumer/producer settings
    */
  def getKafkaConfig(path: Option[String]): Option[Config] = path flatMap { p => system.settings.config.config(p) }


  private[streaming] lazy val consumerSettings: ConsumerSettings[K, V] = {
    val settings = getKafkaConfig(config.string("custom-conf-path")) match {
      case Some(customConfig) => ConsumerSettings(customConfig, toKafkaDeserializer[K](ser.keySer, k), toKafkaDeserializer[V](ser.valueSer, v))
      case None => ConsumerSettings(system, toKafkaDeserializer[K](ser.keySer, k), toKafkaDeserializer[V](ser.valueSer, v))
    }
    settings.withBootstrapServers(config.getString("bootstrap-servers"))
      .withGroupId(config.getString("group-id"))
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.string("offset-reset").getOrElse("earliest"))
  }


  private[streaming] lazy val producerSettings: ProducerSettings[K, V] = {
    val settings = getKafkaConfig(config.string("custom-conf-path")) match {
      case Some(customConfig) => ProducerSettings(customConfig, toKafkaSerializer[K](ser.keySer,k), toKafkaSerializer[V](ser.valueSer,v))
      case None => ProducerSettings(system, toKafkaSerializer[K](ser.keySer,k), toKafkaSerializer[V](ser.valueSer,v))
    }
    settings.withBootstrapServers(config.getString("bootstrap-servers"))
  }

  override lazy val consumer: KafkaConsumer[K, V] = new KafkaConsumerImpl(consumerSettings)

  override lazy val producer: KafkaProducer[K, V] = new KafkaProducerImpl(producerSettings)

}