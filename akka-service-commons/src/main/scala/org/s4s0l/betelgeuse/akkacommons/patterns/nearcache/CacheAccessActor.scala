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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props, SupervisorStrategy}
import akka.pattern.ask
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.ValueOwnerFacade.OwnerValueResult
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor._
import org.s4s0l.betelgeuse.akkacommons.utils.QA
import org.s4s0l.betelgeuse.akkacommons.utils.QA._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  *
  * Actor that is capable of providing cached access to some value.
  * Original concept behind this class was to cache value stored
  * by some persistent sharded actor. Because of sharding this value
  * might be on some remote node or the value might be not serializable (and
  * thus needs to be reduced to serialized form before transmission and then recreated).
  * This actor can bring this value to vm where it is running and keep it in some
  * 'enriched' form which will be forgotten after some period of time.
  *
  * This mechanism can be applied only to immutable values, as there is no cache invalidation
  * in place.
  *
  * See [[org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol]] for how to use it
  * or [[Settings]] for how to set it up.
  *
  * @author Marcin Wielgus
  */
class CacheAccessActor[G, K, R, V](settings: Settings[G, K, R, V]) extends Actor with ActorLogging {


  var cacheValueActors: Map[K, CacheValueActor.Protocol] = Map()
  //TODO: there is missing some kind of scheduled timer to clean it up!
  var requestingDuringCreation: Map[K, List[(ActorRef, Uuid)]] = Map()

  override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def receive: Actor.Receive = {
    case req@GetCacheValue(getterMessage, _) =>
      val key = settings.keyFactory(getterMessage.asInstanceOf[G])
      val valueHolder = cacheValueActors.get(key)
      if (valueHolder.isDefined) {
        valueHolder.get.apply(CacheValueActor.Protocol.GetCacheValue(req.messageId))(context.dispatcher, sender())
      } else {
        val valueCreationInProgress = requestingDuringCreation.getOrElse(key, List()).nonEmpty
        val newRequestingActors = (sender(), req.messageId) :: requestingDuringCreation.getOrElse(key, List())
        requestingDuringCreation = requestingDuringCreation + (key -> newRequestingActors)
        if (!valueCreationInProgress) {
          import akka.pattern.pipe
          import context.dispatcher
          settings.valueOwnerFacade(getterMessage.asInstanceOf[G])
            .map {
              case ValueOwnerFacade.OwnerValueOk(_, value) => InternalCreateValueActor(key, value)
              case ValueOwnerFacade.OwnerValueNotOk(_, ex) => InternalCreateValueActorFailed(key, getterMessage, ex)
            }
            .recover { case ex: Throwable => InternalCreateValueActorFailed(key, getterMessage, ex) }
            .pipeTo(self)
        }
      }

    case InternalCreateValueActor(key, valueMessage) =>
      val valueHolder = CacheValueActor.start(settings.name + "_" + key.toString, CacheValueActor.Settings(
        key.asInstanceOf[K],
        valueMessage.asInstanceOf[V],
        settings.valueEnricher,
        settings.timeoutTime))(context)
      cacheValueActors = cacheValueActors + (key.asInstanceOf[K] -> valueHolder)
      valueHolder.watchWith(context, InternalCreateValueActorTerminated(key))


    case InternalCreateValueActorTerminated(key) =>
      cacheValueActors = cacheValueActors - key.asInstanceOf[K]
      requestingDuringCreation.getOrElse(key.asInstanceOf[K], List()).reverse.foreach { it =>
        it._1 ! GetCacheValueNotOk(it._2, new Exception("Value holder died"))
      }
      requestingDuringCreation = requestingDuringCreation - key.asInstanceOf[K]

    case InternalCreateValueActorFailed(key, _, ex) =>
      log.error(ex, s"Unable to get cache value for key $key")
      requestingDuringCreation.getOrElse(key.asInstanceOf[K], List()).reverse.foreach(it => it._1 ! GetCacheValueNotOk(it._2, ex))
      requestingDuringCreation = requestingDuringCreation - key.asInstanceOf[K]

    case CacheValueDied(key, ex: Throwable) =>
      cacheValueActors = cacheValueActors - key.asInstanceOf[K]
      requestingDuringCreation.getOrElse(key.asInstanceOf[K], List()).reverse.foreach(it => it._1 ! GetCacheValueNotOk(it._2, ex))
      requestingDuringCreation = requestingDuringCreation - key.asInstanceOf[K]

    case CacheValueOk(key) =>
      val holder = cacheValueActors(key.asInstanceOf[K])
      requestingDuringCreation.getOrElse(key.asInstanceOf[K], List()).reverse.foreach(it =>
        holder.apply(CacheValueActor.Protocol.GetCacheValue(it._2))(context.dispatcher, it._1))
      requestingDuringCreation = requestingDuringCreation - key.asInstanceOf[K]
  }


}


