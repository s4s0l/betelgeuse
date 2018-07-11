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

import org.s4s0l.betelgeuse.akkacommons.persistence.journal.ScalikeAsyncWriteJournalDao
import org.slf4j.LoggerFactory
import scalikejdbc._

import scala.collection.immutable

/**
  * @author Marcin Wielgus
  */
class RoachAsyncSingleWriteJournalDao() extends
  ScalikeAsyncWriteJournalDao[RoachAsyncWriteJournalEntity] {

  private val e = RoachAsyncSingleWriteJournalEntity.syntax("e")
  private val column = RoachAsyncSingleWriteJournalEntity.column
  private val LOGGER = LoggerFactory.getLogger(getClass)

  override def replayMessages(tag: String, uniqueId: String, fromSequenceNr: Long,
                              toSequenceNr: Long, max: Long)
                             (cb: RoachAsyncWriteJournalEntity => Unit)
                             (implicit session: DBSession): Unit = {
    LOGGER.debug(s"Replaying $tag $uniqueId $fromSequenceNr $toSequenceNr $max")
    withSQL {
      select.from(RoachAsyncSingleWriteJournalEntity as e)
        .where
        .eq(e.tag, tag).and
        .eq(e.id, uniqueId)
    }.map(RoachAsyncSingleWriteJournalEntity(e.resultName))
      .single()
      .apply()
      .foreach(cb)
  }

  override def save(l: immutable.Seq[RoachAsyncWriteJournalEntity])
                   (implicit session: DBSession): Unit = {

    l.foreach(entity => {
      if (entity.seq == 1) {
        withSQL {
          insert.into(RoachAsyncSingleWriteJournalEntity)
            .namedValues(
              column.tag -> entity.tag,
              column.id -> entity.id,
              column.seq -> entity.seq,
              column.manifest -> entity.manifest,
              column.writerUuid -> entity.writerUuid,
              column.event -> entity.event,
              column.eventClass -> entity.eventClass,
              column.deleted -> entity.deleted
            )
        }.update().apply()
      } else {
        val updatedRecords = withSQL {
          update(RoachAsyncSingleWriteJournalEntity).set(
            column.seq -> entity.seq,
            column.manifest -> entity.manifest,
            column.writerUuid -> entity.writerUuid,
            column.event -> entity.event,
            column.eventClass -> entity.eventClass,
            column.deleted -> entity.deleted
          ).where.eq(column.seq, entity.seq - 1)
            .and.eq(column.id, entity.id)
            .and.eq(column.tag, entity.tag)
        }.update().apply()
        if (updatedRecords != 1) {
          throw new Exception(s"Single write entity seems to have skipped some events or we are facing split brain for ${entity.tag}/${entity.id} seq was ${entity.seq}")
        }
      }
    })
  }


  override def deleteUpTo(tag: String, id: String, toSeqNum: Long)
                         (implicit session: DBSession): Int = {
    withSQL {
      delete.from(RoachAsyncSingleWriteJournalEntity)
        .where.eq(RoachAsyncSingleWriteJournalEntity.column.id, id)
        .and.eq(RoachAsyncSingleWriteJournalEntity.column.tag, tag)
        .and.lt(RoachAsyncSingleWriteJournalEntity.column.seq, toSeqNum)
    }.update.apply()
  }

  def getMaxSequenceNumber(tag: String, id: String, from: Long)
                          (implicit session: DBSession): Long = {
    withSQL {
      select(e.result.seq).from(RoachAsyncSingleWriteJournalEntity as e)
        .where
        .eq(e.tag, tag).and
        .eq(e.id, id)
    }
      .map(rs => rs.long(e.resultName.seq))
      .single().apply() match {
      case None => 0L
      case Some(s) => s
    }
  }

}
