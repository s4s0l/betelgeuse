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

import java.nio.charset.Charset
import java.sql.SQLException
import java.util.Base64

import akka.persistence.PersistentRepr
import akka.serialization.{Serialization, SerializationExtension}
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports.CrateDbObject
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{JurnalDuplicateKeyException, PersistenceId, ScalikeAsyncWriteJournal}
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer}
import org.slf4j.LoggerFactory
import scalikejdbc.DBSession

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
class CrateAsyncWriteJournal()
  extends ScalikeAsyncWriteJournal[CrateAsyncWriteJournalEntity]() {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  override val dao: CrateAsyncWriteJournalDao = new CrateAsyncWriteJournalDao()
  private val reprSerialization: Serialization = SerializationExtension.get(context.system)
  private val jsonSerialization: Option[JacksonJsonSerializer] = JacksonJsonSerializer.get(reprSerialization)

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    Future.failed(new UnsupportedOperationException("message deletion is not supported, it could mess up consistency."))
  }

  override def createEntity(representation: PersistentRepr): CrateAsyncWriteJournalEntity = {
    CrateAsyncWriteJournal.createEntity(representation, reprSerialization, jsonSerialization)
  }


  override def createRepresentation(entity: CrateAsyncWriteJournalEntity): PersistentRepr = {
    CrateAsyncWriteJournal.createRepresentation(entity, reprSerialization)
  }

  override def mapExceptions(session: DBSession): PartialFunction[Exception, Exception] = {
    case sql: SQLException if sql.getMessage.contains("DuplicateKeyException") =>
      try {
        //TODO some strategy at most once in a second or something
        dao.refreshTable(session)
      } catch {
        case _: Exception =>
          LOGGER.error("Unable to refresh table on duplicate key exception")
      }
      new JurnalDuplicateKeyException("Key duplicated", sql)
  }
}

object CrateAsyncWriteJournal {
  def createEntity(representation: PersistentRepr,
                   reprSerialization: Serialization,
                   jsonSerialization: Option[JacksonJsonSerializer])
  : CrateAsyncWriteJournalEntity = {
    val persistenceId = PersistenceId.fromString(representation.persistenceId)
    val persistenceIdTag = persistenceId.tag
    val uniqueId = persistenceId.uniqueId
    val sequenceNr = representation.sequenceNr
    val serializedRepr: Array[Byte] = reprSerialization.serialize(representation).get
    val representationEncoded = Base64.getEncoder.encodeToString(serializedRepr)

    val crateObject = representation.payload match {
      case a: CrateDbObject => Some(AnyRefObject(a))
      case _ => None
    }
    val jsonObject = crateObject match {
      case None => toJson(representation, jsonSerialization)
      case _ => None
    }
    new CrateAsyncWriteJournalEntity(persistenceIdTag, uniqueId,
      sequenceNr,
      representationEncoded,
      crateObject,
      jsonObject,
      None
    )
  }

  def toJson(p: PersistentRepr, jsonSerialization: Option[JacksonJsonSerializer]): Option[String] = {
    jsonSerialization
      .find(_ => classOf[JacksonJsonSerializable].isAssignableFrom(p.payload.getClass))
      .map(jsonSerialization => jsonSerialization.toBinary(p.payload.asInstanceOf[AnyRef]))
      .map(bytes => new String(bytes, Charset.forName("UTF-8")))
  }

  def createRepresentation(entity: CrateAsyncWriteJournalEntity,
                           reprSerialization: Serialization)
  : PersistentRepr = {
    reprSerialization
      .serializerFor(classOf[PersistentRepr])
      .fromBinary(entity.getSerializedRepresentation)
      .asInstanceOf[PersistentRepr]
      .update(
        sequenceNr = entity.getSequenceNumber,
        deleted = false,
        persistenceId = PersistenceId(entity.tag, entity.id).toString
      )
  }
}
