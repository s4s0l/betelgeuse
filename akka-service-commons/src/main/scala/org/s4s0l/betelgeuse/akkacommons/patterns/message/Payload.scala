/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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

package org.s4s0l.betelgeuse.akkacommons.patterns.message

import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.serialization.{Serialization, Serializers}
import akka.util.ByteString
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.databind.{DeserializationContext, JsonNode, SerializerProvider}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Json serializable payload wrapper, gives some apis for creating common types that do not need
  * serializer, and for complex types if forces usage of serializer.
  *
  * DO NOT MUTATE its contents.
  *
  * @author Marcin Wielgus
  */
@SerialVersionUID(2L)
@JsonSerialize(using = classOf[PayloadSerializer])
@JsonDeserialize(using = classOf[PayloadDeserializer])
class Payload[P <: AnyRef] private(
                                    val manifest: String,
                                    val contents: () => ByteString
                        )
  extends JacksonJsonSerializable {

  @transient lazy val asArray: Array[Byte] = {
    _binaryContents.toArray
  }
  @transient lazy val asBytes: ByteString = {
    _binaryContents
  }
  @transient lazy val asString: String = {
    _binaryContents.decodeString(StandardCharsets.UTF_8)
  }
  @transient lazy val payloadSize: Int = {
    _binaryContents.length
  }
  @transient private lazy val _binaryContents = contents()

  def unwrap(implicit classTag: ClassTag[P], serializer: Serialization): P = {
    deserializeTo[P]
  }

  def deserializeTo[T <: AnyRef](implicit classTag: ClassTag[T], serializer: Serialization): T = {
    manifest match {
      case "string" if classTag.runtimeClass == classOf[String] => asString.asInstanceOf[T]
      case "array" if classTag.runtimeClass == classOf[Array[Byte]] => asArray.asInstanceOf[T]
      case "bytes" if classTag.runtimeClass == classOf[ByteString] => asBytes.asInstanceOf[T]
      case _ if classTag.runtimeClass == classOf[ByteString] => asBytes.asInstanceOf[T]
      case _ => Payload.asObject(manifest, asArray)(classTag, serializer)
    }
  }

  @JsonIgnore
  def isEmpty: Boolean = payloadSize == 0

  override def equals(other: Any): Boolean = other match {
    case that: Payload[_] =>
      (that canEqual this) &&
        asBytes.equals(that.asBytes)
    case _ => false
  }

  private def canEqual(other: Any): Boolean = other.isInstanceOf[Payload[_]]

  override def hashCode(): Int = {
    val state = Seq(contents)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object Payload {

  implicit def wrap[T <: AnyRef](value: T)
                                (implicit serializer: Serialization): Payload[T] = {
    value match {
      case null => throw new Exception("Payload may not be null")
      case a if a.getClass == classOf[String] =>
        Payload(a.asInstanceOf[String]).asInstanceOf[Payload[T]]
      case a if a.getClass == classOf[Array[Byte]] =>
        Payload(a.asInstanceOf[Array[Byte]]).asInstanceOf[Payload[T]]
      case a if a.getClass == classOf[ByteString] =>
        Payload(a.asInstanceOf[ByteString]).asInstanceOf[Payload[T]]
      case _ =>
        val str = Serializers.manifestFor(serializer.serializerFor(value.getClass), value)
        new Payload(str, () => ByteString(serializer.serialize(value).get))
    }
  }

  implicit def apply(bytes: ByteString): Payload[ByteString] = new Payload("bytes", () => bytes)

  @deprecated("Do not use arrays as payload use byte string", since = "2018.06.12")
  implicit def toArray(bytes: ByteString): Payload[Array[Byte]] = new Payload("array", () => bytes)

  @deprecated("Do not use arrays as payload use byte string", since = "2018.06.12")
  implicit def apply(bytes: Array[Byte]): Payload[Array[Byte]] = new Payload("array", () => ByteString(bytes))

  implicit def apply(string: String): Payload[String] = new Payload("string", () => ByteString(string.getBytes("utf8")))

  implicit def toResponseMarshallable(p: Payload[_]): ToResponseMarshallable =
    p._binaryContents

  val emptyString: Payload[String] = new Payload("empty", () => ByteString.empty)

  val emptyArray: Payload[Array[Byte]] = new Payload("empty", () => ByteString.empty)

  val emptyUnit: Payload[NotUsed] = new Payload("empty", () => ByteString.empty)

  val emptyBytes: Payload[ByteString] = new Payload("empty", () => ByteString.empty)

  def serialize(value: Payload[_], gen: JsonGenerator, provider: SerializerProvider): Unit = {
    val array = value.asArray
    gen.writeStartObject()
    gen.writeStringField("manifest", value.manifest)
    gen.writeBinaryField("data", array)
    gen.writeEndObject()
  }

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): Payload[AnyRef] = {
    val node: JsonNode = jp.getCodec.readTree(jp)
    val manifest = node.get("manifest").asText
    val data = node.get("data").asInstanceOf[TextNode].binaryValue()
    new Payload[AnyRef](manifest, () => ByteString(data))
  }

  private def asObject[T](manifest: String, contents: Array[Byte])
                         (implicit classTag: ClassTag[T], serializer: Serialization)
  : T = {
    val ser = serializer.serializerFor(classTag.runtimeClass)
    serializer.deserialize(contents, ser.identifier, manifest).get.asInstanceOf[T]
  }

}
