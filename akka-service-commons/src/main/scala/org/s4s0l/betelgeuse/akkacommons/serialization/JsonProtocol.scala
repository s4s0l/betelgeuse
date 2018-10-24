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
