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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor.Protocol.{PublicationResult, PublicationResultNotOk, PublicationResultOk, PublishMessage}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor._
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.SatelliteStateListener
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.SatelliteStateActor.SatelliteStateListener.{StateChanged, StateChangedNotOk, StateChangedOk, StateChangedResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.utils.QA._
import org.s4s0l.betelgeuse.akkacommons.utils.{AsyncInitActor, QA}
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, postfixOps}

/**
  *
  * An actor that manages confirmed broadcasts
  * to list of listeners, confirmation is send back only if all listeners respond with
  * successful status.
  *
  * Listeners are provided as a future, all messages send to this actor prior to future
  * completion are stashed.
  *
  * todo: rewrite so it wil not use asks (Futures) in listeners
  *
  * @author Marcin Wielgus
  */
class DelayedSubsActor[K, V](settings: Settings[K, V]) extends Actor with ActorLogging
  with AsyncInitActor {

  import context.dispatcher

  var listeners: Seq[Listener[K, V]] = _

  override def initialReceive: Receive = {
    case ListenersReady(listenersReady) =>
      listeners = listenersReady.map(_.asInstanceOf[Listener[K, V]])
      initiationComplete()
    case ListenersFailed(ex) =>
      log.error(ex, "Delayed subscription failed, quitting actor")
      throw new Exception("Delayed subscription failed, quitting actor", ex)
  }


  override def preStart(): Unit = {
    import context.dispatcher
    settings.listeners
      .map(ListenersReady(_))
      .recover { case ex: Throwable => ListenersFailed(ex) }
      .pipeTo(self)
  }

  override def receive: Actor.Receive = {
    case pm@PublishMessage(id, _) =>
      val originalSender = sender()
      val notificationFuture = listOfFuturesToFutureOfList(
        listeners.map(listener =>
          listener.onMessage(pm.asInstanceOf[PublishMessage[K, V]])(context.dispatcher, self)
            .map((_, listener))
            .recover { case ex: Throwable => (PublicationResultNotOk[K](id.asInstanceOf[K], ex), listener) }
        )
      )
        .map(listeners => listeners.filter(_._1.isNotOk).map(_._2.name))
        .map(failedListeners =>
          if (failedListeners.isEmpty) PublicationResultOk(id)
          else PublicationResultNotOk(id, new Exception(s"There were failed listeners ${failedListeners.mkString(",")}")))

      import org.s4s0l.betelgeuse.utils.AllUtils._
      notificationFuture.pipeToWithTimeout(originalSender, settings.ackTimeout.duration,
        PublicationResultNotOk(id, new Exception(s"Timeout publishing $id!")), context.system.scheduler)

  }


}


object DelayedSubsActor {
  /**
    * creates props for actor
    */
  def start[K, V](settings: Settings[K, V], propsMapper: Props => Props = identity)
                 (implicit actorSystem: ActorRefFactory): Protocol[K, V] = {
    val ref = actorSystem.actorOf(Props(new DelayedSubsActor(settings)))
    Protocol(ref, settings)
  }

  trait Listener[K, V] {

    def onMessage(publishMessage: PublishMessage[K, V])
                 (implicit executionContext: ExecutionContext, sender: ActorRef):
    Future[PublicationResult[K]]

    def name: String
  }

  final case class Settings[K, V](name: String,
                                  listeners: Future[Seq[Listener[K, V]]],
                                  ackTimeout: Timeout = 5 seconds)

  /**
    * An protocol for [[DelayedSubsActor]]
    */
  final class Protocol[K, V] private(actorRef: => ActorRef, settings: Settings[K, V]) {

    /**
      * emits event, sender have to expect [[PublicationResult[K]] on successful delivery to all mandatory subscribers
      *
      */
    def send(msg: PublishMessage[K, V])
            (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Unit =
      actorRef ! msg

    /**
      * ask pattern version of [[Protocol.send]]
      *
      */
    def sendAsk(msg: PublishMessage[K, V])
               (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[PublicationResult[K]] =
      actorRef.ask(msg)(settings.ackTimeout, sender).mapTo[PublicationResult[K]]

  }

  private case class ListenersReady[K, V](listeners: Seq[Listener[K, V]])

  private case class ListenersFailed(ex: Throwable)


  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[K, V](actorRef: => ActorRef, settings: Settings[K, V]): Protocol[K, V] = new Protocol(actorRef, settings)

    case class PublishMessage[K, V](messageId: K, payload: V) extends QA.Question[K]

    sealed trait PublicationResult[K] extends QA.NullResult[K]

    case class PublicationResultOk[K](correlationId: K) extends PublicationResult[K] with OkNullResult[K]

    case class PublicationResultNotOk[K](correlationId: K, ex: Throwable) extends PublicationResult[K] with NotOkNullResult[K]

    implicit def asSatelliteStateListener[T](protocol: Protocol[VersionedId, T])
    : SatelliteStateListener[T] = new SatelliteStateListener[T] {
      override def configurationChanged(msg: StateChanged[T])
                                       (implicit executionContext: ExecutionContext, sender: ActorRef = ActorRef.noSender)
      : Future[StateChangedResult] = {
        protocol.sendAsk(PublishMessage[VersionedId, T](msg.messageId, msg.value)).map {
          case PublicationResultOk(_) => StateChangedOk(msg.messageId)
          case PublicationResultNotOk(_, ex) => StateChangedNotOk(msg.messageId, ex)
        }.recover { case ex: Throwable => StateChangedNotOk(msg.messageId, ex) }
      }
    }
  }

}    