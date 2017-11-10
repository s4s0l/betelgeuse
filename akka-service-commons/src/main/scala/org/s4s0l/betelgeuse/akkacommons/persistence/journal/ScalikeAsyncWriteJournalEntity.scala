package org.s4s0l.betelgeuse.akkacommons.persistence.journal

import java.util.Date

/**
  * @author Marcin Wielgus
  */

trait ScalikeAsyncWriteJournalEntity {
  def getPersistenceIdTag: String

  def getPersistenceUniqueId: String

  def getSequenceNumber: Long

  def getSerializedRepresentation: Array[Byte]

  def getTimestamp: Date
}







