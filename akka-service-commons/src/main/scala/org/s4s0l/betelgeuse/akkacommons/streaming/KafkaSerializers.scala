package org.s4s0l.betelgeuse.akkacommons.streaming

import java.util

import org.s4s0l.betelgeuse.akkacommons.serialization.SimpleSerializer

import scala.reflect.ClassTag

case class KafkaSerializers(keySer: SimpleSerializer, valueSer: SimpleSerializer)

object KafkaSerializers {

  def toKafkaSerializer[T](simpleSerializer: SimpleSerializer, k: ClassTag[T]): org.apache.kafka.common.serialization.Serializer[T] = {
    new org.apache.kafka.common.serialization.Serializer[T] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

      override def serialize(topic: String, data: T): Array[Byte] = simpleSerializer.toBinary(data.asInstanceOf[AnyRef])

      override def close(): Unit = {}
    }
  }


  def toKafkaDeserializer[T](simpleSerializer: SimpleSerializer, k: ClassTag[T]): org.apache.kafka.common.serialization.Deserializer[T] = {
    new org.apache.kafka.common.serialization.Deserializer[T] {
      override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

      override def deserialize(topic: String, data: Array[Byte]): T = {
        simpleSerializer.fromBinary[AnyRef](data)(k.asInstanceOf[ClassTag[AnyRef]]).asInstanceOf[T]
      }

      override def close(): Unit = {}
    }
  }

}