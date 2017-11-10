/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-06 11:12
 *
 */

package org.s4s0l.betelgeuse.akkacommons.http.stomp

import java.util.UUID

import akka.http.scaladsl.model.headers.`Content-Type`
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
                             id: String = UUID.randomUUID().toString,
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
