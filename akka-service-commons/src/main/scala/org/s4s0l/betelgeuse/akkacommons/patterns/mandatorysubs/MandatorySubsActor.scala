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

package org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs

import akka.actor.Status.{Failure, Status, Success}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.Protocol.{PublishMessage, _}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.{ActorDead, InternalPublicationResult, MessageForwarderContext, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.SatelliteStateListener
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, postfixOps}
import scala.util.Try

/**
  * Actor that broadcasts any message to actors that subscribed to it.
  * It has list of mandatory predefined subscription keys.
  * When all subscribed actors, that subscribed with a key from that list confirm reception,
  * a response is send back by this actor. If some subscriptions from mandatory
  * list are absent no confirmation is send back. There can be at most one
  * actor subscribed under given key.
  *
  * @author Marcin Wielgus
  */
class MandatorySubsActor[T](settings: Settings[T]) extends Actor with ActorLogging {

  private var subscriptions: Map[String, ActorRef] = Map()


  override def receive: Actor.Receive = {
    case Subscribe(key, ref) =>
      subscriptions = subscriptions + (key -> ref)
      context.watchWith(ref, ActorDead(key, ref))
      sender() ! SubscribeAck(key, ref)

    case ActorDead(key, _) =>
      subscriptions = subscriptions - key

    case pm: PublishMessage[T] =>

      val originalSender = sender()
      val receptionTime = System.currentTimeMillis()
      import context.dispatcher
      listOfFuturesToFutureOfList(
        subscriptions
          .map { it =>
            settings.messageForwarder.forward(pm, MessageForwarderContext(it._1, it._2, settings))(context.dispatcher)
              .map(x => (it._1, it._2, x))
              .recover { case x: Throwable => (it._1, it._2, Failure(x)) }
          }.toSeq)
        .map { seq => InternalPublicationResult(pm, originalSender, receptionTime, scala.util.Success(seq.map(elem => (elem._1, elem._3)))) }
        .recover { case it => InternalPublicationResult(pm, originalSender, receptionTime, scala.util.Failure(it)) }
        .pipeTo(self)

    case InternalPublicationResult(pm, originalSender, receptionTime, scala.util.Failure(ex)) =>
      log.error(ex, "Unable to publish message of id {} from {} received at {}", pm.id, originalSender, receptionTime)

    case InternalPublicationResult(pm, originalSender, receptionTime, scala.util.Success(_))
      if receptionTime + settings.ackTimeout.duration.toMillis < System.currentTimeMillis() =>
      log.error("Published message result received after timeout had id {} from {} received at {}", pm.id, originalSender, receptionTime)

    case InternalPublicationResult(pm, originalSender, _, scala.util.Success(results))
      if results.filter(_._2.isInstanceOf[Success]).map(_._1).filter(it => settings.mandatorySubscriptionKeys.contains(it)) != settings.mandatorySubscriptionKeys =>
      val failedReceivers = results.filter(!_._2.isInstanceOf[Success]).map(_._1)
      val allReceived = results.map(_._1)
      val missingReceivers = settings.mandatorySubscriptionKeys.filter(!allReceived.contains(_))

      log.error("Published message result does not contain all mandatory receivers, message had id {} from {}, failed receivers {}, missing receivers {}",
        pm.id, originalSender, failedReceivers.mkString(","), missingReceivers.mkString(","))

    case InternalPublicationResult(pm, originalSender, _, scala.util.Success(_)) =>
      originalSender ! Ack(pm.id)


  }


}


object MandatorySubsActor {

  /**
    * default forwarder. Forwards payload only treats any response as ack.
    */
  def defaultMessageForwarder[T]: MessageForwarder[T] = new MessageForwarder[T] {
    override def forward(publishMessage: PublishMessage[T], context: MessageForwarderContext[T])(implicit ec: ExecutionContext): Future[Status] = {
      context.actorRef.ask(publishMessage.payload)(context.settings.ackTimeout)
        .map(_ => Success(None))
        .recover { case x: Throwable => Failure(x) }
    }
  }


  /**
    * creates props for actor
    */
  def start[T](settings: Settings[T], propsMapper: Props => Props = identity)
              (implicit actorSystem: ActorSystem): Protocol[T] = {
    val ref = actorSystem.actorOf(Props(new MandatorySubsActor(settings)), settings.name)
    Protocol(ref, settings)
  }

  /**
    * Message forwarder, from received message, subscription key, sunscribed actor to
    * future containing status of forward. It should prepare message an interpret response
    * (ack vs nack)
    *
    * todo: replace with regular send get rid of ask pattern?
    *
    */
  trait MessageForwarder[T] {
    def forward(pm: PublishMessage[T], context: MessageForwarderContext[T])(implicit ec: ExecutionContext): Future[Status]
  }

  case class MessageForwarderContext[T](key: String, actorRef: ActorRef, settings: Settings[T])

  /**
    *
    * @param mandatorySubscriptionKeys - list of mandatory subscriptions keys that are needed to confirm delivery
    * @param messageForwarder          tool used for sending messages to subscribers
    * @param ackTimeout                how long will we wait for confirmation
    */
  final case class Settings[T](name: String,
                               mandatorySubscriptionKeys: Seq[String],
                               messageForwarder: MessageForwarder[T],
                               ackTimeout: Timeout = 5 seconds)

  /**
    * An protocol for [[MandatorySubsActor]]
    */
  final class Protocol[T] private(actorRef: => ActorRef, settings: Settings[T]) {

    /**
      * emits event, sender have to expect [[Ack]] on successfull delivery to all mandatory subscribers
      *
      */
    def send(msg: PublishMessage[T])(implicit sender: ActorRef = Actor.noSender)
    : Unit =
      actorRef ! msg

    /**
      * ask pattern version of [[Protocol.send]]
      *
      */
    def sendAsk(msg: PublishMessage[T])
               (implicit sender: ActorRef = Actor.noSender)
    : Future[Ack] =
      actorRef.ask(msg)(settings.ackTimeout).mapTo[Ack]

    /**
      * subscribes actor on key
      *
      * @return ack that subscription was done
      */
    def subscribe(subs: Subscribe)(implicit sender: ActorRef = Actor.noSender)
    : Future[SubscribeAck] =
      actorRef.ask(subs)(3 seconds).mapTo[SubscribeAck]


  }

  private case class ActorDead(key: String, actorRef: ActorRef)

  private case class PublicationResult()

  private case class InternalPublicationResult[T](publishMessage: PublishMessage[T],
                                                  originalSender: ActorRef,
                                                  receptionTime: Long,
                                                  results: Try[Seq[(String, Status)]])

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: => ActorRef, settings: Settings[T]): Protocol[T] = new Protocol(actorRef, settings)

    sealed trait IncomingMessage

    sealed trait OutgoingMessage

    case class PublishMessage[T](id: Any, payload: T) extends IncomingMessage

    case class Subscribe(key: String, ref: ActorRef) extends IncomingMessage

    case class Ack(id: Any) extends OutgoingMessage

    case class SubscribeAck(key: String, ref: ActorRef) extends OutgoingMessage


    implicit def asSatelliteStateListener[T](protocol: Protocol[T]): SatelliteStateListener[T] = new SatelliteStateListener[T] {
      override def configurationChanged(versionedId: VersionedId, value: T)(implicit executionContext: ExecutionContext, sender: ActorRef = ActorRef.noSender): Future[Status] = {
        protocol.sendAsk(PublishMessage[T](versionedId, value)).map(Success(_))
      }
    }
  }

}