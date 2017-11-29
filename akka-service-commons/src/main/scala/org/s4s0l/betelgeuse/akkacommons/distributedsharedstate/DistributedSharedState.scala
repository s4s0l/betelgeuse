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

package org.s4s0l.betelgeuse.akkacommons.distributedsharedstate

import akka.actor.ActorRefFactory
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaServiceId
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BetelgeuseAkkaClusteringClientExtension
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStateDistributor.{Protocol, Settings}
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.{OriginStateDistributor, SatelliteStateActor}

/**
  * @author Marcin Wielgus
  */
object DistributedSharedState {
  /**
    * Creates an api to be used by origin configuration holder aggregate. Using returned
    * api it can broadcast versioned value to remote services and reliably receive confirmations
    * without tracking state.
    *
    * @param name     the name must be same at origin and at satellite ends
    * @param services list of remote services to which changes will be distributed
    */
  def createStateDistributionToRemoteServices[T](name: String, services: Seq[BetelgeuseAkkaServiceId])
                                                (implicit clientExt: BetelgeuseAkkaClusteringClientExtension,
                                                 actorRefFactory: ActorRefFactory): Protocol[T] = {
    val satellites: Map[String, SatelliteStateActor.Protocol[T]] = services.map { it =>
      it.systemName -> SatelliteStateActor.Protocol[T](clientExt.client(it).toActorTarget(s"/satellite-state-$name"))
    }.toMap
    OriginStateDistributor.start(Settings(name, satellites))
  }


  //  def createStateDistributionSatellite[T](name: String)

}
