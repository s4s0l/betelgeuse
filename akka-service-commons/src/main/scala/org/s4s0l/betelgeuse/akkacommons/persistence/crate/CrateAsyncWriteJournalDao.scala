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


package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports._
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.ScalikeAsyncWriteJournalDao
import org.slf4j.LoggerFactory
import scalikejdbc._

import scala.collection.immutable

/**
  * @author Marcin Wielgus
  */
class CrateAsyncWriteJournalDao()
  extends ScalikeAsyncWriteJournalDao[CrateAsyncWriteJournalEntity] {

  private val e = CrateAsyncWriteJournalEntity.syntax("e")
  private val column = CrateAsyncWriteJournalEntity.column


  private val LOGGER = LoggerFactory.getLogger(getClass)

  override def deleteUpTo(tag: String, id: String, toSeqNum: Long)
                         (implicit session: DBSession): Int = {
    new UnsupportedOperationException("Deletion of events from journal is unsupported in crate")
  }

  override def replayMessages(tag: String, uniqueId: String, fromSequenceNr: Long,
                              toSequenceNr: Long, max: Long)
                             (cb: CrateAsyncWriteJournalEntity => Unit)
                             (implicit session: DBSession)
  : Unit = {
    LOGGER.info(s"Replaying $tag $uniqueId $fromSequenceNr $toSequenceNr $max")
    withSQL {
      select.from(CrateAsyncWriteJournalEntity as e)
        .where
        .eq(e.tag, tag).and
        .eq(e.id, uniqueId).and
        .ge(e.seq, fromSequenceNr).and
        .le(e.seq, toSequenceNr)
        .orderBy(e.seq)
      //TODO: loop through max with limit...
      //        .limit(100)
    }.foreach { rs =>
      val entity = CrateAsyncWriteJournalEntity.apply(e.resultName)(rs)

      cb.apply(entity)
    }
  }

  override def save(l: immutable.Seq[CrateAsyncWriteJournalEntity])(implicit session: DBSession): Unit = {
    if (l.size == 1) {
      val e = l.head
      withSQL {
        insert.into(CrateAsyncWriteJournalEntity)
          .namedValues(
            column.tag -> e.tag,
            column.id -> e.id,
            column.seq -> e.seq,
            column.serialized -> e.serialized,
            column.event -> e.event.map { x => param(x) }.orNull,
            column.json -> e.json.orNull
          )
      }.update().apply()
    } else {
      withSQL {
        insert.into(CrateAsyncWriteJournalEntity)
          .namedValues(
            column.tag -> sqls.?,
            column.id -> sqls.?,
            column.seq -> sqls.?,
            column.serialized -> sqls.?,
            column.event -> sqls.?,
            column.json -> sqls.?
          )
      }.batch(l.map { e =>
        Seq(e.tag, e.id, e.seq, e.serialized, e.event.map { x => param(x) }.orNull, e.json.orNull)
      }: _*).apply()
    }
  }

  override def getMaxSequenceNumber(tag: String, id: String, from: Long)(implicit session: DBSession): Long = {
    val sql = sql"select max(seq) from ${CrateAsyncWriteJournalEntity.table} where tag = $tag and id = $id and seq >= $from"

    val v: Option[Option[Long]] = sql
      .map(r => r.longOpt(1))
      .single()
      .apply()

    val xx: Long = v.map {
      case None => Some(0L)
      case a => a
    }.getOrElse(Some(0L)).get

    Math.max(from, xx)
  }


  def refreshTable(implicit session: DBSession): Unit = {
    sql"refresh table ${CrateAsyncWriteJournalEntity.table}".execute().apply()
  }
}
