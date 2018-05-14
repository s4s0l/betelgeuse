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

import java.io.Serializable

import akka.actor.ActorSystem
import akka.persistence.PersistentRepr
import akka.serialization.SerializationExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateAsyncWriteJournalDaoTest._
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports.{CrateDbObject, CrateDbObjectMapper, classTag, _}
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.Internals.Wrapper
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer}
import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FeatureSpec, GivenWhenThen}

import scala.collection.{immutable, mutable}
import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */
class CrateAsyncWriteJournalDaoTest extends FeatureSpec with GivenWhenThen with DbCrateTest with MockFactory {

  val actorSystem = ActorSystem(getClass.getSimpleName)

  feature("Akka journal can be saved in crate db") {
    scenario("Regular Events are saved and retrieved") {
      localTx { implicit session =>
        Given("Crate async writer dao with no serializer")
        val dao = new CrateAsyncWriteJournalDao(SerializationExtension.get(actorSystem), None)
        And("Some regular event")
        val event = RegularEvent("s", 1, Seq("a"))
        When("Entity is created")
        val repr = PersistentRepr.apply(
          payload = event, sequenceNr = 1,
          persistenceId = "tag/123",
          deleted = false
        )
        val entity = dao.createEntity(repr)
        Then("It has all fields set as in request")
        assert(entity.tag == "tag")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.event.isEmpty)
        assert(entity.json.isEmpty)
        assert(entity.created.isEmpty)
        assert(entity.serialized.nonEmpty)
        assert(entity.getSerializedRepresentation.nonEmpty)

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))
        refreshTable(CrateAsyncWriteJournalEntity.tableName)

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[CrateAsyncWriteJournalEntity]()

        dao.replayMessages("tag", "123", -1, 100, 100) {
          (e, _) =>
            replayedMessages += e
        }

        Then("We get the one created earlier")
        assert(replayedMessages.lengthCompare(1) == 0)
        assert(replayedMessages.head.tag == "tag")
        assert(replayedMessages.head.id == "123")
        assert(replayedMessages.head.seq == 1)
        assert(replayedMessages.head.event.isEmpty)
        assert(replayedMessages.head.json.isEmpty)
        assert(replayedMessages.head.created.isDefined)
        assert(replayedMessages.head.serialized == entity.serialized)
        assert(replayedMessages.head.getSerializedRepresentation.toSeq == entity.getSerializedRepresentation.toSeq)

      }
    }


    scenario("Json serializable Events are saved and retrieved") {
      localTx { implicit session =>
        Given("Crate async writer dao with no serializer")
        val dao = new CrateAsyncWriteJournalDao(SerializationExtension.get(actorSystem), Some(new JacksonJsonSerializer()))
        And("Some regular event")
        val event = JsonEvent("s", 1, Seq("a"))
        When("Entity is created")
        val entity = dao.createEntity(PersistentRepr.apply(
          payload = event, sequenceNr = 1, persistenceId = "tag2/123", deleted = false
        ))
        Then("It has all fields set as in request")
        assert(entity.tag == "tag2")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.event.isEmpty)
        assert(entity.json.get == """{"s":"s","i":1,"seq":["a"]}""")
        assert(entity.created.isEmpty)
        assert(entity.serialized.nonEmpty)
        assert(entity.getSerializedRepresentation.nonEmpty)

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))
        refreshTable(CrateAsyncWriteJournalEntity.tableName)

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag2", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[CrateAsyncWriteJournalEntity]()

        dao.replayMessages("tag2", "123", -1, 100, 100) {
          (e, _) =>
            replayedMessages += e
        }

        Then("We get the one created earlier")
        assert(replayedMessages.lengthCompare(1) == 0)
        assert(replayedMessages.head.tag == "tag2")
        assert(replayedMessages.head.id == "123")
        assert(replayedMessages.head.seq == 1)
        assert(replayedMessages.head.event.isEmpty)
        assert(replayedMessages.head.json.get == entity.json.get)
        assert(replayedMessages.head.created.isDefined)
        assert(replayedMessages.head.serialized == entity.serialized)
        assert(replayedMessages.head.getSerializedRepresentation.toSeq == entity.getSerializedRepresentation.toSeq)

      }
    }


    scenario("Crate serializable Events are saved and retrieved") {
      localTx { implicit session =>
        Given("Crate async writer dao with no serializer")
        val dao = new CrateAsyncWriteJournalDao(SerializationExtension.get(actorSystem), Some(new JacksonJsonSerializer()))
        And("Some regular event")
        val event = new CrateEvent("s", 1, Seq("a"))
        When("Entity is created")
        val entity = dao.createEntity(PersistentRepr.apply(
          payload = event,
          sequenceNr = 1,
          persistenceId = "tag3/123",
          deleted = false
        ))
        Then("It has all fields set as in request")
        assert(entity.tag == "tag3")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.event.get == AnyRefObject(event))
        assert(entity.json.isEmpty)
        assert(entity.created.isEmpty)
        assert(entity.serialized.nonEmpty)
        assert(entity.getSerializedRepresentation.nonEmpty)

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))
        refreshTable(CrateAsyncWriteJournalEntity.tableName)

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag3", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[CrateAsyncWriteJournalEntity]()

        dao.replayMessages("tag3", "123", -1, 100, 100) {
          (e, _) =>
            replayedMessages += e
        }

        Then("We get the one created earlier")
        assert(replayedMessages.lengthCompare(1) == 0)
        assert(replayedMessages.head.tag == "tag3")
        assert(replayedMessages.head.id == "123")
        assert(replayedMessages.head.seq == 1)
        assert(replayedMessages.head.event.get == AnyRefObject(event))
        assert(replayedMessages.head.json.isEmpty)
        assert(replayedMessages.head.created.isDefined)
        assert(replayedMessages.head.serialized == entity.serialized)
        assert(replayedMessages.head.getSerializedRepresentation.toSeq == entity.getSerializedRepresentation.toSeq)

      }
    }

  }

}


object CrateAsyncWriteJournalDaoTest {

  case class RegularEvent(s: String, i: Int, seq: Seq[String])

  case class JsonEvent(s: String, i: Int, seq: Seq[String])
    extends JacksonJsonSerializable

  case class CrateEvent(s: String, i: Int, seq: Seq[String])
    extends CrateDbObject with Serializable

  object CrateEvent extends CrateDbObjectMapper[CrateEvent] {
    override def ctag: ClassTag[CrateEvent] = classTag[CrateEvent]

    override def toSql(no: CrateEvent): Map[String, Wrapper] = {
      Map[String, Wrapper](
        "s" -> no.s,
        "i" -> no.i,
        "seq" -> no.seq.toList,
      )
    }

    override def fromSql(resolver: Internals.ObjectAttributeResolver): CrateEvent = {
      new CrateEvent(
        resolver.string("s").get,
        resolver.int("i").get,
        resolver.get[List[String]]("seq").get,
      )
    }

  }

}