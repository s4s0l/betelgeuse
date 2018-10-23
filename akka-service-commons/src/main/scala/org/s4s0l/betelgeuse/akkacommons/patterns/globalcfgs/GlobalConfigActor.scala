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



package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs

import akka.actor.{Actor, ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigActor.{ConfigValue, GetConfig, GlobalConfigActorSettings}
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigSupervisorActor.{ConfigurationChangedInternal, ConfigurationChangedInternalAck}
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId

/**
  *
  * TODO this actor should handle persistence by itself as it
  * will be running on all nodes and persistenceId and seq will repeat by design
  *
  * @author Marcin Wielgus
  */
class GlobalConfigActor[X, T](settings: GlobalConfigActorSettings[X, T], persistenceIdent: PersistenceId)
  extends PersistentActor with ActorLogging {

  var currentValue: Option[T] = None
  var lastInputValue: Option[X] = None

  override def receiveRecover: Actor.Receive = {
    case RecoveryCompleted =>
      log.debug(s"Recovery completed for $persistenceIdent")
      try {
        currentValue = lastInputValue.map(settings.mapper)

      } catch {
        case f: Throwable =>
          log.error(f, "unable to restore!!!")
          currentValue = None
      }
    case a =>
      lastInputValue = Some(a.asInstanceOf[X])
  }


  private def setValue(a: Any): Unit = {
    val incomingX = a.asInstanceOf[X]
    if (!lastInputValue.contains(incomingX)) {
      lastInputValue = Some(incomingX)
      currentValue = Some(settings.mapper.apply(incomingX))
      log.debug(s"Recreated current value of $persistenceIdent")
    }
  }


  override def receiveCommand: Actor.Receive = {
    case _: GetConfig =>
      sender() ! ConfigValue(currentValue)
    case ConfigurationChangedInternal(uuid, originator, pid, v) if pid == persistenceIdent =>
      log.debug(s"Got ConfigurationChangedInternal uuid=$uuid, pid=$pid, originator=$originator")
      val sndr = sender()
      persist(v) { x =>
        log.debug("Persisted...")
        setValue(x)
        sndr ! ConfigurationChangedInternalAck(uuid, originator)
      }
  }

  override def persistenceId: String = persistenceIdent.toString
}


object GlobalConfigActor {
  def props[X, T](settings: GlobalConfigActorSettings[X, T], persistenceIdent: PersistenceId): Props =
    Props(new GlobalConfigActor[X, T](settings, persistenceIdent))

  case class GetConfig(persistenceId: PersistenceId)

  case class ConfigValue[T](value: Option[T])

  case class GlobalConfigActorSettings[X, T](mapper: X => T)

}
