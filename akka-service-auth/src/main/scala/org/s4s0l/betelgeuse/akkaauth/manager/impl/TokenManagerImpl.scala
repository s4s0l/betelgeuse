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

package org.s4s0l.betelgeuse.akkaauth.manager.impl

import java.util.Date

import akka.Done
import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.AskableActorRef
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.fasterxml.jackson.annotation.{JsonInclude, JsonSubTypes, JsonTypeInfo}
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkaauth.common
import org.s4s0l.betelgeuse.akkaauth.common.{AccessToken, RefreshToken, TokenId, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.TokenManager
import org.s4s0l.betelgeuse.akkaauth.manager.impl.TokenManagerImpl._
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.PersistentShardedActor
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.akkacommons.utils.TimeoutShardedActor
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */
private class TokenManagerImpl()(implicit val domainEventClassTag: ClassTag[DomainEvent])
  extends PersistentShardedActor
    with PersistentFSM[TokenState, TokenData, DomainEvent]
    with TimeoutShardedActor
    with ActorLogging {

  override val timeoutTime: FiniteDuration = context.system.settings.config.getDuration("bg.auth.provider.entity-passivation-timeout")

  startWith(InitialState, InitialData)

  when(InitialState, 5.second) {
    case Event(ce: CreateEvent, InitialData) =>
      goto(CreatedState) applying ce andThen { _ =>
        sender() ! Result(Left(Done))
      }
    case Event(StateTimeout, _) =>
      shardedPassivate()
      stay()
  }

  when(CreatedState) {
    case Event("revoke", CreatedData(_)) =>
      goto(RevokedState) applying RevokeEvent(new Date()) andThen { _ =>
        sender() ! Result(Left(Done))
      }
    case Event("is-valid", CreatedData(_)) =>
      sender() ! Result(Left(true))
      stay()
    case Event("get-subject", CreatedData(ce)) =>
      sender() ! Result(Left(UserId(ce.userId)))
      stay()
  }

  when(RevokedState) {
    case Event("revoke", RevokedData(_, _)) =>
      sender() ! Result(Left(Done))
      stay()
    case Event("is-valid", RevokedData(_, _)) =>
      sender() ! Result(Left(false))
      stay()
    case Event("get-subject", RevokedData(ce, _)) =>
      sender() ! Result(Left(UserId(ce.userId)))
      stay()
  }

  whenUnhandled {
    case Event(_, InitialData) =>
      sender() ! Result(Right(ErrorMessage(s"Token does not exist: $shardedActorId")))
      shardedPassivate()
      stay()
    case Event(_: CreateEvent, _) =>
      sender() ! Result(Right(ErrorMessage(s"duplicate token id: $shardedActorId")))
      stay()
    case Event(_, data) =>
      sender() ! Result(Right(ErrorMessage(s"token in invalid state: $shardedActorId ($data)")))
      stay()
  }

  override def applyEvent(domainEvent: DomainEvent, currentData: TokenData): TokenData = {
    (domainEvent, currentData) match {
      case (ce: CreateEvent, _) =>
        CreatedData(ce)
      case (re: RevokeEvent, CreatedData(ce)) =>
        RevokedData(ce, re.when)
      case _ =>
        throw new IllegalStateException("This should not happen")
    }
  }
}

object TokenManagerImpl {

  def start(implicit system: BgClusteringShardingExtension, config: Config)
  : TokenManager = {


    val ref = system.start(
      typeName = "token-manager",
      entityProps = Props(new TokenManagerImpl()),
      extractEntityId = {
        case (tokenId: TokenId, msg) =>
          (tokenId.id, msg)
      })


    def ask[T](userId: TokenId, message: Any)
              (implicit ec: ExecutionContext,
               timeout: Timeout,
               sender: ActorRef = ActorRef.noSender)
    : Future[T] = {
      (new AskableActorRef(ref) ? (userId, message))
        .map {
          case Result(Left(value)) => value.asInstanceOf[T]
          case Result(Right(ErrorMessage(errorMessage))) =>
            throw new Exception(errorMessage)
        }
    }

    new TokenManager() {
      override def saveToken(token: common.TokenInfo[_ <: common.TokenType],
                             userId: common.UserId)
                            (implicit ec: ExecutionContext,
                             timeout: Timeout,
                             sender: ActorRef = ActorRef.noSender)
      : Future[Done] =
        ask(token.tokenType.tokenId, CreateEvent(
          token.tokenType.tokenId.id,
          userId.id,
          token.expiration,
          token.issuedAt,
          token.issuer,
          token.tokenType match {
            case _: AccessToken => "access"
            case _: RefreshToken => "refresh"
          }
        ))

      override def revokeToken(tokenId: TokenId)
                              (implicit ec: ExecutionContext,
                               timeout: Timeout,
                               sender: ActorRef = ActorRef.noSender)
      : Future[Done] =
        ask(tokenId, "revoke")

      override def isValid(tokenId: TokenId)(implicit ec: ExecutionContext,
                                             timeout: Timeout,
                                             sender: ActorRef = ActorRef.noSender)
      : Future[Boolean] =
        ask(tokenId, "is-valid")

      override def getSubject(tokenId: TokenId)(implicit ec: ExecutionContext,
                                                timeout: Timeout,
                                                sender: ActorRef = ActorRef.noSender)
      : Future[common.UserId] =
        ask(tokenId, "get-subject")
    }

  }


  private case class ErrorMessage(message: String)

  private case class Result[T](res: Either[T, ErrorMessage])

  sealed trait TokenData

  private case object InitialData extends TokenData

  private case class CreatedData(createEvent: CreateEvent) extends TokenData

  private case class RevokedData(createEvent: CreateEvent, revokedAt: Date) extends TokenData

  sealed trait TokenState extends FSMState {
    override def identifier: String = getClass.getSimpleName
  }

  private case object InitialState extends TokenState

  private case object CreatedState extends TokenState

  private case object RevokedState extends TokenState


  @JsonInclude(Include.NON_NULL)
  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes(Array(
    new Type(name = "create", value = classOf[CreateEvent]),
    new Type(name = "revoke", value = classOf[RevokeEvent])
  ))
  sealed trait DomainEvent extends JacksonJsonSerializable

  private case class CreateEvent(tokenId: String,
                                 userId: String,
                                 expiration: Date,
                                 issuedAt: Date,
                                 issuer: Option[String],
                                 tokenType: String) extends DomainEvent

  private case class RevokeEvent(when: Date) extends DomainEvent

}
