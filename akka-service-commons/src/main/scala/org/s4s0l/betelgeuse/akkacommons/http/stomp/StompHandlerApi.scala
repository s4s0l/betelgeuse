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



package org.s4s0l.betelgeuse.akkacommons.http.stomp

import akka.http.scaladsl.model.headers.`Content-Type`
import org.s4s0l.betelgeuse.utils.UuidUtils
import org.s4s0l.betelgeuse.utils.stomp.Stomp

/**
  * @author Marcin Wielgus
  */
object StompHandlerApi {

  sealed trait StompMessageDestination {

    val webSocketName: String

    def toUserSpaceDestination: String

    def toServerSpaceDestination: String
  }

  object StompMessageDestination {
    private val DestinationMatch = "/(topic|user|session)/([a-zA-Z0-9_\\-]+)".r

    def apply(webSocketName: String, userId: String, sessionId: String, dest: String): StompMessageDestination = {
      dest match {
        case DestinationMatch(t, name) =>
          t match {
            case "topic" => TopicDestination(webSocketName, name)
            case "user" => UserDestination(webSocketName, name, userId)
            case "session" => SessionDestination(webSocketName, name, sessionId)
            case _ => UnsupportedDestination(webSocketName, dest)
          }
        case _ => UnsupportedDestination(webSocketName, dest)
      }
    }
  }


  case class TopicDestination(webSocketName: String, name: String) extends StompMessageDestination {
    override def toUserSpaceDestination = s"/topic/$name"

    override def toServerSpaceDestination = s"/ws-$webSocketName/topic/$name"
  }

  case class UserDestination(webSocketName: String, name: String, userId: String) extends StompMessageDestination {
    override def toUserSpaceDestination = s"/user/$name"

    override def toServerSpaceDestination = s"/ws-$webSocketName/user/$userId/$name"
  }

  case class SessionDestination(webSocketName: String, name: String, sessionId: String) extends StompMessageDestination {
    override def toUserSpaceDestination = s"/session/$name"

    override def toServerSpaceDestination = s"/ws-$webSocketName/session/$sessionId/$name"
  }

  case class UnsupportedDestination(webSocketName: String, what: String) extends StompMessageDestination {
    override def toUserSpaceDestination = throw new RuntimeException(s"Unsupported destination $what in $webSocketName")

    override def toServerSpaceDestination = throw new RuntimeException(s"Unsupported destination $what in $webSocketName")
  }


  sealed trait StompUserMessage


  sealed trait StompServerMessage extends StompUserMessage {
    val destination: StompMessageDestination
  }

  case class StompServerData(destination: StompMessageDestination,
                             payload: String,
                             id: String = UuidUtils.timeBasedUuid().toString,
                             contentType: Option[`Content-Type`] = None, headers:Map[String,String] = Map()) extends StompServerMessage

  case class StompServerReceipt(destination: StompMessageDestination, receiptId: String) extends StompServerMessage

  case class StompServerError(destination: StompMessageDestination,
                              error: String,
                              payload: Option[String] = None,
                              receiptId: Option[String] = None,
                              contentType: Option[`Content-Type`] = None) extends StompServerMessage


  case class StompSource(sessionId: String, userId: String, webSocketName: String) {

    def topic(name: String): StompMessageDestination = TopicDestination(webSocketName, name)

    def user(name: String): StompMessageDestination = UserDestination(webSocketName, name, userId)

    def session(name: String): StompMessageDestination = SessionDestination(webSocketName, name, sessionId)
  }

  sealed trait StompClientMessage extends StompUserMessage {
    val headers: Map[String, String]
    val source: StompSource
  }


  case class StompClientSend(headers: Map[String, String],
                             source: StompSource,
                             id: String,
                             payload: Option[String]) extends StompClientMessage {
    def destination: String = headers(Stomp.Headers.Send.DESTINATION)
  }

  case class StompClientAck(headers: Map[String, String],
                            source: StompSource,
                            id: String) extends StompClientMessage {

  }

  case class StompClientNAck(headers: Map[String, String],
                             source: StompSource,
                             id: String) extends StompClientMessage {

  }

}
