/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-07 13:52
 *
 */

package org.s4s0l.betelgeuse.akkacommons.http.stomp

import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, _}
import akka.stream.{FlowShape, Materializer, OverflowStrategy}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.pubsub.BetelgeuseAkkaClusteringPubSubExtension.NamedPut
import org.s4s0l.betelgeuse.akkacommons.http.stomp.SocksHandler.{IncomingSocksMessage, SocksSessionId}
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandler.StompUserActor.{StompUserActorDone, _}
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandler._
import org.s4s0l.betelgeuse.akkacommons.http.stomp.StompHandlerApi._
import org.s4s0l.betelgeuse.utils.stomp.{Stomp, StompMessage, StompParser}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
object StompHandler extends StompHandler {

  def defaultActorDestination(webSocketName: String, internalSessionId: String): StompMessageDestination = SessionDestination(webSocketName, "default", internalSessionId)

  def defaultActorDestinationFromSessionId(sessionId: SocksSessionId): StompMessageDestination = defaultActorDestination(sessionId.webSocketName, sessionId.internalSessionId)

  final case class StompHandlerEnvironment(stompServerMessageActor: ActorRef, socksSessionId: SocksSessionId)


  /**
    *
    * @param registerAsNamed                if true actor registers itself in given pub sub as service named /ws-$webSocketName/session/$sessionId/default"
    *                                       and will not register uppon any SUBSCRIBE messages!
    * @param stompServerMessageActorMapping - optional function for mapping messages other than stomp ones
    * @param webSocketName                  name ofweb socket
    * @param pubSubActorRef                 pub sub to register receiving actorto
    */
  final case class StompHandlerSettings(webSocketName: String, pubSubActorRef: Option[ActorRef] = None,
                                        registerAsNamed: Boolean = false,
                                        stompServerMessageActorMapping: SocksSessionId => PartialFunction[Any, StompServerMessage] = _ => PartialFunction.empty
                                       )


  sealed trait BasicHandleDecision {
    val value: (SocksSessionId, StompMessage)

    def isDirect = false

    def isUser = false

    def isHandler = false

    def toUserMessage: StompClientMessage = throw new RuntimeException(value._2.command + " from session " + value._1 + " has no user message representation")
  }

  case class BasicHandleDecisionDirect(value: (SocksSessionId, StompMessage)) extends BasicHandleDecision {
    override def isDirect: Boolean = true
  }


  case class BasicHandleDecisionHandler(value: (SocksSessionId, StompMessage)) extends BasicHandleDecision {
    override def isHandler: Boolean = true
  }

  case class BasicHandleDecisionUser(value: (SocksSessionId, StompMessage)) extends BasicHandleDecision {
    override def isUser: Boolean = true

    override def toUserMessage: StompClientMessage = {
      val source = StompSource(value._1.internalSessionId, value._1.userId, value._1.webSocketName)
      value._2 match {
        case StompMessage("SEND", headers, payload) =>
          StompClientSend(headers, source, headers.getOrElse(Stomp.Headers.Send.ID, s"sg:${UUID.randomUUID().toString}"), payload)
        case StompMessage("ACK", headers, _) =>
          StompClientAck(headers, source, headers(Stomp.Headers.Ack.ID))
        case StompMessage("NACK", headers, _) =>
          StompClientNAck(headers, source, headers(Stomp.Headers.Ack.ID))
        case a =>
          throw new RuntimeException(s"This seems like not a user decision message: $a")
      }
    }

  }

  /**
    * This actor is a proxy for all server originated messages because it handles subscription id mappings.
    * It also handles subscription and unsubs in provided pub sub mediator.
    *
    * Handles messages like:
    *
    * StompUserActorRegister(actorRef: ActorRef) will respond with StompUserActorDone, actor that is connected to websocket
    *      - all messages are going to be delivered to socket
    *
    * BasicHandleDecisionHandler(message: StompServerMessage) will respond with StompUserActorDone, messages that are not going
    * to be forwarded to user flow must be passed from stomp flow here, SUBSCRIBE and UNSUBSCRIBE are a must
    *
    * StompServerMessage -> no response to that - forwards messages to the client filling proper subs id and other headers
    *
    * StompUserActorAknowledgeable(message: StompServerMessage) - will respond with StompUserActorDone after forwarding
    *
    * @param socksSessionId - socks id of a session
    * @param settings       - reference to DistributedPubSubMediator & othersettings
    */
  private class StompUserActor(socksSessionId: SocksSessionId, settings: StompHandlerSettings) extends Actor with ActorLogging {

    private val subscriptionIds = mutable.Map[StompMessageDestination, String]()
    private var webSocket: ActorRef = _
    private val userMessageMapping = settings.stompServerMessageActorMapping(socksSessionId)
    private val pubSubActorRef = settings.pubSubActorRef.filter(_ => !settings.registerAsNamed)

