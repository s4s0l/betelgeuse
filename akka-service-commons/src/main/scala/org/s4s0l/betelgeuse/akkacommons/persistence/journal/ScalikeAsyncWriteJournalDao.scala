/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.journal

import akka.persistence.PersistentRepr
import scalikejdbc.DBSession

import scala.collection.immutable.Seq

/**
  * @author Marcin Wielgus
  */
trait ScalikeAsyncWriteJournalDao[T <: ScalikeAsyncWriteJournalEntity] {
  def replayMessages(tag: String, uniqueId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                    (cb: T => Unit)
                    (implicit session: DBSession)

  def createEntity(persistenceIdTag: String,
                   uniqueId: String,
                   sequenceNr: Long,
                   serializedRepr: Array[Byte],
                   representation: PersistentRepr): T

  def save(l: Seq[T])(implicit session: DBSession): Unit

  def getMaxSequenceNumber(persistenceIdTag: String,
                           uniqueId: String, from: Long)(implicit session: DBSession): Long

}
