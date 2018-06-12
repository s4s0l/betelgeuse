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

package org.s4s0l.betelgeuse.akkacommons.distsharedstate

import org.s4s0l.betelgeuse.akkacommons.BgServiceId
import org.s4s0l.betelgeuse.akkacommons.clustering.client.BgClusteringClient
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.{OriginStateActor, OriginStateDistributor, SatelliteProtocol}
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId

import scala.concurrent.duration.{FiniteDuration, _}

/**
  * @author Marcin Wielgus
  */
trait BgOriginStateService
  extends BgClusteringSharding
    with BgClusteringClient {


  def createOriginState[I <: AnyRef](name: String,
                                     distributor: OriginStateDistributor.Protocol[I],
                                     validator: (VersionedId, I) => I = (_: VersionedId, a: I) => a,
                                     stateDistributionRetryInterval: FiniteDuration = 30.seconds)
  : OriginStateActor.Protocol[I] = {
    OriginStateActor.startSharded(OriginStateActor.Settings(
      name, distributor, validator, stateDistributionRetryInterval))
  }

  def createLocalDistribution[I <: AnyRef](name: String, satelliteStates: Map[String, SatelliteProtocol[I]])
  : OriginStateDistributor.Protocol[I] = {
    OriginStateDistributor.start(OriginStateDistributor.Settings(name, satelliteStates))
  }

  def createRemoteDistribution[I <: AnyRef](name: String, services: Seq[BgServiceId])
  : OriginStateDistributor.Protocol[I] = {
    OriginStateDistributor.startRemote(name, services)
  }


}
