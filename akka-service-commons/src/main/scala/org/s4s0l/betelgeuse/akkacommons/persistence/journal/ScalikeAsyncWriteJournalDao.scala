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