object CacheAccessActor {

  /**
    * Creates access with given settings
    *
    * @param propsMapper - function applied to created props before passing them
    *                    to actorRefFactory, gives ability to modify it before actor
    *                    creation
    * @param actorSystem - where underlying actors should be created
    * @tparam G type of 'GetValue' message
    * @tparam K cache key type
    * @tparam R type of enriched value
    * @tparam V type of value before enriching
    * @return an protocol to use
    */
  def start[G, K, R, V](settings: Settings[G, K, R, V], propsMapper: Props => Props = identity)
                       (implicit actorSystem: ActorRefFactory): Protocol[G, K, R] = {
    val ref = actorSystem.actorOf(Props(new CacheAccessActor(settings)), settings.name)
    Protocol(ref)

  }

  trait ValueOwnerFacade[G, K, V] {
    def apply(getterMessage: G)
             (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[OwnerValueResult[K, V]]
  }

  /**
    *
    * @tparam G type of 'GetValue' message
    * @tparam K cache key type
    * @tparam R type of enriched value
    */
  final class Protocol[G, K, R] private(actorRef: => ActorRef) {

    import concurrent.duration._

    /**
      * gets a cache value.
      *
      * @param msg an message wrapping a 'GetValue' message
      */
    def apply(msg: GetCacheValue[G])
             (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[GetCacheValueResult[R]] =
      actorRef.ask(msg)(5 seconds).mapTo[GetCacheValueResult[R]]
  }


  /**
    * The [[CacheAccessActor]] works like this:
    * - when receives [[GetCacheValue]] message gets from it a 'getterMessage'.
    * - 'getterMessage' ('GetValue') message is passed to keyFactory to determine the 'key'.
    * - if value for this 'key' is present then actor holding it is asked for a value.
    * - if not, valueOwnerFacade is asked to provide a value that needs to be cached. This function can for
    * example send 'getterMessage' to some external actor and return response from this actor. Response is then passed to
    * valueEnricher what produces value that will be cached. This function can extract for eg. some information
    * from message returned by valueOwnerFacade. This final value is then kept by an additional actor
    * associated with a 'key'.
    * - after timeoutTime of inactivity of given 'key' actor holding its value is stopped.
    *
    * @param name             name of the actor
    * @param keyFactory       creates cache key from 'GetValue' message passed in [[GetCacheValue]] message
    * @param valueEnricher    function that creates value to be cached from value returned by valueOwnerFacade
    *                      function. Can be an identity function of course.
    * @param valueOwnerFacade function for getting value for given 'GetValue' message passed in [[GetCacheValue]]
    * @param timeoutTime      after what time of inactivity value for a given key should be forgotten.
    * @tparam G type of 'GetValue' message
    * @tparam K cache key type
    * @tparam R type of enriched value
    * @tparam V type of value before enriching
    */
  final case class Settings[G, K, R, V](name: String,
                                        keyFactory: G => K,
                                        valueEnricher: V => Future[R],
                                        valueOwnerFacade: ValueOwnerFacade[G, K, V],
                                        timeoutTime: FiniteDuration = 10 minutes)

  private case class InternalCreateValueActor[K, V](key: K, valueMessage: V)

  private case class InternalCreateValueActorFailed[K, G](key: K, getterMessage: G, ex: Throwable)

  private case class InternalCreateValueActorTerminated[K](key: K)

  object ValueOwnerFacade {

    sealed trait OwnerValueResult[K, V] extends Result[K, V]

    case class OwnerValueOk[K, V](correlationId: K, value: V) extends OwnerValueResult[K, V] with OkResult[K, V]

    case class OwnerValueNotOk[K, V](correlationId: K, ex: Throwable) extends OwnerValueResult[K, V] with NotOkResult[K, V]

  }

  object Protocol {

    def apply[G, K, R](actorRef: => ActorRef): Protocol[G, K, R] = new Protocol(actorRef)

    sealed trait IncomingMessage

    sealed trait GetCacheValueResult[V] extends Result[Uuid, V]

    case class GetCacheValue[G](getterMessage: G, messageId: Uuid = QA.uuid) extends IncomingMessage with UuidQuestion

    case class GetCacheValueOk[V](correlationId: Uuid, value: V) extends GetCacheValueResult[V] with OkResult[Uuid, V]

    case class GetCacheValueNotOk[V](correlationId: Uuid, ex: Throwable) extends GetCacheValueResult[V] with NotOkResult[Uuid, V]

    private[nearcache] case class CacheValueDied[K](key: K, ex: Throwable) extends IncomingMessage

    private[nearcache] case class CacheValueOk[K](key: K) extends IncomingMessage


  }


}