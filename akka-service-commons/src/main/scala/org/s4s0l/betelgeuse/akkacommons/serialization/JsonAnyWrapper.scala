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

import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.s4s0l.betelgeuse.akkacommons.serialization.JsonAnyWrapper.NullWrapper

import scala.language.implicitConversions

/**
  * @author Marcin Wielgus
  */
case class JsonAnyWrapper private(
                                   @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS,
                                     defaultImpl = classOf[NullWrapper],
                                     include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
                                   wrapped: AnyRef)
  extends JacksonJsonSerializable {


}


object JsonAnyWrapper {
  implicit def toOptional[T](jsonAnyWrapper: JsonAnyWrapper): Option[T] = {
    jsonAnyWrapper.wrapped match {
      case NullWrapper() => None
      case StringWrapper(s) => Some(s.asInstanceOf[T])
      case x => Some(x.asInstanceOf[T])
    }
  }

  implicit def apply[T](o: Option[T]): JsonAnyWrapper = {
    o match {
      case None =>
        JsonAnyWrapper(NullWrapper())
      case Some(x: String) =>
        JsonAnyWrapper(StringWrapper(x))
      case Some(x: JacksonJsonSerializable) =>
        JsonAnyWrapper(x)
      case _ =>
        throw new UnsupportedOperationException(s"type of $o is not supported! missing JacksonJsonSerializable ??")
    }
  }


  case class StringWrapper(stringValue: String)

  case class NullWrapper()

}
