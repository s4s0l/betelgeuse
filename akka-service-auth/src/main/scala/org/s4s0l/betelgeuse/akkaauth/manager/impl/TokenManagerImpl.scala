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
import akka.actor.Status.Failure
import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.AskableActorRef
import akka.persistence.fsm.PersistentFSM.{FSMState, StateChangeEvent}
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.fasterxml.jackson.annotation.{JsonInclude, JsonSubTypes, JsonTypeInfo}
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkaauth.common
import org.s4s0l.betelgeuse.akkaauth.common.{TokenId, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.ProviderExceptions.{TokenAlreadyExist, TokenDoesNotExist, TokenIllegalState}
import org.s4s0l.betelgeuse.akkaauth.manager.TokenManager
import org.s4s0l.betelgeuse.akkaauth.manager.TokenManager.{TokenCreationParams, TokenPurpose}
import org.s4s0l.betelgeuse.akkaauth.manager.impl.TokenManagerImpl.{DomainEvent, TokenData, _}
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
    with TimeoutShardedActor
    with ActorLogging {

  override val timeoutTime: FiniteDuration = context.system.settings.config.getDuration("bg.auth.provider.entity-passivation-timeout")

  lazy val tokenId: TokenId = TokenId(shardedActorId)

  //noinspection ActorMutableStateInspection
  private var state: TokenData = InitialData

  override def receiveCommand: Receive = {
    case a if receiveCommandInternal.isDefinedAt(a) => receiveCommandInternal.apply(a)
  }

  /**
    * as receive command is not called upon every message, we need something
    * we need to wrap in receiveCommand, its done this way to organize case
    * statements 'state match' first, then command.
    *
    * @return
    */
  private def receiveCommandInternal = state match {
    case InitialData => withDefault {
      case ce: CreateEvent =>
        persist(ce) { evt =>
          applyEvent(evt)
          sender() ! Done
        }
      case _ =>
        sender() ! Failure(TokenDoesNotExist(tokenId))
        shardedPassivate()
    }
    case CreatedData(created) => withDefault {
      case "revoke" =>
        persist(RevokeEvent(new Date())) { evt =>
          applyEvent(evt)
          sender() ! Done
        }
      case "is-valid" =>
        sender() ! true
      case "get-subject" =>
        sender() ! UserId(created.userId)
    }
    case RevokedData(ce, _) => withDefault {
      case "revoke" =>
        sender() ! Done
      case "is-valid" =>
        sender() ! false
      case "get-subject" =>
        sender() ! UserId(ce.userId)
    }
  }

  override def receiveRecover: Receive = {
    case evt: DomainEvent => applyEvent(evt)
    case StateChangeEvent(_, _) =>
  }

  private def withDefault(receive: Receive): Receive = receive orElse {
    case _: CreateEvent =>
      sender() ! Failure(TokenAlreadyExist(tokenId))
    case _ =>
      sender() ! Failure(TokenIllegalState(tokenId))
  }

  private def applyEvent(domainEvent: DomainEvent): Unit = {
    state = (domainEvent, state) match {
      case (ce: CreateEvent, InitialData) =>
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
        case (TokenId(id), msg) =>
          (id, msg)
      })


    def ask[T](tokenId: TokenId, message: Any)
              (implicit ec: ExecutionContext,
               timeout: Timeout,
               sender: ActorRef = ActorRef.noSender,
               classTag: ClassTag[T]
              )
    : Future[T] = {
      (new AskableActorRef(ref) ? (tokenId, message)).mapTo[T]
    }

    new TokenManager() {
      override def saveToken(creationParams: TokenCreationParams)
                            (implicit ec: ExecutionContext,
                             timeout: Timeout,
                             sender: ActorRef = ActorRef.noSender)
      : Future[Done] = {
        val token = creationParams.token
        val userId = creationParams.userId
        ask[Done](token.tokenType.tokenId, CreateEvent(
          token.tokenType.tokenId.id,
          userId.id,
          token.expiration,
          token.issuedAt,
          token.issuer,
          token.tokenType.tokenTypeName,
          creationParams.purpose,
          creationParams.description
        ))
      }

      override def revokeToken(tokenId: TokenId)
                              (implicit ec: ExecutionContext,
                               timeout: Timeout,
                               sender: ActorRef = ActorRef.noSender)
      : Future[Done] =
        ask[Done](tokenId, "revoke")

      override def isValid(tokenId: TokenId)(implicit ec: ExecutionContext,
                                             timeout: Timeout,
                                             sender: ActorRef = ActorRef.noSender)
      : Future[Boolean] =
        ask[Boolean](tokenId, "is-valid")

      override def getSubject(tokenId: TokenId)(implicit ec: ExecutionContext,
                                                timeout: Timeout,
                                                sender: ActorRef = ActorRef.noSender)
      : Future[common.UserId] =
        ask[common.UserId](tokenId, "get-subject")
    }

  }

  sealed trait TokenData

  private case object InitialData extends TokenData

  private case class CreatedData(createEvent: CreateEvent) extends TokenData

  private case class RevokedData(createEvent: CreateEvent, revokedAt: Date) extends TokenData

  /**
    * ########################
    * some time ago it was a FSM, for backward compatibility
    * leave it here
    */
  //noinspection ScalaUnusedSymbol
  sealed trait TokenState extends FSMState {
    override def identifier: String = getClass.getSimpleName
  }

  //noinspection ScalaUnusedSymbol
  private case object InitialState extends TokenState

  //noinspection ScalaUnusedSymbol
  private case object CreatedState extends TokenState

  //noinspection ScalaUnusedSymbol
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
                                 tokenType: String,
                                 purpose: TokenPurpose,
                                 description: Option[String]) extends DomainEvent

  private case class RevokeEvent(when: Date) extends DomainEvent

}
