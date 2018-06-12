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

package org.s4s0l.betelgeuse.akkacommons.streaming

import java.util

import akka.serialization.Serialization
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer

import scala.language.implicitConversions
import scala.reflect.ClassTag

case class KafkaSerializers(keySer: StreamingSerializer, valueSer: StreamingSerializer)

object KafkaSerializers {

  def toKafkaSerializer[T <: AnyRef](simpleSerializer: StreamingSerializer, k: ClassTag[T]): org.apache.kafka.common.serialization.Serializer[T] = {
    new org.apache.kafka.common.serialization.Serializer[T] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

      override def serialize(topic: String, data: T): Array[Byte] = simpleSerializer.toBinary(data.asInstanceOf[AnyRef])

      override def close(): Unit = {}
    }
  }


  def toKafkaDeserializer[T <: AnyRef](simpleSerializer: StreamingSerializer, k: ClassTag[T]): org.apache.kafka.common.serialization.Deserializer[T] = {
    new org.apache.kafka.common.serialization.Deserializer[T] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

      override def deserialize(topic: String, data: Array[Byte]): T = {
        simpleSerializer.fromBinary[T](data)(k)
      }

      override def close(): Unit = {}
    }
  }

  implicit def toStreamingSerializer(jacksonSerializer: JacksonJsonSerializer):
  StreamingSerializer = {
    new StreamingSerializer {
      override def toBinary(o: AnyRef): Array[Byte] = jacksonSerializer.toBinary(o)

      override def fromBinary[T <: AnyRef](bytes: Array[Byte])(implicit classTag: ClassTag[T]): T =
        jacksonSerializer.fromBinary(bytes, classTag.runtimeClass).asInstanceOf[T]
    }
  }

  implicit def toStreamingSerializerFromAkka(serializer: Serialization):
  StreamingSerializer = {
    new StreamingSerializer {
      override def toBinary(o: AnyRef): Array[Byte] = serializer.serialize(o).get

      override def fromBinary[T <: AnyRef](bytes: Array[Byte])
                                          (implicit classTag: ClassTag[T]): T =
        serializer.deserialize(bytes, classTag.runtimeClass).get.asInstanceOf[T]
    }
  }


}