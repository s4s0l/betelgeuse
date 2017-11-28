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

package org.s4s0l.betelgeuse.akkacommons.patterns.nearcache

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props, SupervisorStrategy}
import akka.pattern.ask
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol.{CacheValue, CacheValueDied, CacheValueOk, GetCacheValue}
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.{CreateValueActor, CreateValueActorFailed, CreateValueActorTerminated, Settings}

import scala.concurrent.Future
import scala.concurrent.duration._
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
  * This mechanizm can be applied only to immutable values, as there is no cache invalidation
  * in place.
  *
  * See [[org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol]] for how to use it
  * or [[Settings]] for how to set it up.
  *
  * @author Marcin Wielgus
  */
class CacheAccessActor[G, K, R, V](settings: Settings[G, K, R, V]) extends Actor with ActorLogging {


  var cacheValueActors: Map[K, CacheValueActor.Protocol] = Map()
  //TODO: there is missing some kind of sheduled timer to clean it up!
  var currentRequestors: Map[K, List[ActorRef]] = Map()

  override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def receive: Actor.Receive = {
    case GetCacheValue(getterMessage) =>
      val key = settings.keyFactory(getterMessage.asInstanceOf[G])
      currentRequestors = currentRequestors + (key -> (sender() :: currentRequestors.getOrElse(key, List())))
      val valueHolder = cacheValueActors.get(key)
      if (valueHolder.isDefined) {
        valueHolder.get.apply(CacheValueActor.Protocol.GetCacheValue())(sender())
      } else {
        import akka.pattern.pipe
        import context.dispatcher
        val senderTmp = sender()
        settings.valueOwnerFacade(getterMessage.asInstanceOf[G])
          .map(it => CreateValueActor(key, it, senderTmp))
          .recover { case ex: Throwable => CreateValueActorFailed(key, getterMessage, ex, senderTmp) }
          .pipeTo(self)
      }

    case CreateValueActor(key, None, senderTmp) =>
      forgetSender(key.asInstanceOf[K], senderTmp, CacheValue(key.asInstanceOf[K], Left(None)))

    case CreateValueActor(key, Some(valueMessage), senderTmp) =>
      val valueHolder = cacheValueActors.get(key.asInstanceOf[K])
      if (valueHolder.isDefined) {
        valueHolder.get.apply(CacheValueActor.Protocol.GetCacheValue())(senderTmp)
      } else {
        val valueHolder = CacheValueActor.start(settings.name + "_" + key.toString, CacheValueActor.Settings(
          key.asInstanceOf[K],
          valueMessage.asInstanceOf[V],
          settings.valueEnricher,
          settings.timeoutTime))(context)
        valueHolder.apply(CacheValueActor.Protocol.GetCacheValue())(senderTmp)
        cacheValueActors = cacheValueActors + (key.asInstanceOf[K] -> valueHolder)
        valueHolder.watchWith(context, CreateValueActorTerminated(key))
      }

    case CreateValueActorTerminated(key) =>
      cacheValueActors = cacheValueActors - key.asInstanceOf[K]

    case CreateValueActorFailed(key, _, ex, senderTmp) =>
      log.error(ex, s"Unable to get cache value for key $key")
      forgetSender(key.asInstanceOf[K], senderTmp, CacheValue(key.asInstanceOf[K], Right(ex)))

    case CacheValueDied(key, ex: Throwable) =>
      currentRequestors.getOrElse(key.asInstanceOf[K], List()).foreach(_ ! CacheValue(key.asInstanceOf[K], Right(ex)))
      currentRequestors = currentRequestors - key.asInstanceOf[K]

    case CacheValueOk(key) =>
      currentRequestors = currentRequestors - key.asInstanceOf[K]
  }

  private def forgetSender(key: K, sndr: ActorRef, message: Any): Unit = {
    sndr ! message
    val refs = currentRequestors.getOrElse(key, List()).filter(_ != sndr)
    if (refs.isEmpty) {
      currentRequestors = currentRequestors - key
    } else {
      currentRequestors = currentRequestors + (key -> refs)
    }
  }

}


object CacheAccessActor {

  /**
    * Creates accesor with given settings
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
                                        valueEnricher: V => R,
                                        valueOwnerFacade: G => Future[Option[V]],
                                        timeoutTime: FiniteDuration = 10 minutes)

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
      * @return see [[CacheValue]] docs
      */
    def apply(msg: GetCacheValue[G]): Future[CacheValue[K, R]] =
      actorRef.ask(msg)(5 seconds).mapTo[CacheValue[K, R]]
  }

  private case class CreateValueActor[K, V](key: K, valueMessage: Option[V], sender: ActorRef)

  private case class CreateValueActorFailed[K, G](key: K, getterMessage: G, ex: Throwable, sender: ActorRef)

  private case class CreateValueActorTerminated[K](key: K)

  object Protocol {


    def apply[G, K, R](actorRef: => ActorRef): Protocol[G, K, R] = new Protocol(actorRef)

    sealed trait IncomingMessage

    sealed trait OutgoingMessage

    case class GetCacheValue[G](getterMessage: G) extends IncomingMessage

    /**
      * Returned cache value
      *
      * @param key   cache key
      * @param value either throwable in case of any error or None if value is not found,
      *              or value on the left.
      * @tparam K cache key type
      * @tparam R type of enriched value
      **/
    case class CacheValue[K, R](key: K, value: Either[Option[R], Throwable]) extends OutgoingMessage

    private[nearcache] case class CacheValueDied[K](key: K, ex: Throwable) extends IncomingMessage

    private[nearcache] case class CacheValueOk[K](key: K) extends IncomingMessage


  }


}