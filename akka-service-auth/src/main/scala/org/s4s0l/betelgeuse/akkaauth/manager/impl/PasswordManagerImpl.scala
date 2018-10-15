package org.s4s0l.betelgeuse.akkaauth.manager.impl

import akka.Done
import akka.actor.Props
import akka.actor.Status.Failure
import akka.cluster.sharding.ShardRegion
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo.{As, Id}
import com.fasterxml.jackson.annotation.{JsonInclude, JsonSubTypes, JsonTypeInfo}
import org.s4s0l.betelgeuse.akkaauth.common.{PasswordCredentials, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.{HashProvider, PasswordManager}
import org.s4s0l.betelgeuse.akkaauth.manager.impl.PasswordManagerImpl.PasswordManagerCommand._
import org.s4s0l.betelgeuse.akkaauth.manager.impl.PasswordManagerImpl.{PasswordManagerCommand, _}
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringShardingExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.PersistentShardedActor
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.akkacommons.utils.ActorTarget

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps


class PasswordManagerImpl(hashProvider: HashProvider) extends PersistentShardedActor {

  var state: Option[PasswordState] = None

  private def isInitialized: Boolean = state isDefined

  def updateState(event: PasswordManagerEvent): Unit = {
    event match {
      case PasswordCreated(userId, hash, createdAt) =>
        state = Some(PasswordState(hash, userId, createdAt, enabled = false, 1, initialized = true))
      case PasswordEnabled() if isInitialized =>
        state = state.map(_.copy(enabled = true))
      case PasswordChanged(newHash) if isInitialized =>
        state = state.map { s =>
          s.copy(hash = newHash, updatedCount = s.updatedCount + 1)
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
        case Some(currentState) => sender() ! Failure(new Exception("Password already initialized"))
        case None =>
          persist(PasswordCreated(userId, hashProvider.hashPassword(password), System.currentTimeMillis())) { event =>
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
        persist(PasswordChanged(hashProvider.hashPassword(password))) { evt =>
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

  }

}


object PasswordManagerImpl {

  case class Settings(hashProvider: HashProvider, askTimeout: Timeout)

  private def entityExtractor: ShardRegion.ExtractEntityId = {
    case a: PasswordManagerCommand =>
      (a.login, a)
  }

  def startSharded(settings: Settings)(implicit system: BgClusteringShardingExtension): PasswordManager = {
    val ref = system.start("password-manager", Props(new PasswordManagerImpl(settings.hashProvider)), entityExtractor)
    new PasswordManager {
      val actorTarget: ActorTarget = ref

      implicit val timeout: Timeout = settings.askTimeout

      import PasswordManagerCommand._

      override def createPassword(userId: UserId, credentials: PasswordCredentials)(implicit ec: ExecutionContext): Future[Done] =
        actorTarget.?(CreatePassword(userId, credentials)).mapTo[Done]

      override def verifyPassword(credentials: PasswordCredentials)(implicit ec: ExecutionContext): Future[UserId] =
        actorTarget.?(VerifyPassword(credentials)).mapTo[UserId]

      override def enablePassword(login: String)(implicit ec: ExecutionContext): Future[UserId] =
        actorTarget.?(EnablePassword(login)).mapTo[UserId]

      override def updatePassword(credentials: PasswordCredentials)(implicit ec: ExecutionContext): Future[Done] =
        actorTarget.?(UpdatePassword(credentials)).mapTo[Done]

      override def removePassword(login: String)(implicit ec: ExecutionContext): Future[Done] =
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

  private[impl] case class PasswordCreated(userId: UserId, hash: String, createdAt: Long) extends PasswordManagerEvent

  private[impl] case class PasswordEnabled() extends PasswordManagerEvent

  private[impl] case class PasswordRemoved() extends PasswordManagerEvent

  private[impl] case class PasswordChanged(hash: String) extends PasswordManagerEvent


  case class PasswordState(
                            hash: String,
                            userId: UserId,
                            createdat: Long,
                            enabled: Boolean,
                            updatedCount: Int = 0,
                            initialized: Boolean
                          )

}