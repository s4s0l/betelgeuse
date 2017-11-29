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

package org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib

import akka.actor.Status.{Failure, Status}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.Protocol.{OriginStateChanged, OriginStateChangedConfirm}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.utils.AllUtils.{listOfFuturesToFutureOfList, _}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * This actually could be generalized to broadcast acknowledgeable actor.
  * forwards message to N destinations and awaits its responses confirms received message
  * only if all destinations confirmed.
  *
  * @author Marcin Wielgus
  */
class OriginStateDistributor[T](settings: Settings[T]) extends Actor with ActorLogging {

  import context.dispatcher

  override def receive: Actor.Receive = {
    case OriginStateChanged(versionedId, value, duration) =>
      val originalSender = sender()
      val start = System.currentTimeMillis()
      implicit val timeout: Timeout = duration
      val stateChangeResult: Future[String] = listOfFuturesToFutureOfList(
        settings.satelliteStates.map { case (satellite, api) =>
          api.stateChanged(versionedId, value.asInstanceOf[T], satellite)
            .recoverToAkkaStatus
        }.toSeq)
        .filter(seq => !seq.exists(_.isInstanceOf[Failure]))
        .map(_ => "ok")


      stateChangeResult.flatMap { _ =>
        val timeSpentSoFar = System.currentTimeMillis() - start
        if (timeSpentSoFar > duration.toMillis) {
          Future.failed(new Exception("state distribution took too long, aborting commit."))
        } else {
          implicit val timeout: Timeout = duration - (timeSpentSoFar millisecond)
          listOfFuturesToFutureOfList(
            settings.satelliteStates.map { case (satellite, api) =>
              api.stateDistributed(versionedId, satellite)
                .recoverToAkkaStatus
            }.toSeq)
            .filter(seq => !seq.exists(_.isInstanceOf[Failure]))
        }
      }
        .map(_ => OriginStateChangedConfirm(versionedId))
        .andThen {
          case scala.util.Success(r) ⇒
            val timeSpentSoFar = System.currentTimeMillis() - start
            if (timeSpentSoFar <= duration.toMillis) {
              originalSender ! r
            }
          case scala.util.Failure(_) ⇒
        }


  }
}


object OriginStateDistributor {
  /**
    * creates props for actor
    */
  def start[T](settings: Settings[T], propsMapper: Props => Props = identity)
              (implicit actorSystem: ActorRefFactory): Protocol[T] = {
    val ref = actorSystem.actorOf(Props(new OriginStateDistributor(settings)))
    Protocol(ref)
  }

  trait SatelliteProtocol[T] {
    /**
      * distributes state change
      */
    def stateChanged(versionedId: VersionedId, value: T, destination: String)
                    (implicit timeouted: Timeout, executionContext: ExecutionContext): Future[Status]

    /**
      * informs that all destinations confirmed
      */

    def stateDistributed(versionedId: VersionedId, destination: String)
                        (implicit timeouted: Timeout, executionContext: ExecutionContext): Future[Status]
  }

  final case class Settings[T](name: String, satelliteStates: Map[String, SatelliteProtocol[T]])

  /**
    * An protocol for [[OriginStateDistributor]]
    */
  final class Protocol[T] private(actorRef: => ActorRef) {

    /**
      * Emits state change. Sender should expect
      * [[org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.Protocol.OriginStateChangedConfirm]]
      */
    def stateChanged(msg: OriginStateChanged[T])
                    (implicit sender: ActorRef = Actor.noSender)
    : Unit =
      actorRef ! msg

  }

  object Protocol {
    /**
      * Wraps actor ref factory with protocol interface
      */
    def apply[T](actorRef: => ActorRef): Protocol[T] = new Protocol(actorRef)

    case class OriginStateChanged[T](versionedId: VersionedId, value: T, expectedConfirmIn: FiniteDuration)

    case class OriginStateChangedConfirm(versionedId: VersionedId)

  }


}    