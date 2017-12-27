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


package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigActor.{ConfigValue, GetConfig, GlobalConfigActorSettings}
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigSupervisorActor._
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JournalReader, PersistenceId}
import org.s4s0l.betelgeuse.akkacommons.utils.PubSubWithDefaultMediator
import org.s4s0l.betelgeuse.utils.AllUtils

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * za zadanie ma po starcie odtworzyć
  * wszystkich persystentnych aktorów z konfiguracją
  * oraz routowac komunikaty do odpowiednich aktorów
  *
  * @author Marcin Wielgus
  */
class GlobalConfigSupervisorActor[X, T](configName: String,
                                        settings: GlobalConfigActorSettings[X, T],
                                        globalConfigQueryFacade: JournalReader,
                                        pubSub: PubSubWithDefaultMediator) extends Actor with ActorLogging {
  private var children = Map[PersistenceId, ActorRef]()
  private implicit val execCtx: ExecutionContextExecutor = context.dispatcher

  override def preStart(): Unit = {
    log.debug(s"Pre starting GlobalConfigSupervisor $configName...")
    pubSub.subscribe("/topic/" + configName, self)
    pubSub.registerByName("/globalcfgs/" + configName, self)
    //todo should be async!
    val ids = Await.result(globalConfigQueryFacade.allActorsAsync(configName), 10 second)
    ids.foreach { pid =>
      startChild(pid)
    }
    log.debug(s"Pre starting GlobalConfigSupervisor $configName done.")
  }

  private def startChild(pid: PersistenceId): ActorRef = {
    val ref = context.actorOf(GlobalConfigActor.props(settings, pid), pid.toString.replace("/", "-"))
    children = children + (pid -> ref)
    context.watchWith(ref, TerminatedChild(ref, pid))
    ref
  }

  override def receive: Actor.Receive = {
    case gc@GetConfig(pid) =>
      children.getOrElse(pid, startChild(pid)).tell(gc, sender())
    case TerminatedChild(_, pid) =>
      children = children - pid
    case ConfigurationAwaitRestore =>
      log.debug(s"Got ConfigurationAwaitRestore in config=$configName")
      import akka.pattern._

      import scala.concurrent.duration._
      implicit val timeout: Timeout = Timeout(10 seconds)
      val allFutures = children.map {
        child =>
          (child._2 ? GetConfig(child._1)).map(_.asInstanceOf[ConfigValue[_]])
      }.toSeq
      val sndr = sender()
      AllUtils.listOfFuturesToFutureOfList(allFutures).andThen {
        case Success(_) =>
          sndr ! ConfigurationRestored
          log.debug(s"Config seems to be restored, config=$configName")
        case Failure(f) => log.error(f, s"Unable to wait for config $configName to be fully restored")
      }
    case ConfigurationChanged(uuid, pid, v) =>
      log.debug(s"Got ConfigurationChanged in config=$configName, uuid=$uuid, pid=$pid")
      pubSub.publish("/topic/" + configName, ConfigurationChangedInternal(uuid, sender(), pid, v))(self)
      unAckedUuids = uuid :: unAckedUuids
    case cc@ConfigurationChangedInternal(uuid, _, pid, _) =>
      log.debug(s"Got ConfigurationChangedInternal in config=$configName, uuid=$uuid, pid=$pid")
      children.getOrElse(pid, startChild(pid)) ! cc
    case ConfigurationChangedInternalAck(uuid, originator) =>
      log.debug(s"Got ConfigurationChangedInternalAck in config=$configName, uuid=$uuid, from=$originator ($unAckedUuids)")
      if (unAckedUuids.contains(uuid)) {
        unAckedUuids = unAckedUuids.filter(_ != uuid)
        originator ! ConfigurationChangedAck(uuid)
      }
  }

  private var unAckedUuids = List[String]()

}

object GlobalConfigSupervisorActor {
  def props[X, T](configName: String,
                  settings: GlobalConfigActorSettings[X, T],
                  globalConfigQueryFacade: JournalReader,
                  pubSub: PubSubWithDefaultMediator): Props = {
    Props(new GlobalConfigSupervisorActor(configName, settings, globalConfigQueryFacade, pubSub))
  }

  private case class TerminatedChild(ref: ActorRef, pid: PersistenceId)

  case class ConfigurationChangedInternal(uuid: String, initiator: ActorRef, persistenceId: PersistenceId, config: Any)

  case class ConfigurationChangedInternalAck(uuid: String, initiator: ActorRef)

  case class ConfigurationChanged(uuid: String, persistenceId: PersistenceId, config: Any)

  case class ConfigurationChangedAck(uuid: String)

  case object ConfigurationRestored

  case object ConfigurationAwaitRestore

}