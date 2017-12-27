/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.nearcache

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.pattern.pipe
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol.GetCacheValueOk
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheValueActor.Protocol.GetCacheValue
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheValueActor.{Settings, ValueEnriched, ValueEnrichedFailure}
import org.s4s0l.betelgeuse.akkacommons.utils.QA.{Question, Uuid}
import org.s4s0l.betelgeuse.akkacommons.utils.{AsyncInitActor, TimeoutActor}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  *
  * Uses AsyncInitSo that initialization will be done in this actor dispatcher context.
  *
  * @author Marcin Wielgus
  *
  *
  */
private[nearcache] class CacheValueActor[K, R, V](settings: Settings[K, R, V]) extends Actor
  with AsyncInitActor
  with TimeoutActor
  with ActorLogging {

  override val timeoutTime: FiniteDuration = settings.timeoutTime
  var value: R = _

  override def preStart(): Unit = {
    //TODO: configurable dispatcher so rich value creation would not block us if is blocking?
    try {

      import context.dispatcher
      settings.valueEnricher(settings.valueMessage)
        .map(it => ValueEnriched(it))
        .recover { case it: Throwable => ValueEnrichedFailure(it) }
        .pipeTo(self)
    } catch {
      case ex: Throwable =>
        self ! ValueEnrichedFailure(ex)
    }
  }

  override def initialReceive: PartialFunction[Any, Unit] = {
    case ValueEnriched(r) =>
      value = r.asInstanceOf[R]
      initiationComplete()
      context.parent ! CacheAccessActor.Protocol.CacheValueOk(settings.key)
    case ValueEnrichedFailure(ex) =>
      //we fail this actor, its supervisor problem
      context.parent ! CacheAccessActor.Protocol.CacheValueDied(settings.key, ex)
      context.stop(self)
  }

  override def receive: Actor.Receive = {
    case req: GetCacheValue =>
      sender() ! GetCacheValueOk(req.messageId, value)
  }


}


private[nearcache] object CacheValueActor {
  /**
    * creates props for actor
    */
  def start[K, R, V](name: String, settings: Settings[K, R, V], propsMapper: Props => Props = identity)
                    (implicit actorSystem: ActorRefFactory): Protocol = {
    val ref = actorSystem.actorOf(Props(new CacheValueActor(settings)), name)
    Protocol(ref)
  }

  final case class Settings[K, R, V](key: K, valueMessage: V, valueEnricher: V => Future[R], timeoutTime: FiniteDuration = 10 minutes)

  final class Protocol private(actorRef: => ActorRef) {

    def apply(msg: GetCacheValue)
             (implicit executionContext: ExecutionContext, sender: ActorRef): Unit = actorRef ! msg

    def watchWith(context: ActorContext, msg: Any): Unit = {
      context.watchWith(actorRef, msg)
    }

  }

  private case class ValueEnriched[R](enriched: R)

  private case class ValueEnrichedFailure(ex: Throwable)

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply(actorRef: => ActorRef): Protocol = new Protocol(actorRef)

    case class GetCacheValue(messageId: Uuid) extends Question[Uuid]

  }

}    