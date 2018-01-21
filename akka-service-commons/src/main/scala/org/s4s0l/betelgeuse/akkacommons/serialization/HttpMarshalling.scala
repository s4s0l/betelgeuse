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



package org.s4s0l.betelgeuse.akkacommons.serialization

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{MediaTypes, MessageEntity}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, PredefinedFromEntityUnmarshallers}

import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */
class HttpMarshalling(val serializer: JacksonJsonSerializer) {

  def marshaller[T <: AnyRef]: ToEntityMarshaller[T] = Marshaller.combined[T, Array[Byte], MessageEntity](serializer.toBinary(_))(Marshaller.byteArrayMarshaller(MediaTypes.`application/json`))


  def unmarshaller[T <: AnyRef](implicit classTag: ClassTag[T]): FromEntityUnmarshaller[T] =
    PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller.forContentTypes(MediaTypes.`application/json`).map { it =>
      serializer.fromBinary(it, classTag.runtimeClass).asInstanceOf[T]
    }

}
