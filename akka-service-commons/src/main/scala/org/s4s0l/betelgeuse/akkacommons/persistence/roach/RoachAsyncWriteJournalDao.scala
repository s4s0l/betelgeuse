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

import java.nio.charset.Charset
import java.util.Base64

import akka.persistence.PersistentRepr
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.PostgresScalikeJdbcImports._
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.ScalikeAsyncWriteJournalDao
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer}
import org.slf4j.LoggerFactory
import scalikejdbc._

import scala.collection.immutable

/**
  * @author Marcin Wielgus
  */
class RoachAsyncWriteJournalDao(serialization: Option[JacksonJsonSerializer])
  extends ScalikeAsyncWriteJournalDao[RoachAsyncWriteJournalEntity] {

  private val e = RoachAsyncWriteJournalEntity.syntax("e")
  private val column = RoachAsyncWriteJournalEntity.column


  override def createEntity(persistenceIdTag: String, uniqueId: String,
                            sequenceNr: Long, serializedRepr: Array[Byte],
                            representation: PersistentRepr): RoachAsyncWriteJournalEntity = {
    val representationEncoded = Base64.getEncoder.encodeToString(serializedRepr)
    val crateObject = None // Roach doesnt support object type
    val jsonObject = toJson(representation)
    new RoachAsyncWriteJournalEntity(persistenceIdTag, uniqueId,
      sequenceNr,
      representationEncoded,
      crateObject,
      jsonObject,
      None
    )
  }


  def toJson(p: PersistentRepr): Option[String] = {
    serialization
      .find(_ => classOf[JacksonJsonSerializable].isAssignableFrom(p.payload.getClass))
      .map(serializetion => serializetion.toBinary(p.payload.asInstanceOf[AnyRef]))
      .map(bytes => new String(bytes, Charset.forName("UTF-8")))
  }

  private val LOGGER = LoggerFactory.getLogger(getClass)

  override def replayMessages(tag: String, uniqueId: String, fromSequenceNr: Long,
                              toSequenceNr: Long, max: Long)(cb: (RoachAsyncWriteJournalEntity) => Unit)
                             (implicit session: DBSession): Unit = {
    LOGGER.info(s"Replaying $tag $uniqueId $fromSequenceNr $toSequenceNr $max")
    withSQL {
      select.from(RoachAsyncWriteJournalEntity as e)
        .where
        .eq(e.tag, tag).and
        .eq(e.id, uniqueId).and
        .ge(e.seq, fromSequenceNr).and
        .le(e.seq, toSequenceNr)
        .orderBy(e.seq)
      //TODO: loop through max with limit...
      //        .limit(100)
    }.foreach { rs =>
      val entity = RoachAsyncWriteJournalEntity.apply(e.resultName)(rs)
      cb.apply(entity)
    }
  }

  override def save(l: immutable.Seq[RoachAsyncWriteJournalEntity])(implicit session: DBSession): Unit = {
    if (l.size == 1) {
      val e = l.head
      withSQL {
        insert.into(RoachAsyncWriteJournalEntity)
          .namedValues(
            column.tag -> e.tag,
            column.id -> e.id,
            column.seq -> e.seq,
            column.serialized -> e.serialized,
            column.json -> e.json.orNull
          )
      }.update().apply()
    } else {
      withSQL {
        insert.into(RoachAsyncWriteJournalEntity)
          .namedValues(
            column.tag -> sqls.?,
            column.id -> sqls.?,
            column.seq -> sqls.?,
            column.serialized -> sqls.?,
            column.json -> sqls.?
          )
      }.batch(l.map { e =>
        Seq(e.tag, e.id, e.seq, e.serialized, e.json.orNull)
      }: _*).apply()
    }
  }


  def deleteUpTo(tag: String, id: String, toSeqNum: Long)(implicit session: DBSession): Int = {
    val maxSeq = getMaxSequenceNumber(tag, id, -1)
    if (maxSeq >= toSeqNum) {
      withSQL {
        delete.from(RoachAsyncWriteJournalEntity)
          .where.eq(RoachAsyncWriteJournalEntity.column.id, id)
          .and.eq(RoachAsyncWriteJournalEntity.column.tag, tag)
          .and.lt(RoachAsyncWriteJournalEntity.column.seq, toSeqNum)
      }.update.apply()
    }
    else {
      throw new Exception(s"You tried to delete all events for tag: $tag and id $id. Max seq: $maxSeq and you tried to delete up to $toSeqNum")
    }
  }


  override def getMaxSequenceNumber(tag: String, id: String, from: Long)(implicit session: DBSession): Long = {
    val sql = sql"select max(seq) from ${RoachAsyncWriteJournalEntity.table} where tag = $tag and id = $id and seq >= $from"

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

}
