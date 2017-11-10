/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-05 21:08
 */

package org.s4s0l.betelgeuse.akkacommons.serialization

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import spray.json.{CompactPrinter, DefaultJsonProtocol, RootJsonFormat}


/**
  * @author Marcin Wielgus
  */
object JsonProtocol {
}


trait JsonProtocol[T] extends SprayJsonSupport with DefaultJsonProtocol {


  implicit def toJson: RootJsonFormat[T]

  //  implicitly[ClassTag[T]].runtimeClass
  implicit val toEntity: ToEntityMarshaller[T] = sprayJsonMarshallerConverter(toJson)(CompactPrinter)
}
