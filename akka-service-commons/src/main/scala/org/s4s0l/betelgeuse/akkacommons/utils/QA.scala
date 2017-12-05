/*
 * Copyright© 2017 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.utils

import java.util.UUID

/**
  * @author Marcin Wielgus
  */
object QA {

  type Uuid = String

  def uuid: Uuid = UUID.randomUUID().toString


  trait Question[ID] {
    def messageId: ID

    def isRelated(answer: Answer[ID]): Boolean = messageId == answer.correlationId
  }

  type UuidQuestion = Question[Uuid]

  @SerialVersionUID(1L)
  final case class QuestionWrapper[ID, V](messageId: ID, value: V)
    extends Question[ID]

  @SerialVersionUID(1L)
  final case class DefaultQuestionWrapper[V](value: V, messageId: String = uuid)
    extends Question[String]

  trait Answer[ID] {
    def correlationId: ID
  }

  @SerialVersionUID(1L)
  final case class AnswerWrapper[ID, V](correlationId: ID, value: V) extends Answer[ID]

  type SimpleResult[ID] = Result[ID, Null]

  type UuidSimpleResult = SimpleResult[Uuid]

  type UuidResult[V] = Result[Uuid, V]

  sealed trait Result[ID, V] extends Answer[ID]

  @SerialVersionUID(1L)
  final case class Ok[ID, V](correlationId: ID, value: V) extends Result[ID, V]

  object Ok {
    def apply[ID, V](correlationId: ID, value: V): Ok[ID, V] = new Ok(correlationId, value)

    def apply[ID](correlationId: ID): Ok[ID, Null] = new Ok[ID, Null](correlationId, null)
  }

  @SerialVersionUID(1L)
  final case class NotOk[ID, V](correlationId: ID, ex: Throwable) extends Result[ID, V] {

    def this(correlationId: ID, message: String) = this(correlationId, new Exception(message))
  }

  object NotOk {
    def apply[ID, V](correlationId: ID, ex: Throwable): NotOk[ID, V] = new NotOk(correlationId, ex)

    def apply[ID, V](correlationId: ID, ex: String): NotOk[ID, V] = new NotOk(correlationId, new Exception(ex))

    def apply[ID, V](correlationId: ID): NotOk[ID, V] = new NotOk(correlationId, new Exception("Simply no!"))
  }

}
