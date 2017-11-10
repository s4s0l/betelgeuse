/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 03:07
 *
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BetelgeuseAkkaClusteringClientExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigActor.GlobalConfigActorSettings
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigSupervisorActor.{ConfigurationChanged, ConfigurationChangedAck}
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigsFactory.GlobalConfigsEventPublisher
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId
import org.s4s0l.betelgeuse.akkacommons.utils.PubSubWithDefaultMediator

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
trait GlobalConfigsFactory {

  def createGlobalConfig[X, T](configName: String, globalConfigQueryFacade: GlobalConfigQueryFacade,
                               pubSub: PubSubWithDefaultMediator, mapper: X => T = (it: X) => it.asInstanceOf[T])
                              (implicit actorSystem: ActorSystem): GlobalConfig[T] = {
    val configPersistenceTag = getGlobalConfigName(configName)
    val supervisorName = s"$configPersistenceTag-supervisor"
    val setts = GlobalConfigActorSettings[X, T](mapper)
    val supervisor = actorSystem.actorOf(GlobalConfigSupervisorActor.props(configPersistenceTag, setts, globalConfigQueryFacade, pubSub), supervisorName)
    new GlobalConfig[T](configPersistenceTag, supervisor)
  }

  private def getGlobalConfigName(configName: String): String = {
    s"globalcfgs-$configName"
  }


  def eventPublisher[T](pubSub: PubSubWithDefaultMediator, configName: String)
                       (implicit timeout: Timeout, sender: ActorRef = Actor.noSender): GlobalConfigsEventPublisher[T] = (id: String, value: T, uuid: String) => {
    val name = getGlobalConfigName(configName)
    val evt = ConfigurationChanged(uuid, PersistenceId(name, id), value)
    pubSub.question("/globalcfgs/" + name, evt).mapTo[ConfigurationChangedAck]
  }

  def eventPublisherRemote[T](clientName: String, ext: BetelgeuseAkkaClusteringClientExtension, configName: String)
                       (implicit timeout: Timeout, sender: ActorRef = Actor.noSender): GlobalConfigsEventPublisher[T] = (id: String, value: T, uuid: String) => {
    val name = getGlobalConfigName(configName)
    val evt = ConfigurationChanged(uuid, PersistenceId(name, id), value)
    ext.question(clientName, "/globalcfgs/" + name, evt).mapTo[ConfigurationChangedAck]
  }

}

object GlobalConfigsFactory extends GlobalConfigsFactory {


  trait GlobalConfigsEventPublisher[T] {
    def emitChange(id: String, msg: T, uuid: String = UUID.randomUUID().toString): Future[ConfigurationChangedAck]
  }

}