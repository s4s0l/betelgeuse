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

package org.s4s0l.betelgeuse.akkacommons.clustering.client

import akka.actor.{ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.pattern.ask
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.BgServiceId
import org.s4s0l.betelgeuse.akkacommons.clustering.client.ClusterClientsSupervisor.{AllReferencesMessage, GetAllReferencesMessage}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class BgClusteringClientExtension(private val system: ExtendedActorSystem) extends Extension {

  private val clientActors: Map[String, ActorRef] = {
    implicit val timeout: Timeout = 45 seconds
    val asking = (system.actorOf(ClusterClientsSupervisor.props(), "cluster-client-supervisor") ? GetAllReferencesMessage).mapTo[AllReferencesMessage]
    Await.result(asking, 45 seconds).references
  }

  def client(id: BgServiceId): ClusterClientTarget = new ClusterClientTarget(clientActors(id.systemName), system)

}

object BgClusteringClientExtension extends ExtensionId[BgClusteringClientExtension] with ExtensionIdProvider {

  override def get(system: ActorSystem): BgClusteringClientExtension = system.extension(this)

  override def apply(system: ActorSystem): BgClusteringClientExtension = system.extension(this)

  override def lookup(): BgClusteringClientExtension.type = BgClusteringClientExtension

  override def createExtension(system: ExtendedActorSystem): BgClusteringClientExtension =
    new BgClusteringClientExtension(system)
}
