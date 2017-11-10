/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 14:57
 *
 */

package org.s4s0l.betelgeuse.akkacommons.serialization

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, PredefinedFromEntityUnmarshallers}

import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */
class HttpMarshalling(val serializer: SimpleSerializer) {

  def marshaller[T <: AnyRef]: ToEntityMarshaller[T] = Marshaller.combined[T, Array[Byte], MessageEntity](serializer.toBinary(_))


  def unmarshaller[T <: AnyRef](implicit classTag: ClassTag[T]): FromEntityUnmarshaller[T] =
    PredefinedFromEntityUnmarshallers.byteArrayUnmarshaller.map { it =>
      serializer.fromBinary(it, Some(classTag.runtimeClass)).asInstanceOf[T]
    }

}
