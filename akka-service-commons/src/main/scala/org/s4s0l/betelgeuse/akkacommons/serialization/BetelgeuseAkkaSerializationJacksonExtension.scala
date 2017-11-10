/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */



package org.s4s0l.betelgeuse.akkacommons.serialization

import akka.actor.{ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.serialization.SerializationExtension

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaSerializationJacksonExtension(private val system: ExtendedActorSystem) extends Extension {
  def serializationJackson: SimpleSerializer = JacksonJsonSerializer.get(SerializationExtension.get(system)).get

  lazy val httpMarshaller: HttpMarshalling = new HttpMarshalling(serializationJackson)
}


object BetelgeuseAkkaSerializationJacksonExtension extends ExtensionId[BetelgeuseAkkaSerializationJacksonExtension] with ExtensionIdProvider {

  @SerialVersionUID(1L) final case class NamedPut(name: String, ref: ActorRef)

  override def get(system: ActorSystem): BetelgeuseAkkaSerializationJacksonExtension = system.extension(this)

  override def apply(system: ActorSystem): BetelgeuseAkkaSerializationJacksonExtension = system.extension(this)

  override def lookup(): BetelgeuseAkkaSerializationJacksonExtension.type = BetelgeuseAkkaSerializationJacksonExtension

  override def createExtension(system: ExtendedActorSystem): BetelgeuseAkkaSerializationJacksonExtension =
    new BetelgeuseAkkaSerializationJacksonExtension(system)

}
