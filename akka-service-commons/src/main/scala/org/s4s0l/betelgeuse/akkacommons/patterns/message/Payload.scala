/*
 * Copyright© 2018 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.patterns.message

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.util.ByteString
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.annotation.{JsonDeserialize, JsonSerialize}
import com.fasterxml.jackson.databind.{DeserializationContext, SerializerProvider}
import org.s4s0l.betelgeuse.akkacommons.serialization.SimpleSerializer

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Payload for messages. Could be just ByteString, but bs is copying arrays all the time...
  * This payload wraps either bytestring or Array[Byte], and allows conversion between both.
  * User of this class is not aware whether it was created with string or bytestring and
  * can use it as both. or even as string.
  *
  * DO NOT MUTATE!
  *
  * @author Marcin Wielgus
  */
@SerialVersionUID(2L)
@JsonSerialize(using = classOf[PayloadSerializer])
@JsonDeserialize(using = classOf[PayloadDeserializer])
class Payload private(val contents: Either[ByteString, Array[Byte]]) extends Serializable {

  def asBytes: ByteString = _asBytes

  def asString: String = _asString

  def asArray: Array[Byte] = _asArray

  def payloadSize: Int = _length

  @transient private lazy val _asArray: Array[Byte] = {
    contents.left.map(_.toArray[Byte]).left
      .getOrElse(contents.right.get)
  }

  @transient private lazy val _asBytes: ByteString = {
    contents.left.getOrElse(ByteString(contents.right.get))
  }

  @transient private lazy val _asString: String = {
    contents.right.map(new String(_, "utf8")).getOrElse(contents.left.get.decodeString(StandardCharsets.UTF_8))
  }

  @transient private lazy val _length: Int = {
    contents.right.map(_.length).getOrElse(contents.left.get.length)
  }

  def asObject[T](implicit classTag: ClassTag[T], serializer: SimpleSerializer): T = {
    Payload.asObject(this)(classTag, serializer)
  }

  @JsonIgnore
  def isEmpty: Boolean = _length == 0

  def canEqual(other: Any): Boolean = other.isInstanceOf[Payload]

  override def equals(other: Any): Boolean = other match {
    case that: Payload =>
      (that canEqual this) &&
        asArray.sameElements(that.asArray)
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(contents)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object Payload {

  implicit def apply(value: AnyRef)
                    (implicit serializer: SimpleSerializer): Payload = {
    Payload(serializer.toBinary(value))
  }

  implicit def apply(bytes: ByteString): Payload = new Payload(Left(bytes))

  implicit def apply(bytes: Array[Byte]): Payload = new Payload(Right(bytes))

  implicit def apply(string: String): Payload = new Payload(Right(string.getBytes("utf8")))

  implicit def asBytes(p: Payload): ByteString = p.asBytes

  implicit def asArray(p: Payload): Array[Byte] = p.asArray

  implicit def asString(p: Payload): String = p.asString

  implicit def toResponseMarshallable(p: Payload): ToResponseMarshallable =
    if (p.contents.isLeft) p.contents.left.get else p.contents.right.get

  val empty: Payload = new Payload(Right(Array()))

  //TODO: how to make it implicit?
  def asObject[T](p: Payload)
                 (implicit classTag: ClassTag[T], serializer: SimpleSerializer)
  : T = {
    serializer.fromBinary[AnyRef](p.asArray)(classTag.asInstanceOf[ClassTag[AnyRef]]).asInstanceOf[T]
  }

  def serialize(value: Payload, gen: JsonGenerator, provider: SerializerProvider): Unit = {
    val array = value.asArray
    gen.writeBinary(array)
  }

  def deserialize(jp: JsonParser, ctxt: DeserializationContext): Payload = {
    val out = new ByteArrayOutputStream(320)
    jp.readBinaryValue(out)
    Payload(out.toByteArray)
  }

}
