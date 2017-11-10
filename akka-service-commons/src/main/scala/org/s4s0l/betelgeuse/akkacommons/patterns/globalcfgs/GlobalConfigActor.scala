/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-16 02:12
 *
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
