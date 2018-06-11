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

/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.streaming

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.reflect.ClassTag

/**
  * @author Maciej Flak
  */
class BgStreamingExtension(private val system: ExtendedActorSystem) extends Extension {

  //fixme: config can be implicit as it is implicitly provided in BgService
  def buildStreamingAccess[K <: AnyRef, V <: AnyRef](config: Config)(implicit k: ClassTag[K], v: ClassTag[V], serializer: KafkaSerializers = defaultSerializers): StreamingAccess[K, V] = {
    implicit val systemA: ExtendedActorSystem = system
    val ec = system.dispatchers.lookup(config.string("poolName").getOrElse("streaming.context.streaming-io-dispatcher"))
    new KafkaAccess(config)(k, v, system, serializer, ec, ActorMaterializer())
  }

  import KafkaSerializers._

  implicit lazy val jacksonSerializer: JacksonJsonSerializer = new JacksonJsonSerializer()
  implicit lazy val defaultKeyValueSerializer: StreamingSerializer = jacksonSerializer


  def defaultSerializers: KafkaSerializers = KafkaSerializers(defaultKeyValueSerializer, defaultKeyValueSerializer)

}


object BgStreamingExtension extends ExtensionId[BgStreamingExtension] with ExtensionIdProvider {

  override def get(system: ActorSystem): BgStreamingExtension = system.extension(this)

  override def apply(system: ActorSystem): BgStreamingExtension = system.extension(this)

  override def lookup(): BgStreamingExtension.type = BgStreamingExtension

  override def createExtension(system: ExtendedActorSystem): BgStreamingExtension =
    new BgStreamingExtension(system)
}

