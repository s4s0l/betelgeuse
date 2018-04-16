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

import org.s4s0l.betelgeuse.akkacommons.clustering.receptionist.BgClusteringReceptionist
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.DistributedSharedState.SatelliteContext
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteStateActor.HandlerResult
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.BgPersistenceJournal

import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */
trait BgSatelliteStateService {
  this: BgPersistenceJournal
    with BgClusteringReceptionist
    with BgClusteringSharding =>

  def createSatelliteStateFactory[I, V](name: String, handler: (I) => HandlerResult[V])
                                       (implicit classTag: ClassTag[I])
  : SatelliteContext[I, V] = {
    DistributedSharedState.createSatelliteStateDistribution(name, handler)
  }

  def createSimpleSatelliteStateFactory[I](name: String)
                                          (implicit classTag: ClassTag[I])
  : SatelliteContext[I, I] = {
    DistributedSharedState.createSatelliteStateDistribution(name, it => Left(Some(it)))
  }
}
