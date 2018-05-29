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

import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbAccess
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializer, SimpleSerializer}
import scalikejdbc._

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * @author Marcin Wielgus
  */
class RoachSnapshotStore extends SnapshotStore {

  private implicit val execContext: ExecutionContextExecutor = context.dispatcher
  private val dbAccess: DbAccess = BgPersistenceExtension.apply(context.system).dbAccess
  private val s = RoachSnapshotStoreEntity.syntax("s")
  private val column = RoachSnapshotStoreEntity.column
  private implicit val jacksonJsonSerializer: JacksonJsonSerializer = new JacksonJsonSerializer()
  private implicit val simpleSerializer: SimpleSerializer = RoachAsyncWriteJournal.simpleSerializer(context.system)
  private implicit val hints: RoachSerializerHints = RoachAsyncWriteJournal
    .getSerializerHints(context.system.settings.config.getConfig("persistence-snapstore-roach"))

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria)
  : Future[Option[SelectedSnapshot]] = {
    val tagAndId: PersistenceId = persistenceId

    dbAccess.queryAsync { implicit session =>
      withSQL {
        select
          .from(RoachSnapshotStoreEntity as s)
          .where(toSqlConditions(criteria, tagAndId))
          .orderBy(s.seq).desc
          .limit(1)
      }.map(RoachSnapshotStoreEntity(s.resultName))
        .single().apply()
    }.map {
      _.map { entity: RoachSnapshotStoreEntity =>
        SelectedSnapshot(
          SnapshotMetadata(
            persistenceId,
            entity.seq,
            entity.snapshotTimestamp
          ),
          RoachAsyncWriteJournal.deserialize(entity.snapshot, entity.snapshotClass)
        )
      }

    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    dbAccess.updateAsync { implicit session =>
      val tagAndId: PersistenceId = metadata.persistenceId
      val serialized = RoachAsyncWriteJournal.serialize(snapshot)
      sql"""
           | upsert into ${RoachSnapshotStoreEntity.table}
           |  ( ${column.id}, ${column.tag}, ${column.seq},
           |    ${column.snapshotTimestamp}, ${column.snapshot}, ${column.snapshotClass})
           | values
           |  ( ${tagAndId.uniqueId}, ${tagAndId.tag}, ${metadata.sequenceNr},
           |    ${metadata.timestamp}, ${serialized.value}, ${serialized.valueClass})
           """.stripMargin
        .update().apply()
    }
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    dbAccess.updateAsync { implicit session =>
      val tagAndId: PersistenceId = metadata.persistenceId
      withSQL {
        delete
          .from(RoachSnapshotStoreEntity as s)
          .where
          .eq(s.tag, tagAndId.tag).and
          .eq(s.id, tagAndId.uniqueId).and
          .eq(s.seq, metadata.sequenceNr)
      }.update().apply()
    }
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    dbAccess.updateAsync { implicit session =>
      val tagAndId: PersistenceId = persistenceId
      withSQL {
        delete
          .from(RoachSnapshotStoreEntity as s)
          .where(toSqlConditions(criteria, tagAndId))
      }.update().apply()
    }
  }

  private def toSqlConditions(criteria: SnapshotSelectionCriteria, tagAndId: PersistenceId) = {
    val syntax = SQLSyntax.eq(s.tag, tagAndId.tag).and
      .eq(s.id, tagAndId.uniqueId).and
      .le(s.seq, criteria.maxSequenceNr).and
      .le(s.snapshotTimestamp, criteria.maxTimestamp).and
      .ge(s.seq, criteria.minSequenceNr).and
      .ge(s.snapshotTimestamp, criteria.minTimestamp)
    syntax
  }
}
