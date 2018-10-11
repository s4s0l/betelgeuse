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

package org.s4s0l.betelgeuse.akkacommons.patterns.message

import akka.NotUsed
import org.s4s0l.betelgeuse.akkacommons.patterns.message.Message.ForwardHeaderProvider
import org.s4s0l.betelgeuse.akkacommons.patterns.message.MessageHeaders.{HeaderAccessors, HeaderSetter, _}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.utils.UuidUtils

import scala.language.implicitConversions

/**
  * @author Marcin Wielgus
  */
object Message {

  implicit def toMap(msg: Message[_]): Map[String, String] = msg.headers

  val defaultForward: ForwardHeaderProvider = () => Seq()

  def apply(target: String, id: String, headers: Map[String, String]): Message[NotUsed] = {
    new Message(target, id, headers + createTimestamp, Payload.emptyUnit)
  }

  def apply[P <: AnyRef](target: String, id: String, headers: Map[String, String], payload: Payload[P]): Message[P] = new Message(target, id, headers + createTimestamp, payload)

  def apply[P <: AnyRef](target: String, headers: Map[String, String], payload: Payload[P]): Message[P] = new Message(target, UuidUtils.timeBasedUuid().toString, headers + createTimestamp, payload)

  private def createTimestamp = {
    MessageHeaders.HEADER_CREATION_TIME -> System.currentTimeMillis().toString
  }

  def apply[P <: AnyRef](target: String, payload: Payload[P]): Message[P] = new Message(target, UuidUtils.timeBasedUuid().toString, Map() + createTimestamp, payload)

  trait ForwardHeaderProvider {
    def apply(): Seq[String]
  }

}

@SerialVersionUID(2L)
final case class Message[P <: AnyRef](target: String, id: String, headers: Map[String, String], payload: Payload[P])
  extends HeaderAccessors
    with HeaderSetter[Message[P]]
    with JacksonJsonSerializable {

  override def withHeaders(map: Map[String, String]): Message[P] = copy(headers = headers ++ map)

  override def withHeader(key: String, value: String): Message[P] = {
    copy(headers = headers + (key -> value))
  }

  def response[N <: AnyRef](newTarget: String, newPayload: Payload[N], newHeaders: Map[String, String] = Map())
                           (implicit extraHeaders: ForwardHeaderProvider): Message[N] = {
    Message(newTarget, UuidUtils.timeBasedUuid().toString,
      (followHeaders(RESPONSE_HEADERS, extraHeaders()) + (MessageHeaders.HEADER_CORRELATION_ID -> id)) ++ newHeaders, newPayload)
  }

  def responseError[N <: AnyRef](newTarget: String, errorPayload: String, newHeaders: Map[String, String] = Map())
                                (implicit extraHeaders: ForwardHeaderProvider): Message[N] = {
    Message(newTarget, UuidUtils.timeBasedUuid().toString,
      (followHeaders(RESPONSE_HEADERS, extraHeaders()) + (MessageHeaders.HEADER_CORRELATION_ID -> id)) ++ newHeaders, Payload.apply(errorPayload).asInstanceOf[Payload[N]]).withFailed()
  }

  private def followHeaders(ffwdBase: Seq[String], forwardedHeadersExtra: Seq[String]): Map[String, String] = {
    var frwrded = headers.filter(it => ffwdBase.contains(it._1) || forwardedHeadersExtra.contains(it._1))
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

  def forwardNewPayload[N <: AnyRef](newTarget: String, newPayload: Payload[N], newHeaders: Map[String, String] = Map())
                                    (implicit extraHeaders: ForwardHeaderProvider): Message[N] = {
    Message(newTarget, UuidUtils.timeBasedUuid().toString,
      followHeaders(FORWARDED_HEADERS, extraHeaders()) ++ newHeaders, newPayload)
  }

  def forward(newTarget: String, newHeaders: Map[String, String] = Map())
             (implicit extraHeaders: ForwardHeaderProvider): Message[P] = {
    Message(newTarget, UuidUtils.timeBasedUuid().toString,
      followHeaders(FORWARDED_HEADERS, extraHeaders()) ++ newHeaders, payload)
  }


}