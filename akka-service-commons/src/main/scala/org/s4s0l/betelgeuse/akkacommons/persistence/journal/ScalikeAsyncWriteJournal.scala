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


package org.s4s0l.betelgeuse.akkacommons.persistence.journal

import java.sql.SQLException

import akka.actor.ActorLogging
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbAccess
import org.s4s0l.betelgeuse.akkacommons.serialization.DepricatedTypeWithMigrationInfo
import scalikejdbc.DBSession

import scala.collection.immutable
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Try}

/**
  * @author Marcin Wielgus
  */
abstract class ScalikeAsyncWriteJournal[T <: ScalikeAsyncWriteJournalEntity]
  extends AsyncWriteJournal
    with ActorLogging {

  val dao: ScalikeAsyncWriteJournalDao[T]

  implicit val execContext: ExecutionContextExecutor = context.dispatcher

  val dbAccess: DbAccess = BgPersistenceExtension.apply(context.system).dbAccess

  def createEntity(representation: PersistentRepr): T

  def createRepresentation(entity: T): PersistentRepr

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    Future {
      messages.map {
        atomicWrite =>
          dbAccess.update { implicit session =>
            //just to be sure it exists?
            session.connection
            val retTry = Try {
              try {
                dao.save(atomicWrite.payload.map(it => createEntity(it)))
              } catch {
                case e: Exception if mapExceptions(session).isDefinedAt(e) => throw mapExceptions(session).apply(e)
              }
            }
            //failed Try are for message rejected, others as ones below will cause failure (actor restart)
            retTry match {
              case Failure(e: JournalFailureException) =>
                throw e
              case _ =>
            }
            retTry
          }
      }
    }
  }

  def mapExceptions(session: DBSession): PartialFunction[Exception, Exception] = {
    case sql: SQLException if sql.getMessage.contains("DuplicateKeyException") =>
      new JurnalDuplicateKeyException("Key duplicated", sql)
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                                  (recoveryCallback: PersistentRepr => Unit): Future[Unit] = {


    Future {
      dbAccess.query { implicit session =>
        val persistenceIdObject: PersistenceId = PersistenceId.fromString(persistenceId)
        dao.replayMessages(persistenceIdObject.tag, persistenceIdObject.uniqueId, fromSequenceNr, toSequenceNr, max) {
          entity: T =>

            val persistentRepresentation: PersistentRepr = createRepresentation(entity)

            val migratedToNewVersion = persistentRepresentation.payload match {
              case callback: DepricatedTypeWithMigrationInfo =>
                val updatedPayload = callback.convertToMigratedType()
                persistentRepresentation.withPayload(updatedPayload)
              case _ =>
                persistentRepresentation
            }

            val updatedRepresentation: PersistentRepr = migratedToNewVersion.payload match {
              case callback: JournalCallback =>
                val updatedPayload = callback.restored(entity)
                migratedToNewVersion.withPayload(updatedPayload)
              case _ =>
                migratedToNewVersion
            }

            recoveryCallback.apply(updatedRepresentation)
        }
      }

    }
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    Future {
      dbAccess.query { implicit session =>
        val id = PersistenceId.fromString(persistenceId)
        dao.getMaxSequenceNumber(id.tag, id.uniqueId, fromSequenceNr)
      }
    }
  }

}