    override def preStart(): Unit = {
      if (settings.registerAsNamed) {
        val nameToRegister = defaultActorDestinationFromSessionId(socksSessionId).toServerSpaceDestination
        log.debug(s"Registered websocket actor ${settings.webSocketName} under $nameToRegister")
        settings.pubSubActorRef.foreach {
          _ ! NamedPut(nameToRegister, self)
        }
      }
    }

    override def receive: Receive = webSocketActorHandle orElse
      coreReceives orElse
      ignoredMessages orElse
      userMessageMapping.andThen(defaultReceive orElse unhandledStuff) orElse
      defaultReceive orElse unhandledStuff


    private def receiveIncomingStompUserMessage: PartialFunction[StompServerMessage, StompMessage] = {
      case StompServerData(destination, payload, id, contentType, iheaders) if isValidDestination(destination) =>
        logReceived(s"-->StompServerData: $id $destination")
        val headers = mutable.Map[String, String]()
        headers ++= iheaders
        headers(Stomp.Headers.Message.SUBSCRIPTION) = subscriptionIds(destination)
        headers(Stomp.Headers.Message.MESSAGE_ID) = id
        headers(Stomp.Headers.Message.DESTINATION) = destination.toUserSpaceDestination
        if (contentType.isDefined)
          headers(Stomp.Headers.CONTENT_TYPE) = contentType.get.toString()
        StompMessage(Stomp.Responses.MESSAGE, headers.toMap, Some(payload))

      case StompServerReceipt(destination, id) if isValidDestination(destination) =>
        logReceived(s"-->StompServerReceipt: $id $destination")
        StompMessage(Stomp.Responses.RECEIPT, Map(Stomp.Headers.Response.RECEIPT_ID -> id), None)
      case StompServerError(destination, error, payload, id, contentType) if isValidDestination(destination) =>
        logReceived(s"-->StompServerError: $id $error $destination")
        val headers = mutable.Map[String, String]()
        if (id.isDefined)
          headers(Stomp.Headers.Response.RECEIPT_ID) = id.get
        if (contentType.isDefined)
          headers(Stomp.Headers.CONTENT_TYPE) = contentType.get.toString()
        headers(Stomp.Headers.Error.MESSAGE) = error
        StompMessage(Stomp.Responses.ERROR, headers.toMap, payload)
    }

    private def receiveIncomingStompMessage: PartialFunction[StompMessage, Boolean] = {
      case s@StompMessage("SUBSCRIBE", headers, _) =>
        logReceived(s"SUBSCRIBE as $s")
        val destination = headers(Stomp.Headers.Subscribe.DESTINATION)
        val subsId = headers(Stomp.Headers.Subscribe.ID)
        val dest = StompMessageDestination(
          socksSessionId.webSocketName,
          socksSessionId.userId,
          socksSessionId.internalSessionId,
          destination)
        dest match {
          case _: UnsupportedDestination => false
          case _ =>
            val serverDestName = dest.toServerSpaceDestination
            pubSubActorRef.foreach {
              _ ! DistributedPubSubMediator.Subscribe(serverDestName, self)
            }
            subscriptionIds(dest) = subsId
            logMe(s"subscribed to $serverDestName as $subsId")
            true
        }
      case s@StompMessage("UNSUBSCRIBE", headers, _) =>
        logReceived(s"UNSUBSCRIBE as $s")
        val subsId = headers(Stomp.Headers.Subscribe.ID)
        val destinationsToUnsubscribe = subscriptionIds.filter(it => it._2 == subsId).keys
        destinationsToUnsubscribe.foreach { dest =>
          val serverDestName = dest.toServerSpaceDestination
          pubSubActorRef.foreach {
            _ ! DistributedPubSubMediator.Unsubscribe(serverDestName, self)
          }
          subscriptionIds -= dest
          logMe(s"unsubscribed from $serverDestName as $subsId")
        }
        destinationsToUnsubscribe.nonEmpty
    }

    private def webSocketActorHandle: Receive = {
      case Terminated(ref) if ref == webSocket =>
        logReceived("Terminated")
        context.stop(self)
      case StompUserActorRegister(ref) =>
        logReceived("StompUserActorRegister")
        this.webSocket = ref
        context.watch(webSocket)
        flowActionDone()
    }

    private def defaultReceive: Receive = {
      case a: StompServerMessage if receiveIncomingStompUserMessage.isDefinedAt(a) =>
        val msg = receiveIncomingStompUserMessage.apply(a)
        webSocket ! msg
    }


