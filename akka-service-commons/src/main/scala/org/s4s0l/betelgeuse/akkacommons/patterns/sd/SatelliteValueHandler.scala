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

import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateDistributor.Protocol.ValidationError
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.SatelliteValueHandler.HandlerResult
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
  * @author Marcin Wielgus
  */
trait SatelliteValueHandler[I, V] {
  def handle(versionedId: VersionedId, input: I)
            (implicit executionContext: ExecutionContext): Future[HandlerResult[V]]
}


object SatelliteValueHandler {
  type HandlerResult[V] = Either[Option[V], ValidationError]

  implicit def simple[I, V](func: I => HandlerResult[V]): SatelliteValueHandler[I, V] = new SatelliteValueHandler[I, V] {
    override def handle(versionedId: VersionedId, input: I)
                       (implicit executionContext: ExecutionContext)
    : Future[HandlerResult[V]] = Future {
      func(input)
    }
  }

  implicit def identity[I](): SatelliteValueHandler[I, I] = new SatelliteValueHandler[I, I] {
    override def handle(versionedId: VersionedId, input: I)
                       (implicit executionContext: ExecutionContext)
    : Future[HandlerResult[I]] = Future {
      Left(Some(input))
    }
  }
}
