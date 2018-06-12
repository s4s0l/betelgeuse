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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import akka.serialization.Serialization
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.RoachSerializerHints.HintWrapped
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer}

import scala.language.implicitConversions

/**
  * if roach persistence encounters non [[org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable]]
  * class it asks hints if it can either marshall it with json anyway
  * or wrap its binary form in json (slow!!)
  *
  * @author Marcin Wielgus
  */
trait RoachSerializerHints {

  def wrap(implicit jsonSerializer: JacksonJsonSerializer,
           simpleSerializer: Serialization): PartialFunction[Any, HintWrapped]

  def unwrap(implicit jsonSerializer: JacksonJsonSerializer,
             simpleSerializer: Serialization): PartialFunction[HintWrapped, Any]

  def useJackson: PartialFunction[Any, Boolean]

  def useBinary: PartialFunction[Any, Boolean]

}

object RoachSerializerHints {

  trait HintWrapped extends JacksonJsonSerializable

  object Never extends RoachSerializerHints {

    override def wrap(implicit jsonSerializer: JacksonJsonSerializer,
                      simpleSerializer: Serialization)
    : PartialFunction[Any, HintWrapped] = Map.empty

    override def unwrap(implicit jsonSerializer: JacksonJsonSerializer,
                        simpleSerializer: Serialization)
    : PartialFunction[HintWrapped, Any] = Map.empty

    override def useJackson: PartialFunction[Any, Boolean] = Map.empty

    override def useBinary: PartialFunction[Any, Boolean] = Map.empty
  }

  implicit def toBuilder(hints: RoachSerializerHints): Builder = new Builder(hints)

  class Builder(wrapped: RoachSerializerHints, orElseHints: RoachSerializerHints = Never) extends RoachSerializerHints {
    override def wrap(implicit jsonSerializer: JacksonJsonSerializer,
                      simpleSerializer: Serialization)
    : PartialFunction[Any, HintWrapped] =
      wrapped.wrap.orElse(orElseHints.wrap)

    override def unwrap(implicit jsonSerializer: JacksonJsonSerializer,
                        simpleSerializer: Serialization)
    : PartialFunction[HintWrapped, Any] =
      wrapped.unwrap.orElse(orElseHints.unwrap)

    override def useJackson: PartialFunction[Any, Boolean] =
      wrapped.useJackson.orElse(orElseHints.useJackson)

    override def useBinary: PartialFunction[Any, Boolean] =
      wrapped.useBinary.orElse(orElseHints.useBinary)

    def orElse(hints: RoachSerializerHints): RoachSerializerHints = {
      new Builder(this, hints)
    }
  }


}
