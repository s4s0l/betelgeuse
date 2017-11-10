/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 13:42
 *
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
