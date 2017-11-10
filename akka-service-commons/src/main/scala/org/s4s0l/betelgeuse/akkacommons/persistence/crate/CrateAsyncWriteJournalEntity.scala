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
