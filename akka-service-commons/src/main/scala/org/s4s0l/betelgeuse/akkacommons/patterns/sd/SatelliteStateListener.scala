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

package org.s4s0l.betelgeuse.akkacommons.patterns.sd

import akka.actor.ActorRef
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteStateListener._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.utils.QA.{NotOkNullResult, NullResult, OkNullResult, Question}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait SatelliteStateListener[V] {
  def configurationChanged(msg: StateChanged[V])
                          (implicit executionContext: ExecutionContext, sender: ActorRef = ActorRef.noSender)
  : Future[StateChangedResult]
}

object SatelliteStateListener {

  sealed trait StateChangedResult extends NullResult[VersionedId]

  case class StateChanged[V](messageId: VersionedId, value: V, expDuration: FiniteDuration) extends Question[VersionedId]

  case class StateChangedOk(correlationId: VersionedId) extends StateChangedResult with OkNullResult[VersionedId]

  case class StateChangedNotOk(correlationId: VersionedId, ex: Throwable) extends StateChangedResult with NotOkNullResult[VersionedId]

}

