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

package org.s4s0l.betelgeuse.akkaauth.manager.impl

import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.AskableActorRef
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.fasterxml.jackson.annotation.{JsonInclude, JsonSubTypes, JsonTypeInfo}
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkaauth.common.{UserAttributes, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{Role, UserDetailedAttributes, UserDetailedInfo}
import org.s4s0l.betelgeuse.akkaauth.manager.impl.UserManagerImpl._
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
class UserManagerImpl()(implicit val domainEventClassTag: ClassTag[DomainEvent])
  extends PersistentShardedActor
    with PersistentFSM[UserState, UserData, DomainEvent]
    with TimeoutShardedActor
    with ActorLogging {

  override val timeoutTime: FiniteDuration = context.system.settings.config.getDuration("bg.auth.provider.entity-passivation-timeout")

  startWith(InitialState, NotExistsData)

  when(InitialState, 5.second) {
    case Event(("createUser", ud: UserDetailedInfo), NotExistsData) =>
      val createEvent = CreateEvent(
        ud.attributes.userAttributed,
        ud.attributes.roles.map(_.name),
        ud.attributes.additionalAttributes,
        ud.login,
        ud.locked
      )
      goto(CreatedState) applying createEvent andThen { _ =>
        sender() ! Result(Left(Done))
      }
    case Event(StateTimeout, _) =>
      shardedPassivate()
      stay()
  }

  when(CreatedState) {
    case Event(("updateRoles", roles: Set[_]), _: ExistsData) =>
      stay() applying UpdateRoleEvent(roles.map(_.asInstanceOf[Role].name)) andThen { _ =>
        sender() ! Result(Left(Done))
      }
    case Event(("updateAdditionalAttributes", attrs: Map[_, _]), _: ExistsData) =>
      stay() applying UpdateAttrsEvent(attrs.asInstanceOf[Map[String, Option[String]]]) andThen { _ =>
        sender() ! Result(Left(Done))
      }
    case Event(("getUser", _), d: ExistsData) =>
      sender() ! Result(Left(
        UserManager.UserDetailedInfo(
          userId = UserId(shardedActorId),
          attributes = UserDetailedAttributes(
            userAttributed = d.userAttributed,
            roles = d.roles.map(Role),
            additionalAttributes = d.additionalAttributes
          ),
          login = d.login,
          locked = d.locked
        )
      ))
      stay()
    case Event(("lockUser", _), _: ExistsData) =>
      stay() applying UpdateLockEvent(true) andThen { _ =>
        sender() ! Result(Left(Done))
      }
    case Event(("unLockUser", _), _: ExistsData) =>
      stay() applying UpdateLockEvent(false) andThen { _ =>
        sender() ! Result(Left(Done))
      }
  }


  whenUnhandled {
    case Event(_, NotExistsData) =>
      sender() ! Result(Right(ErrorMessage(s"User does not exist: $shardedActorId")))
      stay()
    case Event(("createUser", _: UserDetailedInfo), _) =>
      sender() ! Result(Right(ErrorMessage(s"duplicate user id: $shardedActorId")))
      stay()
    case Event(_, data) =>
      sender() ! Result(Right(ErrorMessage(s"user in invalid state: $shardedActorId ($data)")))
      stay()
  }


  override def applyEvent(domainEvent: DomainEvent, currentData: UserData)
  : UserData = {
    (domainEvent, currentData) match {
      case (ce: CreateEvent, NotExistsData) =>
        ExistsData(ce.userAttributed, ce.roles, ce.additionalAttributes, ce.login, ce.locked)
      case (UpdateRoleEvent(roles), d: ExistsData) =>
        d.copy(roles = roles)
      case (UpdateAttrsEvent(attrs), d: ExistsData) =>
        val oldAttrs = d.additionalAttributes
        val newAttrs = attrs.foldLeft(oldAttrs) { (c, elem) =>
          elem match {
            case (key, None) =>
              c - key
            case (key, Some(value)) =>
              c + (key -> value)
          }
        }
        d.copy(additionalAttributes = newAttrs)
      case (UpdateLockEvent(newLock), d: ExistsData) =>
        d.copy(locked = newLock)
      case _ =>
        throw new IllegalStateException("This should not happen")
    }
  }
}

object UserManagerImpl {

  def start(implicit system: BgClusteringShardingExtension, config: Config)
  : UserManager = {

    val ref = system.start(
      typeName = "token-manager",
      entityProps = Props(new UserManagerImpl()),
      extractEntityId = {
        case (userId: UserId, msg) =>
          (userId.id, msg)
      })

    def ask[T](command: String, userId: UserId, message: Any)
              (implicit ec: ExecutionContext,
               timeout: Timeout,
               sender: ActorRef = ActorRef.noSender)
    : Future[T] = {
      (new AskableActorRef(ref) ? (userId, (command, message)))
        .map {
          case Result(Left(value)) => value.asInstanceOf[T]
          case Result(Right(ErrorMessage(errorMessage))) =>
            throw new Exception(errorMessage)
        }
    }

    new UserManager() {

      override def generateUserId()
                                 (implicit ec: ExecutionContext,
                                  timeout: Timeout,
                                  sender: ActorRef = ActorRef.noSender): Future[UserId] = {
        Future.successful(UserId(UUID.randomUUID().toString))
      }

      override def createUser(userInfo: UserManager.UserDetailedInfo)
                             (implicit ec: ExecutionContext,
                              timeout: Timeout,
                              sender: ActorRef = ActorRef.noSender)
      : Future[Done] = ask("createUser", userInfo.userId, userInfo)


      override def updateRoles(userId: UserId, roles: Set[UserManager.Role])
                              (implicit ec: ExecutionContext,
                               timeout: Timeout,
                               sender: ActorRef = ActorRef.noSender)
      : Future[Done] = ask("updateRoles", userId, roles)

      override def updateAdditionalAttributes(userId: UserId, attrs: Map[String, Option[String]])
                                             (implicit ec: ExecutionContext,
                                              timeout: Timeout,
                                              sender: ActorRef = ActorRef.noSender)
      : Future[Done] = ask("updateAdditionalAttributes", userId, attrs)

      override def getUser(userId: UserId)
                          (implicit ec: ExecutionContext,
                           timeout: Timeout,
                           sender: ActorRef = ActorRef.noSender)
      : Future[UserManager.UserDetailedInfo] = ask("getUser", userId, NotUsed)

      override def lockUser(userId: UserId)
                           (implicit ec: ExecutionContext,
                            timeout: Timeout,
                            sender: ActorRef = ActorRef.noSender)
      : Future[Done] = ask("lockUser", userId, NotUsed)

      override def unLockUser(userId: UserId)
                             (implicit ec: ExecutionContext,
                              timeout: Timeout,
                              sender: ActorRef = ActorRef.noSender)
      : Future[Done] = ask("unLockUser", userId, NotUsed)
    }
  }

  private case class ErrorMessage(message: String)

  private case class Result[T](res: Either[T, ErrorMessage])

  sealed trait UserData

  private case object NotExistsData extends UserData

  private case class ExistsData(
                                 userAttributed: UserAttributes,
                                 roles: Set[String],
                                 additionalAttributes: Map[String, String],
                                 login: Option[String],
                                 locked: Boolean
                               )
    extends UserData

  sealed trait UserState extends FSMState {
    override def identifier: String = getClass.getSimpleName
  }

  private case object InitialState extends UserState

  private case object CreatedState extends UserState

  @JsonInclude(Include.NON_NULL)
  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes(Array(
    new Type(name = "create", value = classOf[CreateEvent]),
    new Type(name = "lock", value = classOf[UpdateLockEvent]),
    new Type(name = "updateAttrs", value = classOf[UpdateAttrsEvent]),
    new Type(name = "updateRole", value = classOf[UpdateRoleEvent])
  ))
  sealed trait DomainEvent extends JacksonJsonSerializable

  private case class CreateEvent(userAttributed: UserAttributes,
                                 roles: Set[String],
                                 additionalAttributes: Map[String, String],
                                 login: Option[String],
                                 locked: Boolean)
    extends DomainEvent

  private case class UpdateRoleEvent(roles: Set[String])
    extends DomainEvent

  private case class UpdateAttrsEvent(attrs: Map[String, Option[String]])
    extends DomainEvent

  private case class UpdateLockEvent(locked: Boolean)
    extends DomainEvent

}