    private def coreReceives: Receive = {
      case BasicHandleDecisionHandler((session, stompMessage)) if session == socksSessionId && receiveIncomingStompMessage.isDefinedAt(stompMessage) =>
        val success = receiveIncomingStompMessage.apply(stompMessage)
        flowActionDone(success)
      case StompUserActorAknowledgeable(a) if receiveIncomingStompUserMessage.isDefinedAt(a) =>
        val msg = receiveIncomingStompUserMessage.apply(a)
        webSocket ! msg
        flowActionDone()

    }

    private def ignoredMessages: Receive = {
      case SubscribeAck(_) =>
        logReceived("SubscribeAck")

    }

    private def unhandledStuff: Receive = {
      case h@BasicHandleDecisionHandler((session, _)) if session != socksSessionId =>
        log.error(s"StompUserActor got message from wrong flow? my session=$socksSessionId got $h!")
      case h@BasicHandleDecisionHandler((_, stompMessage)) if !receiveIncomingStompMessage.isDefinedAt(stompMessage) =>
        log.error(s"StompUserActor got decission with unsupported command? my session=$socksSessionId got $h!")
      case x: StompServerMessage if !isValidDestination(x.destination) =>
        log.warning(s"StompUserActor got serverMessage with wrong destination? my session=$socksSessionId got $x! but we have only ${this.subscriptionIds}")
      case a: StompServerMessage if !receiveIncomingStompUserMessage.isDefinedAt(a) =>
        log.error(s"StompUserActor got serverMessage with unsupported command? my session=$socksSessionId got $a!")
      case a =>
        log.error(s"StompUserActor got a funny message session=$socksSessionId got $a!")
    }

    private def flowActionDone(success: Boolean = true): Unit = {
      if (success) {
        sender() ! StompUserActorDoneOk()
      }
      else {
        logMe("Failing operation")
        sender() ! StompUserActorDoneFailed()
      }
    }


    private def isValidDestination(dest: StompMessageDestination): Boolean = {
      this.subscriptionIds.contains(dest)
    }

    private def logReceived(obk: => String): Unit = {
      if (log.isDebugEnabled) {
        log.debug(s"(${socksSessionId.internalSessionId},${socksSessionId.userId}) Received $obk")
      }
    }

    private def logMe(obk: => String): Unit = {
      if (log.isDebugEnabled) {
        log.debug(s"(${socksSessionId.internalSessionId},${socksSessionId.userId}) $obk")
      }
    }

  }

  object StompUserActor {
    def props(socksSessionId: SocksSessionId, settings: StompHandlerSettings): Props = {
      Props(new StompUserActor(socksSessionId, settings))
    }

    sealed trait StompUserActorDone {
      val success: Boolean
    }

    case class StompUserActorDoneOk() extends StompUserActorDone {
      override val success = true
    }

    case class StompUserActorDoneFailed() extends StompUserActorDone {
      override val success = false
    }

    case class StompUserActorAknowledgeable(message: StompServerMessage)

    case class StompUserActorRegister(actorRef: ActorRef)

  }

}


trait StompHandler {
  private val log = LoggerFactory.getLogger(getClass)

  def socksStompFlow(settings: StompHandlerSettings, userFlowFactory: StompHandlerEnvironment => Flow[StompClientMessage, Option[StompServerMessage], Any])
                    (implicit system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext): Route = {
    SocksHandler.handleSocks(settings.webSocketName, { sessionId =>
      stompFlow(sessionId, settings, userFlowFactory)
    })
  }

