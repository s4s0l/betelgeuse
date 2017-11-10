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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.util.{Base64, Date}

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.AnyRefObject._
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.ScalikeAsyncWriteJournalEntity
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.BetelgeuseEntityObject
import scalikejdbc.WrappedResultSet
/**
  * @author Marcin Wielgus
  */
case class CrateAsyncWriteJournalEntity(
                                         tag: String,
                                         id: String,
                                         seq: Long,
                                         serialized: String,
                                         event: Option[AnyRefObject],
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

object CrateAsyncWriteJournalEntity extends BetelgeuseEntityObject[CrateAsyncWriteJournalEntity]{

  override def apply(m: scalikejdbc.ResultName[CrateAsyncWriteJournalEntity])(rs: WrappedResultSet):CrateAsyncWriteJournalEntity = {
      new CrateAsyncWriteJournalEntity(
        rs.string(m.tag),
        rs.string(m.id),
        rs.long(m.seq),
        rs.string(m.serialized),
        rs.get[Option[AnyRefObject]](m.event),
        rs.stringOpt(m.json),
        rs.dateOpt(m.created)
      )
  }
}
