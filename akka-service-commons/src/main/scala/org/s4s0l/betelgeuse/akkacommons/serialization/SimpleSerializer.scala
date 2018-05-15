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


package org.s4s0l.betelgeuse.akkacommons.serialization

import akka.actor.ActorSystem
import akka.serialization.{Serialization, SerializationExtension, Serializer}

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
  * Simplified adapter for akka serializers
  *
  * @author Marcin Wielgus
  */
trait SimpleSerializer {

  def toString(obj: AnyRef): String

  def toBinary(o: AnyRef): Array[Byte]

  def fromString[T <: AnyRef](bytes: String)(implicit classTag: ClassTag[T]): T

  def fromBinary[T <: AnyRef](bytes: Array[Byte])(implicit classTag: ClassTag[T]): T

  def fromStringToClass[T <: AnyRef](bytes: String, clazz: Class[T]): T

  def fromBinaryToClass[T <: AnyRef](bytes: Array[Byte], clazz: Class[T]): T

}

object SimpleSerializer {

  /**
    * Implicit conversion for akka serializer -> simple serializer
    */
  implicit def toSimpleSerializer(serializer: Serializer): SimpleSerializer = {
    new SimpleSerializer {
      override def fromBinary[T <: AnyRef](bytes: Array[Byte])(implicit classTag: ClassTag[T]): T = {
        fromBinaryToClass(bytes, classTag.runtimeClass.asInstanceOf[Class[T]])
      }

      override def toBinary(o: AnyRef): Array[Byte] = {
        serializer.toBinary(o)
      }

      override def toString(obj: AnyRef): String = {
        new String(toBinary(obj), "UTF8")
      }

      override def fromString[T <: AnyRef](bytes: String)(implicit classTag: ClassTag[T]): T = {
        fromStringToClass(bytes, classTag.runtimeClass.asInstanceOf[Class[T]])
      }

      override def fromStringToClass[T <: AnyRef](bytes: String, clazz: Class[T]): T = {
        serializer.fromBinary(bytes.getBytes("UTF8"), clazz).asInstanceOf[T]
      }

      override def fromBinaryToClass[T <: AnyRef](bytes: Array[Byte], clazz: Class[T]): T = {
        serializer.fromBinary(bytes, clazz).asInstanceOf[T]
      }
    }
  }


  /**
    * creates simple serializer facade for akka actorSystem serialization extension
    *
    */
  def apply(implicit actorSystem: ActorSystem): SimpleSerializer = {
    val serialization: Serialization = SerializationExtension.get(actorSystem)
    new SimpleSerializer {
      override def fromBinary[T <: AnyRef](bytes: Array[Byte])(implicit classTag: ClassTag[T]): T = {
        toSimpleSerializer(serialization.serializerFor(classTag.runtimeClass.asInstanceOf[Class[T]])).fromBinary[T](bytes)
      }

      override def toBinary(o: AnyRef): Array[Byte] = {
        toSimpleSerializer(serialization.findSerializerFor(o)).toBinary(o)
      }

      override def toString(obj: AnyRef): String = {
        toSimpleSerializer(serialization.findSerializerFor(obj)).toString(obj)
      }

      override def fromString[T <: AnyRef](bytes: String)(implicit classTag: ClassTag[T]): T = {
        toSimpleSerializer(serialization.serializerFor(classTag.runtimeClass)).fromString[T](bytes)
      }

      override def fromStringToClass[T <: AnyRef](bytes: String, clazz: Class[T]): T = {
        toSimpleSerializer(serialization.serializerFor(clazz)).fromStringToClass[T](bytes, clazz)
      }

      override def fromBinaryToClass[T <: AnyRef](bytes: Array[Byte], clazz: Class[T]): T = {
        toSimpleSerializer(serialization.serializerFor(clazz)).fromBinaryToClass[T](bytes, clazz)
      }
    }
  }
}