  protected def stompFlow(socksSessionId: SocksSessionId, settings: StompHandlerSettings, flowFactory: StompHandlerEnvironment => Flow[StompClientMessage, Option[StompServerMessage], Any])
                         (implicit system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext): Flow[IncomingSocksMessage, String, Any] = {

    val stompUserActorRef = system.actorOf(StompUserActor.props(socksSessionId, settings))

    val stompMessageReceiverActor = Source.actorRef[StompMessage](bufferSize = 30, OverflowStrategy.dropHead)

    val env = StompHandlerEnvironment(stompUserActorRef, socksSessionId)
    val xxx = flowFactory(env)

    Flow.fromGraph[IncomingSocksMessage, String, Any](GraphDSL.create(xxx, stompMessageReceiverActor)((_, _)) { implicit builder =>
      (userFlow, stompMessageReceiver) =>
        import GraphDSL.Implicits._

        val fromWebSocket: FlowShape[IncomingSocksMessage, (SocksSessionId, StompMessage)] = builder.add(messageToStomp)
        val backToWebSocket: FlowShape[StompMessage, String] = builder.add(stompToMessage)
        val basicProtocol: FlowShape[(SocksSessionId, StompMessage), BasicHandleDecision] = builder.add(basicProtocolHandlerFlow)
        val backToWebSocketProxy = builder.add(Merge[StompMessage](3))
        val userResponseToActor: FlowShape[Option[StompServerMessage], Either[BasicHandleDecision, StompServerMessage]] = builder.add(userResponseToActorFlow)
        val userActorProxy = builder.add(Merge[Either[BasicHandleDecision, StompServerMessage]](2))
        val decisionBcast = builder.add(Broadcast[BasicHandleDecision](3))
        val userActor = builder.add(userActorFlow(stompUserActorRef))
        val actorWorkDone = builder.add(actorWorkDoneFlow)

        backToWebSocketProxy ~> backToWebSocket
        stompMessageReceiver ~> backToWebSocketProxy.in(0)
        fromWebSocket ~> basicProtocol ~> decisionBcast

        decisionBcast.out(0).filter(_.isDirect).map(_.value._2) ~> backToWebSocketProxy.in(1)
        decisionBcast.out(1).filter(_.isUser).map(_.toUserMessage) ~> userFlow ~> userResponseToActor ~> userActorProxy.in(0)
        decisionBcast.out(2).filter(_.isHandler).map(Left(_)) ~> userActorProxy.in(1)

        userActorProxy ~> userActor ~> actorWorkDone ~> backToWebSocketProxy.in(2)

        val materializedOutlet: CombinerBase[StompUserActorDone] = builder.materializedValue.map[Future[StompUserActorDone]]((x: (Any, ActorRef)) => {
          val actorToRegister = StompUserActorRegister(x._2)
          import akka.pattern.ask

          import scala.concurrent.duration._
          implicit val timeout: Timeout = Timeout(1 seconds)
          (stompUserActorRef ? actorToRegister).map(_.asInstanceOf[StompUserActorDone])
        }).mapAsync(1)(x => x).map(failIfNotOk)
        val ignoreDump = builder.add(Sink.ignore)
        materializedOutlet ~> ignoreDump

        FlowShape(fromWebSocket.in, backToWebSocket.out)
    })
  }


  val failIfNotOk: StompUserActorDone => StompUserActorDone = {
    done =>
      if (done.success) {
        done
      } else {
        throw new RuntimeException("Actor action failed")
      }
  }


  protected def userActorFlow(stompUserActor: ActorRef)
                             (implicit system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext)
  : Flow[Either[BasicHandleDecision, StompServerMessage], StompUserActorDone, NotUsed] = {
    Flow[Either[BasicHandleDecision, StompServerMessage]]
      .map(it => if (it.isRight) StompUserActorAknowledgeable(it.right.get) else it.left.get)
      .mapAsync(1) { x =>
        import akka.pattern.ask

        import scala.concurrent.duration._
        implicit val timeout: Timeout = Timeout(1 seconds)
        (stompUserActor ? x).map(_.asInstanceOf[StompUserActorDone])
      }
  }


  protected val actorWorkDoneFlow: Flow[StompUserActorDone, StompMessage, NotUsed] = {
    Flow[StompUserActorDone].map(failIfNotOk).filter(_ => false).map(_.asInstanceOf[StompMessage])
  }

  protected val messageToStomp: Flow[IncomingSocksMessage, (SocksSessionId, StompMessage), NotUsed] = {
    Flow[IncomingSocksMessage].map {
      string: IncomingSocksMessage => (string.session, StompParser.parse(string.payload))
    }
  }

  protected val stompToMessage: Flow[StompMessage, String, NotUsed] = {
    Flow[StompMessage].map {
      string: StompMessage => string.toPayload
    }
  }

  protected val userResponseToActorFlow: Flow[Option[StompServerMessage], Either[BasicHandleDecision, StompServerMessage], NotUsed] = {
    Flow[Option[StompServerMessage]].filter(_.isDefined).map(it => Right(it.get))
  }

  protected val basicProtocolHandlerFlow: Flow[(SocksSessionId, StompMessage), BasicHandleDecision, NotUsed] = {
    Flow[(SocksSessionId, StompMessage)].collect {
      case (a, StompMessage("CONNECT", _, _)) =>
        BasicHandleDecisionDirect((a, StompMessage(Stomp.Responses.CONNECTED, Map(
          Stomp.Headers.Connected.VERSION -> "1.1",
          Stomp.Headers.Connected.HEART_BEAT -> "0,10000",
        ), None)))
      case msg@(_, StompMessage("SEND", _, _)) => BasicHandleDecisionUser(msg)
      case msg@(_, StompMessage("SUBSCRIBE", _, _)) => BasicHandleDecisionHandler(msg)
      case msg@(_, StompMessage("UNSUBSCRIBE", _, _)) => BasicHandleDecisionHandler(msg)
      case msg@(_, StompMessage("ACK", _, _)) => BasicHandleDecisionUser(msg)
      case msg@(_, StompMessage("NACK", _, _)) => BasicHandleDecisionUser(msg)
      case x =>
        log.error(s"Unknown message encountered!!! $x")
        throw new RuntimeException(s"Unknown message encountered!!! $x")
    }
  }


}
