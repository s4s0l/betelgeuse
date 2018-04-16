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

package org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.pattern.{ask, pipe}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor.Protocol.{Publish, PublishNotOk, PublishOk, PublishResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.mandatorysubs.DelayedSubsActor._
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteStateListener
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteStateListener.{StateChanged, StateChangedNotOk, StateChangedOk, StateChangedResult}
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
  * @author Marcin Wielgus
  */
class DelayedSubsActor[K, V](settings: Settings[K, V]) extends Actor with ActorLogging
  with AsyncInitActor {

  import context.dispatcher

  var listeners: Seq[Listener[K, V]] = _

  override def initialReceive: Receive = {
    case InternalListenersReady(listenersReady) =>
      listeners = listenersReady.map(_.asInstanceOf[Listener[K, V]])
      initiationComplete()
    case InternalListenersFailed(ex) =>
      log.error(ex, "Delayed subscription failed, quitting actor")
      throw new Exception("Delayed subscription failed, quitting actor", ex)
  }


  override def preStart(): Unit = {
    import context.dispatcher
    settings.listeners
      .map(InternalListenersReady(_))
      .recover { case ex: Throwable => InternalListenersFailed(ex) }
      .pipeTo(self)
  }

  override def receive: Actor.Receive = {
    case pm@Publish(id, _, ackTimeout) =>
      val originalSender = sender()
      val notificationFuture = listOfFuturesToFutureOfList(
        listeners.map(listener =>
          listener.publish(pm.asInstanceOf[Publish[K, V]])(context.dispatcher, self)
            .map((_, listener))
            .recover { case ex: Throwable => (PublishNotOk[K](id.asInstanceOf[K], ex), listener) }
        )
      )
        .map(listeners => listeners.filter(_._1.isNotOk).map(_._2.name))
        .map(failedListeners =>
          if (failedListeners.isEmpty) PublishOk(id)
          else PublishNotOk(id, new Exception(s"There were failed listeners ${failedListeners.mkString(",")}")))

      import org.s4s0l.betelgeuse.utils.AllUtils._
      notificationFuture.pipeToWithTimeout(originalSender, ackTimeout,
        PublishNotOk(id, new Exception(s"Timeout publishing $id!")), context.system.scheduler)

  }


}


object DelayedSubsActor {
  /**
    * creates props for actor
    */
  def start[K, V](settings: Settings[K, V], propsMapper: Props => Props = identity)
                 (implicit actorSystem: ActorRefFactory): Protocol[K, V] = {
    val ref = actorSystem.actorOf(Props(new DelayedSubsActor(settings)))
    new Protocol[K, V](ref, settings)
  }

  def asSatelliteStateListener[T](protocol: Protocol[VersionedId, T])
  : SatelliteStateListener[T] = new SatelliteStateListener[T] {
    override def configurationChanged(msg: StateChanged[T])
                                     (implicit executionContext: ExecutionContext, sender: ActorRef = ActorRef.noSender)
    : Future[StateChangedResult] =
      protocol.publishAsk(Publish[VersionedId, T](msg.messageId, msg.value, msg.expDuration)).map {
        case PublishOk(_) => StateChangedOk(msg.messageId)
        case PublishNotOk(_, ex) => StateChangedNotOk(msg.messageId, new Exception(ex))
      }.recover { case ex: Throwable => StateChangedNotOk(msg.messageId, ex) }

  }

  def fromSatelliteStateListener[V](listenerName: String, protocol: SatelliteStateListener[V])
  : Listener[VersionedId, V] = {
    new Listener[VersionedId, V] {

      def publish(published: Publish[VersionedId, V])
                 (implicit executionContext: ExecutionContext, sender: ActorRef)
      : Future[PublishResult[VersionedId]] =
        protocol.configurationChanged(StateChanged(published.messageId, published.payload, published.ackTimeout)).map {
          case StateChangedOk(v) => PublishOk(v)
          case StateChangedNotOk(v, ex) => PublishNotOk(v, new Exception(ex))
        }.recover { case ex: Throwable => PublishNotOk(published.messageId, ex) }


      def name: String = listenerName
    }
  }

  trait Listener[K, V] {

    def publish(publishMessage: Publish[K, V])
               (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[PublishResult[K]]

    def name: String
  }

  final case class Settings[K, V](name: String,
                                  listeners: Future[Seq[Listener[K, V]]])

  /**
    * An protocol for [[DelayedSubsActor]]
    */
  final class Protocol[K, V] private[DelayedSubsActor](actorRef: => ActorRef, settings: Settings[K, V]) {

    /**
      * emits event, sender have to expect [[PublishResult[K]] on successful delivery to all mandatory subscribers
      *
      */
    def publishMsg(msg: Publish[K, V])
                  (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Unit =
      actorRef ! msg

    /**
      * ask pattern version of [[Protocol.publishMsg]]
      *
      */
    def publishAsk(msg: Publish[K, V])
                  (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[PublishResult[K]] =
      actorRef.ask(msg)(msg.ackTimeout, sender)
        .mapTo[PublishResult[K]]
        .recover { case ex: Throwable => PublishNotOk(msg.messageId, new Exception(ex)) }


  }

  private case class InternalListenersReady[K, V](listeners: Seq[Listener[K, V]])

  private case class InternalListenersFailed(ex: Throwable)

  object Protocol {


    sealed trait PublishResult[K] extends QA.NullResult[K]

    case class Publish[K, V](messageId: K, payload: V, ackTimeout: FiniteDuration) extends QA.Question[K]

    case class PublishOk[K](correlationId: K) extends PublishResult[K] with OkNullResult[K]

    case class PublishNotOk[K](correlationId: K, ex: Throwable) extends PublishResult[K] with NotOkNullResult[K]

  }

}    