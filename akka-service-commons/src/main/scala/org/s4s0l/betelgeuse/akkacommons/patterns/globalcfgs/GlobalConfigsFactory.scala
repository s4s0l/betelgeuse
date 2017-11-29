/*
 * Copyright© 2017 the original author or authors.
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

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaServiceId
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

  def eventPublisherRemote[T](clientName: BetelgeuseAkkaServiceId, ext: BetelgeuseAkkaClusteringClientExtension, configName: String)
                             (implicit timeout: Timeout, sender: ActorRef = Actor.noSender): GlobalConfigsEventPublisher[T] = (id: String, value: T, uuid: String) => {
    val name = getGlobalConfigName(configName)
    val evt = ConfigurationChanged(uuid, PersistenceId(name, id), value)
    ext.client(clientName).question("/globalcfgs/" + name, evt).mapTo[ConfigurationChangedAck]
  }

}

object GlobalConfigsFactory extends GlobalConfigsFactory {


  trait GlobalConfigsEventPublisher[T] {
    def emitChange(id: String, msg: T, uuid: String = UUID.randomUUID().toString): Future[ConfigurationChangedAck]
  }

}