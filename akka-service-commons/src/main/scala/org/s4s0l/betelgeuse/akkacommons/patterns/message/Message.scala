/*
 * Copyright© 2017 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.patterns.message

import java.util.UUID

import org.s4s0l.betelgeuse.akkacommons.patterns.message.Message.ForwardHeaderProvider
import org.s4s0l.betelgeuse.akkacommons.patterns.message.MessageHeaders.{HeaderAccessors, HeaderSetter, _}

/**
  * @author Marcin Wielgus
  */
object Message {
  implicit def toMap(msg: Message): Map[String, String] = msg.headers

  val defaultForward: ForwardHeaderProvider = () => Seq()

  def apply(target: String, id: String, headers: Map[String, String], payload: String): Message = new Message(target, id, headers + createTimestamp, payload)

  def apply(target: String, headers: Map[String, String], payload: String): Message = new Message(target, UUID.randomUUID().toString, headers + createTimestamp, payload)

  private def createTimestamp = {
    MessageHeaders.HEADER_CREATION_TIME -> System.currentTimeMillis().toString
  }

  def apply(target: String, payload: String): Message = new Message(target, UUID.randomUUID().toString, Map() + createTimestamp, payload)

  trait ForwardHeaderProvider {
    def apply(): Seq[String]
  }

}

@SerialVersionUID(2L)
final case class Message(target: String, id: String, headers: Map[String, String], payload: String)
  extends HeaderAccessors
    with HeaderSetter[Message] {

  override def withHeaders(map: Map[String, String]): Message = copy(headers = headers ++ map)

  override def withHeader(key: String, value: String): Message = {
    copy(headers = headers + (key -> value))
  }

  def response(newTarget: String, newPayload: String, newHeaders: Map[String, String] = Map())
              (implicit extraHeaders: ForwardHeaderProvider): Message = {
    Message(newTarget, UUID.randomUUID().toString,
      (followHeaders(extraHeaders()) + (MessageHeaders.HEADER_CORRELATION_ID -> id)) ++ newHeaders, newPayload)
  }

  def forward(newTarget: String, newPayload: String = payload, newHeaders: Map[String, String] = Map())
             (implicit extraHeaders: ForwardHeaderProvider): Message = {
    Message(newTarget, UUID.randomUUID().toString,
      followHeaders(extraHeaders()) ++ newHeaders, newPayload)
  }

  private def followHeaders(forwardedHeadersExtra: Seq[String]): Map[String, String] = {
    var frwrded = headers.filter(it => FORWARDED_HEADERS.contains(it._1) || forwardedHeadersExtra.contains(it._1))
    if (frwrded.contains(HEADER_TRACE_IDS)) {
      frwrded = frwrded + (HEADER_TRACE_IDS -> (frwrded(HEADER_TRACE_IDS) + "," + id))
    } else {
      frwrded = frwrded + (HEADER_TRACE_IDS -> id)
    }
    if (frwrded.contains(HEADER_TRACE_TARGETS)) {
      frwrded = frwrded + (HEADER_TRACE_TARGETS -> (frwrded(HEADER_TRACE_TARGETS) + "," + target))
    } else {
      frwrded = frwrded + (HEADER_TRACE_TARGETS -> target)
    }
    frwrded
  }


}