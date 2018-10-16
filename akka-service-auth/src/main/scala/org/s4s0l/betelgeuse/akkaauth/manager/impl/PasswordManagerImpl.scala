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

import java.security.SecureRandom
import java.util.Base64

import akka.Done
import akka.actor.Status.Failure
import akka.actor.{ActorRef, Props}
import akka.cluster.sharding.ShardRegion
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.fasterxml.jackson.annotation.{JsonInclude, JsonSubTypes, JsonTypeInfo}
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkaauth.common.{PasswordCredentials, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.HashProvider.HashedValue
import org.s4s0l.betelgeuse.akkaauth.manager.impl.PasswordManagerImpl.PasswordManagerCommand._
import org.s4s0l.betelgeuse.akkaauth.manager.impl.PasswordManagerImpl.{PasswordManagerCommand, _}
import org.s4s0l.betelgeuse.akkaauth.manager.{HashProvider, PasswordManager}
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.PersistentShardedActor
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.akkacommons.utils.{ActorTarget, TimeoutShardedActor}
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class PasswordManagerImpl(hashProvider: HashProvider)
  extends PersistentShardedActor
    with TimeoutShardedActor {

  var state: Option[PasswordState] = None

  private val encoder = Base64.getEncoder
  private val decoder = Base64.getDecoder

  override val timeoutTime: FiniteDuration = context.system.settings.config.getDuration("bg.auth.provider.entity-passivation-timeout")

  private def isInitialized: Boolean = state isDefined

  def updateState(event: PasswordManagerEvent): Unit = {
    event match {
      case PasswordCreated(userId, hash, salt, createdAt) =>
        state = Some(PasswordState(HashProvider.HashedValue(decoder.decode(hash), decoder.decode(salt)), userId, createdAt, enabled = false, 1, initialized = true))
      case PasswordEnabled() if isInitialized =>
        state = state.map(_.copy(enabled = true))
      case PasswordChanged(hash, salt) if isInitialized =>
        state = state.map { s =>
          s.copy(hash = HashProvider.HashedValue(decoder.decode(hash), decoder.decode(salt)), updatedCount = s.updatedCount + 1)
        }
      case PasswordRemoved() =>
        state = None
      case e => log.warning(s"Got update event $e in state $state. It cannot be applied.") // this should not happen
    }
  }

  override def receiveRecover: Receive = {
    case evt: PasswordManagerEvent => updateState(evt)
  }

  override def receiveCommand: Receive = {
    case CreatePassword(userId, PasswordCredentials(_, password)) =>
      state match {
        case Some(_) => sender() ! Failure(new Exception("Password already initialized"))
        case None =>
          val h = hashProvider.hashPassword(password)
          persist(PasswordCreated(userId, encoder.encodeToString(h.hash), encoder.encodeToString(h.salt), System.currentTimeMillis())) { event =>
            updateState(event)
            sender() ! Done
          }
      }
    case VerifyPassword(PasswordCredentials(_, password)) if isInitialized =>
      state.foreach { currentState =>
        if (currentState.enabled) {
          if (hashProvider.checkPassword(currentState.hash, password)) {
            sender() ! currentState.userId
          } else {
            sender() ! Failure(new Exception("Invalid password"))
          }
        } else {
          sender() ! Failure(new Exception("Password not enabled"))
        }
      }

    case EnablePassword(_) if isInitialized =>
      state foreach { currentState =>
        if (!currentState.enabled) {
          persist(PasswordEnabled()) { evt =>
            updateState(evt)
            sender() ! currentState.userId
          }
        } else {
          sender() ! Failure(new Exception("Already enabled"))
        }
      }

    case UpdatePassword(PasswordCredentials(_, password)) => state foreach { currentState =>
      if (currentState.enabled) {
        val h = hashProvider.hashPassword(password)
        persist(PasswordChanged(hash = encoder.encodeToString(h.hash), salt = encoder.encodeToString(h.salt))) { evt =>
          updateState(evt)
          sender() ! Done
        }
      } else {
        sender() ! Failure(new Exception("Password can't be changed while it is not enabled"))
      }
    }

    case RemovePassword(_) if isInitialized => persist(PasswordRemoved()) { evt =>
      updateState(evt)
      sender() ! Done
    }

    case cmd: PasswordManagerCommand if !isInitialized =>
      log.warning("Request to login for uninitialized password {}", cmd)
      sender() ! Failure(new Exception("Password does not exist"))
      shardedPassivate()

  }

}


object PasswordManagerImpl {

  case class Settings(hashProvider: HashProvider)

  private def entityExtractor: ShardRegion.ExtractEntityId = {
    case a: PasswordManagerCommand =>
      (a.login, a)
  }

  def start(implicit system: BgClusteringShardingExtension,
            config: Config): PasswordManager = {
    val rounds = config.getInt("bg.auth.provider.hash-rounds")
    startSharded(Settings(new BcryptProvider(rounds, new SecureRandom())))
  }

  def startSharded(settings: Settings)(implicit system: BgClusteringShardingExtension): PasswordManager = {
    val ref = system.start("password-manager", Props(new PasswordManagerImpl(settings.hashProvider)), entityExtractor)
    new PasswordManager {
      val actorTarget: ActorTarget = ref

      import PasswordManagerCommand._

      override def createPassword(userId: UserId, credentials: PasswordCredentials)
                                 (implicit ec: ExecutionContext,
                                  timeout: Timeout,
                                  sender: ActorRef = ActorRef.noSender): Future[Done] =
        actorTarget.?(CreatePassword(userId, credentials)).mapTo[Done]

      override def verifyPassword(credentials: PasswordCredentials)
                                 (implicit ec: ExecutionContext,
                                  timeout: Timeout,
                                  sender: ActorRef = ActorRef.noSender): Future[UserId] =
        actorTarget.?(VerifyPassword(credentials)).mapTo[UserId]

      override def enablePassword(login: String)
                                 (implicit ec: ExecutionContext,
                                  timeout: Timeout,
                                  sender: ActorRef = ActorRef.noSender): Future[UserId] =
        actorTarget.?(EnablePassword(login)).mapTo[UserId]

      override def updatePassword(credentials: PasswordCredentials)
                                 (implicit ec: ExecutionContext,
                                  timeout: Timeout,
                                  sender: ActorRef = ActorRef.noSender): Future[Done] =
        actorTarget.?(UpdatePassword(credentials)).mapTo[Done]

      override def removePassword(login: String)
                                 (implicit ec: ExecutionContext,
                                  timeout: Timeout,
                                  sender: ActorRef = ActorRef.noSender): Future[Done] =
        actorTarget.?(RemovePassword(login)).mapTo[Done]
    }
  }

  trait PasswordManagerCommand {
    def login: String
  }

  object PasswordManagerCommand {

    case class CreatePassword(userId: UserId, credentials: PasswordCredentials) extends PasswordManagerCommand {
      def login: String = credentials.login
    }

    case class VerifyPassword(credentials: PasswordCredentials) extends PasswordManagerCommand {
      def login: String = credentials.login
    }

    case class EnablePassword(login: String) extends PasswordManagerCommand

    case class UpdatePassword(credentials: PasswordCredentials) extends PasswordManagerCommand {
      def login: String = credentials.login
    }

    case class RemovePassword(login: String) extends PasswordManagerCommand

  }

  @JsonInclude(Include.NON_NULL)
  @JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
  @JsonSubTypes(Array(
    new Type(name = "created", value = classOf[PasswordCreated]),
    new Type(name = "enabled", value = classOf[PasswordEnabled]),
    new Type(name = "removed", value = classOf[PasswordRemoved]),
    new Type(name = "changed", value = classOf[PasswordChanged])
  ))
  private[impl] trait PasswordManagerEvent extends JacksonJsonSerializable

  /** Signals successful creation of user
    *
    * @param userId    - uuid generated for user
    * @param hash      - base64 string hash
    * @param salt      - base64 salt
    * @param createdAt - timestamp of creation date
    */
  private[impl] case class PasswordCreated(userId: UserId, hash: String, salt: String, createdAt: Long) extends PasswordManagerEvent

  private[impl] case class PasswordEnabled() extends PasswordManagerEvent

  private[impl] case class PasswordRemoved() extends PasswordManagerEvent

  /** Signals that password has been successfully changed
    *
    * @param hash base64 string hashed password
    * @param salt base64 string salt
    */
  private[impl] case class PasswordChanged(hash: String, salt: String) extends PasswordManagerEvent


  case class PasswordState(
                            hash: HashedValue,
                            userId: UserId,
                            createdAt: Long,
                            enabled: Boolean,
                            updatedCount: Int = 0,
                            initialized: Boolean
                          )

}