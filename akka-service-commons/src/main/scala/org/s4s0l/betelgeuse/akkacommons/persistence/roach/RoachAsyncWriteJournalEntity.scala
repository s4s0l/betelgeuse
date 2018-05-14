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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import org.s4s0l.betelgeuse.akkacommons.persistence.journal.ScalikeAsyncWriteJournalEntity
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseEntityObject
import scalikejdbc.WrappedResultSet

/**
  * @author Marcin Wielgus
  */
case class RoachAsyncWriteJournalEntity(
                                         tag: String,
                                         id: String,
                                         seq: Long,
                                         manifest: String,
                                         writerUuid: String,
                                         sender: String,
                                         event: String,
                                         eventClass: String,
                                         deleted: Boolean)

  extends ScalikeAsyncWriteJournalEntity {

  override def getPersistenceIdTag: String = tag

  override def getPersistenceUniqueId: String = id

  override def getSequenceNumber: Long = seq

}

object RoachAsyncWriteJournalEntity extends BetelgeuseEntityObject[RoachAsyncWriteJournalEntity] {

  override def tableName: String = "journal_events"

  override def apply(m: scalikejdbc.ResultName[RoachAsyncWriteJournalEntity])(rs: WrappedResultSet): RoachAsyncWriteJournalEntity = {
    new RoachAsyncWriteJournalEntity(
      rs.string(m.tag),
      rs.string(m.id),
      rs.long(m.seq),
      rs.string(m.manifest),
      rs.string(m.writerUuid),
      rs.string(m.sender),
      rs.string(m.event),
      rs.string(m.eventClass),
      rs.boolean(m.deleted)
    )
  }
}
