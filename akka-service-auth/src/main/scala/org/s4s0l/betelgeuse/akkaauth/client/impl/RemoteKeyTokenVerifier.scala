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

/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkaauth.client.impl

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, FSM, Props, Timers}
import akka.pattern._
import akka.util.Timeout
import akka.{Done, util}
import org.s4s0l.betelgeuse.akkaauth.client.TokenVerifier
import org.s4s0l.betelgeuse.akkaauth.client.impl.RemoteKeyTokenVerifier._
import org.s4s0l.betelgeuse.akkaauth.common
import org.s4s0l.betelgeuse.akkaauth.common.{KeyManager, RemoteApi, SerializedToken}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * @author Marcin Wielgus
  */
class RemoteKeyTokenVerifier[A](remoteApi: RemoteApi,
                                keyAvailable: Promise[Done],
                                attrsUnmarshaller: Map[String, String] => A)
                               (implicit serializer: JacksonJsonSerializer)
  extends Actor
    with ActorLogging
    with Timers
    with FSM[KeyState, Option[(String, TokenVerifier[A])]] {

  private implicit val ec: ExecutionContext = context.dispatcher
  private val getKeyTimeout: FiniteDuration = context.system.settings.config.getDuration("bg.auth.client.public-key-timeout")
  startWith(KeyAbsent, None)

  when(KeyAbsent) {
    case Event(GetKey, _) =>
      fetchKey()
      stay()
    case Event(Verify(_, _), _) =>
      sender() ! Result(new Exception("public key missing"))
      stay()
    case Event(RemoteResponse(Some(theKey)), _) =>
      val key = KeyManager.publicKeyFromBase64(theKey)
      val newData = Some((theKey, new TokenVerifierImpl[A](key, attrsUnmarshaller)))
      log.info(s"JWT Public Key obtained: [$theKey].")
      keyAvailable.success(Done)
      goto(KeyPresent) using newData
    case Event(RemoteResponse(None), _) =>
      stay()
  }

  when(KeyPresent) {
    case Event(GetKey, _) =>
      fetchKey()
      stay()
    case Event(Verify(token, timeout), Some((_, verifier))) =>
      implicit val to: util.Timeout = timeout
      verifier.verify(token)
        .map(authInfo => Result(authInfo))
        .recover {
          case ex: Throwable => Result(ex)
        }.pipeTo(sender())
      stay()
    case Event(RemoteResponse(Some(theKey)), Some((oldKey, _))) =>
      if (oldKey != theKey) {
        val key = KeyManager.publicKeyFromBase64(theKey)
        val newData = Some((theKey, new TokenVerifierImpl[A](key, attrsUnmarshaller)))
        log.info(s"JWT Public Key refreshed: [$theKey].")
        goto(KeyPresent) using newData
      } else {
        log.debug(s"JWT Public Key not changed: [$theKey].")
        stay()
      }
    case Event(RemoteResponse(None), _) =>
      stay()
  }

  initialize()

  override def preStart(): Unit =
    timers.startPeriodicTimer("KeyRefreshTimer", GetKey, 15.minutes)

  private def fetchKey(): Unit = {
    implicit val to: util.Timeout = getKeyTimeout
    remoteApi.getPublicKey()
      .map(it => RemoteResponse(Some(it.base64Key)))
      .recover {
        case ex: Throwable =>
          log.error(ex, "Unable to fetch public key")
          RemoteResponse(None)
      }
      .pipeToWithTimeout(self, 10.seconds, RemoteResponse(None), context.system.getScheduler)
  }
}

object RemoteKeyTokenVerifier {

  def start[A](remoteApi: RemoteApi,
               keyAvailable: Promise[Done],
               attrsUnmarshaller: Map[String, String] => A)
              (implicit serializer: JacksonJsonSerializer,
               actorRefFactory: ActorRefFactory)
  : Verifier[A] = {
    val ref = actorRefFactory.actorOf(
      Props(new RemoteKeyTokenVerifier[A](remoteApi, keyAvailable, attrsUnmarshaller)),
      "bgAuthRemoteKeyTokenVerifier"
    )
    new Verifier[A] {
      override def verify(serializedToken: SerializedToken)
                         (implicit ec: ExecutionContext,
                          timeout: Timeout,
                          sender: ActorRef = ActorRef.noSender)
      : Future[common.AuthInfo[A]] = {
        (new AskableActorRef(ref) ? Verify(serializedToken, timeout))
          .map {
            case Result(Left(exception)) =>
              throw new Exception(exception)
            case Result(Right(result)) =>
              result.asInstanceOf[common.AuthInfo[A]]
          }
      }

      override def init(): Unit =
        ref ! GetKey
    }
  }

  trait Verifier[A] extends TokenVerifier[A] {
    def init(): Unit
  }

  sealed trait KeyState

  object KeyAbsent extends KeyState

  object KeyPresent extends KeyState

  case class RemoteResponse(value: Option[String])

  case object GetKey

  case class Verify(serializedToken: SerializedToken, timeout: Timeout)

  case class Result[A](res: Either[Throwable, common.AuthInfo[A]])

  object Result {
    def apply[A](error: Throwable): Result[A] = new Result(Left(error))

    def apply[A](value: common.AuthInfo[A]): Result[A] = new Result(Right(value))

  }

}
