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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import akka.actor.ActorSystem
import akka.persistence.BuiltInSerializerHints
import akka.serialization.{Serialization, SerializationExtension, SerializerWithStringManifest, Serializers}
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.RoachSerializer.Serialized
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer}

/**
  * config should point to place where serialized configuration may be found,
  * currently 'serializerHintsClass' string value is expected (optionally).
  *
  * @author Marcin Wielgus
  */
class RoachSerializer(system: ActorSystem, elementConfig: Config) {


  private implicit val jsonSerializer: JacksonJsonSerializer = new JacksonJsonSerializer()
  private implicit val simpleSerializer: Serialization = SerializationExtension.get(system)
  private val serializerHints: RoachSerializerHints = RoachSerializer.getSerializerHints(elementConfig)

  def serialize(value: Any)
  : Serialized = {
    value match {
      case jsonCapableValue: JacksonJsonSerializable =>
        Serialized(
          jsonSerializer.asSimple.toString(jsonCapableValue),
          value.getClass.getName)

      case wrappingCandidate
        if serializerHints.wrap.isDefinedAt(wrappingCandidate) =>
        val jsonSerializable = serializerHints.wrap.apply(wrappingCandidate)
        Serialized(
          jsonSerializer.asSimple.toString(jsonSerializable),
          jsonSerializable.getClass.getName)

      case jsonCapableCandidate: AnyRef
        if serializerHints.useJackson.isDefinedAt(jsonCapableCandidate)
          && serializerHints.useJackson(jsonCapableCandidate) =>
        Serialized(
          jsonSerializer.asSimple.toString(jsonCapableCandidate),
          value.getClass.getName)

      case binary: AnyRef
        if serializerHints.useBinary.isDefinedAt(binary)
          && serializerHints.useBinary(binary) =>
        val sm = simpleSerializer.serializerFor(binary.getClass)
        Serialized(
          jsonSerializer.asSimple.toString(JsonBinaryWrapper(
            binary.getClass.getName,
            Some(Serializers.manifestFor(sm, binary)),
            simpleSerializer.serialize(binary).get)),
          classOf[JsonBinaryWrapper].getName
        )

      case _ => throw new ClassCastException(s"Event of class ${value.getClass.getName} does not implement JacksonJsonSerializable!!!")
    }

  }


  def deserialize(serializedValue: String, valueClass: String): Any = {
    val eventClass = Class.forName(valueClass).asInstanceOf[Class[AnyRef]]
    jsonSerializer.asSimple.fromStringToClass(serializedValue, eventClass) match {
      case JsonBinaryWrapper(className, manifest, binary) =>
        val expectedClass = Class.forName(className).asInstanceOf[Class[AnyRef]]
        val serializerToUse = simpleSerializer.serializerFor(expectedClass)
        (manifest, serializerToUse) match {
          case (Some(m), _: SerializerWithStringManifest) =>
            simpleSerializer.deserialize(binary, serializerToUse.identifier, m).get
          case (None, _: SerializerWithStringManifest) =>
            throw new Exception(s"In json binary wrapper for class $className, there is no string manifest!!")
          case (_, s) =>
            s.fromBinary(binary, expectedClass)
        }

      case wrapped: RoachSerializerHints.HintWrapped =>
        if (serializerHints.unwrap.isDefinedAt(wrapped)) {
          serializerHints.unwrap.apply(wrapped)
        } else {
          throw new Exception(s"Class ${wrapped.getClass.getName} is HintWrapped but hit cannot deserialize it!")
        }

      case x => x
    }
  }


}

object RoachSerializer {
  private def getSerializerHints(config: Config): RoachSerializerHints = {
    import org.s4s0l.betelgeuse.utils.AllUtils._
    config.string("serializerHintsClass")
      .map(Class.forName(_).asInstanceOf[Class[RoachSerializerHints]])
      .map(_.newInstance())
      .map(_.orElse(new BuiltInSerializerHints()))
      .getOrElse(new BuiltInSerializerHints(): RoachSerializerHints)
  }

  case class Serialized(value: String, valueClass: String)
}
