/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-06 23:48
 *
 */

package org.s4s0l.betelgeuse.akkacommons.http.stomp

import java.util.UUID

import akka.NotUsed
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, _}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.http.stomp.SocksHandler.{IncomingSocksMessage, SocksSessionId}
import org.s4s0l.betelgeuse.utils.stomp.SocksParser
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

/**
  * @author Marcin Wielgus
  */
object SocksHandler extends SocksHandler {


  final case class SocksSessionId(webSocketName: String, serverId: String, sessionId: String, internalSessionId: String, userId: String)


  case class IncomingSocksMessage(session: SocksSessionId, payload: String)

}

trait SocksHandler {

  private val log = LoggerFactory.getLogger(getClass)

  def handleSocks(webSocketName: String, flowFactory: SocksSessionId => Flow[IncomingSocksMessage, String, Any]): Route = {
    pathPrefix(webSocketName) {
      (get | options) {
        //see http://sockjs.github.io/sockjs-protocol/sockjs-protocol-0.3.3.html
        pathPrefix("info") {
          complete(200, HttpEntity(ContentTypes.`application/json`,
            s"""
               |{
               |  "websocket" : true,
               |  "cookie_needed" : false,
               |  "origins" : "*:*",
               |  "entropy" : ${Random.nextInt()}
               |}
            """.stripMargin))
        } ~
          pathPrefix(
            Segment / Segment / "websocket") { (serverId, sessionId) =>
            val internalSessionId = UUID.randomUUID().toString
            val userId = internalSessionId
            val session = SocksSessionId(webSocketName, serverId, sessionId, internalSessionId, userId)
            handleWebSocketMessages(socksFlow(session, flowFactory(session)))
          }
      }
    }
  }


  protected def socksFlow(id: SocksSessionId, xxx: Flow[IncomingSocksMessage, String, Any]): Flow[Message, Message, Any] = {


    Flow.fromGraph[Message, Message, Any](GraphDSL.create(xxx) { implicit builder =>
      userFlow =>

        import GraphDSL.Implicits._
        implicit val timeout: Timeout = Timeout(5 seconds)

        val socksMessageToString: FlowShape[Message, String] = builder.add(socksMessageToStringFlow)

        val stringToSocksMessage: FlowShape[String, Message] = builder.add(stringToSocksMessageFlow)

        val stringToStringMessage: FlowShape[String, Message] = builder.add(stringToStringMessageFlow)

        val staticSource = builder.add(Source(List[String]("o")))

        val backToWebSocketProxy = builder.add(Merge[Message](2))


        staticSource ~> stringToStringMessage ~> backToWebSocketProxy.in(0)
        socksMessageToString.map(IncomingSocksMessage(id, _)) ~> userFlow ~> stringToSocksMessage ~> backToWebSocketProxy.in(1)


        FlowShape(socksMessageToString.in, backToWebSocketProxy.out)
    })


  }

  //TODO: socksjs splits stompframes, it is totally unhandled!
  protected val socksMessageToStringFlow: Flow[Message, String, NotUsed] = {
    Flow[Message].collect[Seq[String]] {
      case TextMessage.Strict("h") =>
        log.debug("Got heart beat Message")
        Seq.empty
      case TextMessage.Strict("[\"\\n\"]") =>
        log.debug("Got heart beat Message")
        Seq.empty
      case TextMessage.Strict(txt) =>
        log.debug("Got text message from Web socket {}", txt)
        try {
          SocksParser.fromIncomingMessage(txt)
        } catch {
          case ex: Exception =>
            log.error("Parsing error ", ex)
            Seq.empty
        }
      case _: BinaryMessage =>
        log.warn("Got Binary message!")
        Seq.empty
    }.mapConcat[String](it => it.to[collection.immutable.Seq])
  }


  protected val stringToSocksMessageFlow: Flow[String, Message, NotUsed] = {
    Flow[String]
      .map {
        str: String => TextMessage.apply(SocksParser.toOutgoingMessage(Seq(str)))
      }
  }

  protected val stringToStringMessageFlow: Flow[String, Message, NotUsed] = {
    Flow[String]
      .map {
        str: String => TextMessage.apply(str)
      }
  }
}
