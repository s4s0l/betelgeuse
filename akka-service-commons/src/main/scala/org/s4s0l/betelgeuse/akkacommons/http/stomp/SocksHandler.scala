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

import akka.NotUsed
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, _}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.http.stomp.SocksHandler.{IncomingSocksMessage, SocksSessionId}
import org.s4s0l.betelgeuse.utils.UuidUtils
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
            val internalSessionId = UuidUtils.timeBasedUuid().toString
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
