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

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.serialization.{Serialization, Serializers}
import akka.util.ByteString
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.databind.{DeserializationContext, JsonNode, SerializerProvider}

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Payload for messages. Could be just ByteString, but bs is copying arrays all the time...
  * This payload wraps either byte string or Array[Byte], and allows conversion between both.
  * User of this class is not aware whether it was created with string or byte string and
  * can use it as both. or even as string.
  *
  * DO NOT MUTATE!
  *
  * @author Marcin Wielgus
  */
@SerialVersionUID(2L)
@JsonSerialize(using = classOf[PayloadSerializer])
@JsonDeserialize(using = classOf[PayloadDeserializer])
class Payload[P] private(
                          val manifest: String,
                          val contents: () => Either[ByteString, Array[Byte]]

                        )
  extends Serializable {

  @transient lazy val asArray: Array[Byte] = {
    _binaryContents.left.map(_.toArray[Byte]).left
      .getOrElse(_binaryContents.right.get)
  }
  @transient lazy val asBytes: ByteString = {
    _binaryContents.left.getOrElse(ByteString(_binaryContents.right.get))
  }
  @transient lazy val asString: String = {
    _binaryContents.right.map(new String(_, "utf8")).getOrElse(_binaryContents.left.get.decodeString(StandardCharsets.UTF_8))
  }
  @transient lazy val payloadSize: Int = {
    _binaryContents.right.map(_.length).getOrElse(_binaryContents.left.get.length)
  }
  @transient private lazy val _binaryContents = contents()

  def asObject(implicit classTag: ClassTag[P], serializer: Serialization): P = {
    Payload.asObject(manifest, asArray)(classTag, serializer)
  }

  def toObject[T <: AnyRef](implicit classTag: ClassTag[T], serializer: Serialization): T = {
    Payload.asObject(manifest, asArray)(classTag, serializer)
  }

  @JsonIgnore
  def isEmpty: Boolean = payloadSize == 0

  override def equals(other: Any): Boolean = other match {
    case that: Payload[_] =>
      (that canEqual this) &&
        asArray.sameElements(that.asArray)
    case _ => false
  }

  private def canEqual(other: Any): Boolean = other.isInstanceOf[Payload[_]]

  override def hashCode(): Int = {
    val state = Seq(contents)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object Payload {

  implicit def fromObject[T <: AnyRef](value: T)
                                      (implicit serializer: Serialization): Payload[T] = {
    val str = Serializers.manifestFor(serializer.serializerFor(value.getClass), value)
    new Payload(str, () => Right(serializer.serialize(value).get))
  }

  implicit def apply(bytes: ByteString): Payload[ByteString] = new Payload("bytes", () => Left(bytes))

  implicit def apply(bytes: Array[Byte]): Payload[Array[Byte]] = new Payload("array", () => Right(bytes))

  implicit def apply(string: String): Payload[String] = new Payload("string", () => Right(string.getBytes("utf8")))

  implicit def toResponseMarshallable(p: Payload[_]): ToResponseMarshallable =
    if (p._binaryContents.isLeft) p._binaryContents.left.get else p._binaryContents.right.get

  val emptyString: Payload[String] = new Payload("empty", () => Right(Array()))

  val emptyArray: Payload[Array[Byte]] = new Payload("empty", () => Right(Array()))

  val emptyUnit: Payload[AnyRef] = new Payload("empty", () => Right(Array()))

  val emptyBytes: Payload[ByteString] = new Payload("empty", () => Right(Array()))

  def serialize(value: Payload[_], gen: JsonGenerator, provider: SerializerProvider): Unit = {
    val array = value.asArray
    gen.writeStartObject()
    gen.writeStringField("manifest", value.manifest)
    gen.writeBinaryField("data", array)
    gen.writeEndObject()
  }

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): Payload[Any] = {
    val node: JsonNode = jp.getCodec.readTree(jp)
    val manifest = node.get("manifest").asText
    val data = node.get("data").asInstanceOf[TextNode].binaryValue()
    new Payload[Any](manifest, () => Right(data))
  }

  private def asObject[T](manifest: String, contents: Array[Byte])
                         (implicit classTag: ClassTag[T], serializer: Serialization)
  : T = {
    val ser = serializer.serializerFor(classTag.runtimeClass)
    serializer.deserialize(contents, ser.identifier, manifest).get.asInstanceOf[T]
  }

}
