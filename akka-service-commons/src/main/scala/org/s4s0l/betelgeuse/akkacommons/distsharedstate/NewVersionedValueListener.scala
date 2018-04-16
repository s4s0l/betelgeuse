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

import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.distsharedstate.NewVersionedValueListener._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.utils.QA.{NotOkNullResult, NullResult, OkNullResult}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */

trait NewVersionedValueListener[R] {
  def onNewVersionAsk(versionedId: VersionedId, aValue: R)
                     (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
  : Future[NewVersionResult]
}

object NewVersionedValueListener {

  def adaptListener[R, V](listener: NewVersionedValueListener[R])
                         (f: (VersionedId, V, ExecutionContext, ActorRef, Timeout) => Future[R])
  : NewVersionedValueListener[V] =
    new NewVersionedValueListener[V] {
      override def onNewVersionAsk(versionedId: VersionedId, aValue: V)
                                  (implicit executionContext: ExecutionContext,
                                   sender: ActorRef, timeout: Timeout)
      : Future[NewVersionResult] = {
        f(versionedId, aValue, executionContext, sender, timeout)
          .flatMap(listener.onNewVersionAsk(versionedId, _))
          .map {
            case NewVersionedValueListener.NewVersionOk(_) => NewVersionedValueListener.NewVersionOk(versionedId)
            case NewVersionedValueListener.NewVersionNotOk(_, ex) => NewVersionedValueListener.NewVersionNotOk(versionedId, ex)
          }
          .recover { case ex: Throwable => NewVersionedValueListener.NewVersionNotOk(versionedId, ex) }
      }
    }


  sealed trait NewVersionResult extends NullResult[VersionedId]

  case class NewVersionOk(correlationId: VersionedId) extends NewVersionResult with OkNullResult[VersionedId]

  case class NewVersionNotOk(correlationId: VersionedId, ex: Throwable) extends NewVersionResult with NotOkNullResult[VersionedId]

}


