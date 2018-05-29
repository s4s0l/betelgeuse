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

package akka.persistence

import akka.cluster.sharding.ClusterShardingSerializable
import akka.persistence.BuiltInSerializerHints.PersistentFSMSnapshot
import akka.persistence.fsm.PersistentFSM
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.{JsonSimpleTypeWrapper, RoachSerializerHints}
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer, SimpleSerializer}

import scala.concurrent.duration.FiniteDuration


//handle persistent fsm statechange event and
//snapshot (its fucking private!!
class BuiltInSerializerHints extends RoachSerializerHints {

  override def useJackson: PartialFunction[Any, Boolean] = {
    case _: PersistentFSM.StateChangeEvent => true
  }

  override def useBinary: PartialFunction[Any, Boolean] = {
    case _:ClusterShardingSerializable => true
  }

  override def wrap(implicit jsonSerializer: JacksonJsonSerializer,
                    simpleSerializer: SimpleSerializer)
  : PartialFunction[Any, RoachSerializerHints.HintWrapped] = {
    case stringValue: String =>
      JsonSimpleTypeWrapper(Some(stringValue), None, None, None)
    case int: Int =>
      JsonSimpleTypeWrapper(None, Some(int), None, None)
    case long: Long =>
      JsonSimpleTypeWrapper(None, None, Some(long), None)
    case bool: Boolean =>
      JsonSimpleTypeWrapper(None, None, None, Some(bool))
    case PersistentFSM.PersistentFSMSnapshot(stateId, data: JacksonJsonSerializable, x) =>
      PersistentFSMSnapshot(stateId, data, x)
  }

  override def unwrap(implicit jsonSerializer: JacksonJsonSerializer,
                      simpleSerializer: SimpleSerializer)
  : PartialFunction[RoachSerializerHints.HintWrapped, Any] = {
    case JsonSimpleTypeWrapper(Some(stringValue), None, None, None) =>
      stringValue
    case JsonSimpleTypeWrapper(None, Some(int), None, None) =>
      int
    case JsonSimpleTypeWrapper(None, None, Some(long), None) =>
      long
    case JsonSimpleTypeWrapper(None, None, None, Some(bool)) =>
      bool
    case PersistentFSMSnapshot(stateId, data, x) =>
      PersistentFSM.PersistentFSMSnapshot(stateId, data, x)
  }
}


object BuiltInSerializerHints {

  case class PersistentFSMSnapshot(stateIdentifier: String,
                                   @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS,
                                     include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
                                   data: JacksonJsonSerializable,
                                   timeout: Option[FiniteDuration])
    extends RoachSerializerHints.HintWrapped

}