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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import java.util.{Base64, Date}

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
                                         serialized: String,
                                         event: Option[AnyRef],
                                         json: Option[String],
                                         created: Option[Date]
                                  )
  extends ScalikeAsyncWriteJournalEntity
{
  override def getPersistenceIdTag: String = tag

  override def getPersistenceUniqueId: String = id

  override def getSequenceNumber: Long = seq

  private lazy val decodedRepr: Array[Byte] = Base64.getDecoder.decode(serialized)

  override def getSerializedRepresentation: Array[Byte] = decodedRepr

  override def getTimestamp: Date = created.get


}

object RoachAsyncWriteJournalEntity extends BetelgeuseEntityObject[RoachAsyncWriteJournalEntity]{

  override def apply(m: scalikejdbc.ResultName[RoachAsyncWriteJournalEntity])(rs: WrappedResultSet):RoachAsyncWriteJournalEntity = {
      new RoachAsyncWriteJournalEntity(
        rs.string(m.tag),
        rs.string(m.id),
        rs.long(m.seq),
        rs.string(m.serialized),
        None,
        rs.stringOpt(m.json),
        rs.dateOpt(m.created)
      )
  }
}
