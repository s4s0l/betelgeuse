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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable

/**
  * @author Marcin Wielgus
  */
@JsonInclude(Include.NON_EMPTY)
case class JsonSimpleTypeWrapper(
                                  string: Option[String],
                                  int: Option[Int],
                                  long: Option[Long],
                                  bool: Option[Boolean],
                                )
  extends RoachSerializerHints.HintWrapped
