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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.Protocol.{PublishMessage, _}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.MandatorySubsActor.{ActorDead, MessageForwarderContext, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.SatelliteStateListener
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.SatelliteStateListener.{StateChanged, StateChangedNotOk, StateChangedOk, StateChangedResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.utils.QA._
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, postfixOps}

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
class MandatorySubsActor[K, T](settings: Settings[K, T]) extends Actor with ActorLogging {

  private val subscriptions: mutable.Map[String, ActorRef] = mutable.Map()


  override def receive: Actor.Receive = {
    case Subscribe(key, ref) =>
      subscriptions.put(key, ref)
      context.watchWith(ref, ActorDead(key, ref))
      sender() ! SubscribeOk(key)

    case ActorDead(key, _) =>
      subscriptions.remove(key)

    case pm: PublishMessage[K, T] =>
      val originalSender = sender()
      import context.dispatcher
      val notificationFuture = listOfFuturesToFutureOfList(
        subscriptions
          .map { it =>
            settings.messageForwarder.forward(pm, MessageForwarderContext(it._1, it._2, settings))(context.dispatcher, self)
              .map(x => (it._1, x))
              .recover { case x: Throwable => (it._1, NotOk(pm.messageId, x)) }
          }.toSeq)
        .map { seq =>
          val failedOnes = seq.filter(_._2.isNotOk).map(it => (it._1, it._2.asInstanceOf[NotOk[K]]))
          failedOnes.foreach { it =>
            log.error(it._2.ex, s"Unable to get state from subscriber ${it._1}")
          }
          val successOnes = seq.filter(_._2.isOk).map(_._1)
          if (failedOnes.isEmpty && settings.mandatorySubscriptionKeys.forall(successOnes.contains(_))) {
            Ok(pm.messageId)
          } else {
            NotOk(pm.messageId, new Exception(s"Unable to get results from ${failedOnes.map(_._1).mkString(",")}"))
          }
        }
        .recover { case it: Throwable => NotOk(pm.messageId, it) }

      import org.s4s0l.betelgeuse.utils.AllUtils._
      notificationFuture.pipeToWithTimeout(originalSender, pm.maxDuration,
        NotOk(pm.messageId, new Exception(s"Timeout publishing ${pm.messageId}!")), context.system.scheduler)


  }


}


object MandatorySubsActor {

  /**
    * default forwarder. Forwards payload only treats any response as ack.
    */
  def defaultMessageForwarder[K, T]: MessageForwarder[K, T] = new MessageForwarder[K, T] {
    override def forward(pm: PublishMessage[K, T], context: MessageForwarderContext[K, T])
                        (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[PublishMessageResult[K]] = {
      context.actorRef.ask(pm.payload)(pm.maxDuration)
        .map(_ => Ok(pm.messageId))
        .recover { case x: Throwable => NotOk(pm.messageId, x) }
    }
  }


  /**
    * creates props for actor
    */
  def start[K, T](settings: Settings[K, T], propsMapper: Props => Props = identity)
                 (implicit actorSystem: ActorSystem): Protocol[K, T] = {
    val ref = actorSystem.actorOf(Props(new MandatorySubsActor(settings)), settings.name)
    Protocol(ref, settings)
  }

  /**
    * Message forwarder, from received message, subscription key, subscribed actor to
    * future containing status of forward. It should prepare message an interpret response
    * (ack vs nack)
    *
    * todo: replace with regular send get rid of ask pattern?
    *
    */
  trait MessageForwarder[K, T] {
    def forward(pm: PublishMessage[K, T], context: MessageForwarderContext[K, T])
               (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[PublishMessageResult[K]]
  }

  case class MessageForwarderContext[K, T](subscriptionKey: String, actorRef: ActorRef, settings: Settings[K, T])

  /**
    *
    * @param mandatorySubscriptionKeys - list of mandatory subscriptions keys that are needed to confirm delivery
    * @param messageForwarder          tool used for sending messages to subscribers
    */
  final case class Settings[K, T](name: String,
                                  mandatorySubscriptionKeys: Seq[String],
                                  messageForwarder: MessageForwarder[K, T])

  /**
    * An protocol for [[MandatorySubsActor]]
    */
  final class Protocol[K, T] private(actorRef: => ActorRef, settings: Settings[K, T]) {

    /**
      * emits event, sender have to expect [[PublishMessageResult[K]]] on successful delivery to all mandatory subscribers
      *
      */
    def send(msg: PublishMessage[K, T])
            (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Unit =
      actorRef ! msg

    /**
      * ask pattern version of [[Protocol.send]]
      *
      */
    def sendAsk(msg: PublishMessage[K, T])
               (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[PublishMessageResult[K]] =
      actorRef.ask(msg)(msg.maxDuration).mapTo[PublishMessageResult[K]]

    /**
      * subscribes actor on key
      *
      * @return ack that subscription was done
      */
    def subscribe(subs: Subscribe)
                 (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[SubscribeResult] =
      actorRef.ask(subs)(3 seconds).mapTo[SubscribeResult]


  }

  private case class ActorDead(key: String, actorRef: ActorRef)


  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[K, T](actorRef: => ActorRef, settings: Settings[K, T]): Protocol[K, T] = new Protocol(actorRef, settings)

    sealed trait IncomingMessage

    sealed trait OutgoingMessage

    sealed trait SubscribeResult extends NullResult[String]

    trait PublishMessageResult[K] extends NullResult[K]

    case class PublishMessage[K, T](messageId: K, payload: T, maxDuration: FiniteDuration) extends IncomingMessage with Question[K]

    case class Ok[K](correlationId: K) extends PublishMessageResult[K] with OkNullResult[K]

    case class NotOk[K](correlationId: K, ex: Throwable) extends PublishMessageResult[K] with NotOkNullResult[K]

    case class Subscribe(messageId: String, ref: ActorRef) extends IncomingMessage with Question[String]

    case class SubscribeOk(correlationId: String) extends SubscribeResult with OkNullResult[String]

    implicit def asSatelliteStateListener[T](protocol: Protocol[VersionedId, T]): SatelliteStateListener[T] = new SatelliteStateListener[T] {
      def configurationChanged(msg: StateChanged[T])
                              (implicit executionContext: ExecutionContext, sender: ActorRef = ActorRef.noSender)
      : Future[StateChangedResult] = {
        protocol.sendAsk(PublishMessage(msg.messageId, msg.value, msg.expDuration)).map {
          case Ok(ver) => StateChangedOk(ver)
          case NotOk(ver, ex) => StateChangedNotOk(ver, ex)
        }
      }
    }
  }

}