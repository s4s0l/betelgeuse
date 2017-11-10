package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import akka.persistence.PersistentRepr
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.s4s0l.betelgeuse.akkacommons.test.DbCrateTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FeatureSpec, GivenWhenThen}

import scala.collection.{immutable, mutable}

/**
  * @author Marcin Wielgus
  */
class CrateAsyncWriteJournalDaoTest extends FeatureSpec with GivenWhenThen with DbCrateTest with MockFactory{

  feature("Akka journal can be savedin crate db") {
    scenario("Regular Events are saved and retrieved") {
      sqlExecution { implicit session =>
        Given("Crate async writer dao with no serializer")
        val dao = new CrateAsyncWriteJournalDao(None)
        And("Some regular event")
        val event = RegularEvent("s", 1, Seq("a"))
        When("Entity is created")
        val entity = dao.createEntity("tag", "123", 1, Array[Byte](1, 2, 3), PersistentRepr.apply(
          payload = event, sequenceNr = 1, persistenceId = "tag/123", deleted = false
        ))
        Then("It has all fields set as in request")
        assert(entity.tag == "tag")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.event.isEmpty)
        assert(entity.json.isEmpty)
        assert(entity.created.isEmpty)
        assert(entity.serialized == "AQID")
        assert(entity.getSerializedRepresentation.toSeq == Array[Byte](1, 2, 3).toSeq)

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))
        refreshTable(CrateAsyncWriteJournalEntity.tableName)

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[CrateAsyncWriteJournalEntity]()

        dao.replayMessages("tag", "123", -1, 100, 100) { e =>
          replayedMessages += e
        }

        Then("We get the one created earlier")
        assert(replayedMessages.size == 1)
        assert(replayedMessages.head.tag == "tag")
        assert(replayedMessages.head.id == "123")
        assert(replayedMessages.head.seq == 1)
        assert(replayedMessages.head.event.isEmpty)
        assert(replayedMessages.head.json.isEmpty)
        assert(replayedMessages.head.created.isDefined)
        assert(replayedMessages.head.serialized == "AQID")
        assert(replayedMessages.head.getSerializedRepresentation.toSeq == Array[Byte](1, 2, 3).toSeq)

      }
    }



    scenario("Json serializable Events are saved and retrieved") {
      sqlExecution { implicit session =>
        Given("Crate async writer dao with no serializer")

        val jjs = mock[JacksonJsonSerializer]
        (jjs.toBinary _).expects(*).returning(Array[Byte](0x41,0x42,0x43))

        val dao = new CrateAsyncWriteJournalDao(Some(jjs))
        And("Some regular event")
        val event = JsonEvent("s", 1, Seq("a"))
        When("Entity is created")
        val entity = dao.createEntity("tag2", "123", 1, Array[Byte](1, 2, 3), PersistentRepr.apply(
          payload = event, sequenceNr = 1, persistenceId = "tag2/123", deleted = false
        ))
        Then("It has all fields set as in request")
        assert(entity.tag == "tag2")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.event.isEmpty)
        assert(entity.json.get == "ABC")
        assert(entity.created.isEmpty)
        assert(entity.serialized == "AQID")
        assert(entity.getSerializedRepresentation.toSeq == Array[Byte](1, 2, 3).toSeq)

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))
        refreshTable(CrateAsyncWriteJournalEntity.tableName)

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag2", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[CrateAsyncWriteJournalEntity]()

        dao.replayMessages("tag2", "123", -1, 100, 100) { e =>
          replayedMessages += e
        }

        Then("We get the one created earlier")
        assert(replayedMessages.size == 1)
        assert(replayedMessages.head.tag == "tag2")
        assert(replayedMessages.head.id == "123")
        assert(replayedMessages.head.seq == 1)
        assert(replayedMessages.head.event.isEmpty)
        assert(replayedMessages.head.json.get == "ABC")
        assert(replayedMessages.head.created.isDefined)
        assert(replayedMessages.head.serialized == "AQID")
        assert(replayedMessages.head.getSerializedRepresentation.toSeq == Array[Byte](1, 2, 3).toSeq)

      }
    }


    scenario("Crate serializable Events are saved and retrieved") {
      sqlExecution { implicit session =>
        Given("Crate async writer dao with no serializer")

        val jjs = mock[JacksonJsonSerializer]
        (jjs.toBinary _).expects(*).never()

        val dao = new CrateAsyncWriteJournalDao(Some(jjs))
        And("Some regular event")
        val event = new CrateEvent("s", 1, Seq("a"))
        When("Entity is created")
        val entity = dao.createEntity("tag3", "123", 1, Array[Byte](1, 2, 3), PersistentRepr.apply(
          payload = event, sequenceNr = 1, persistenceId = "tag3/123", deleted = false
        ))
        Then("It has all fields set as in request")
        assert(entity.tag == "tag3")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.event.get == AnyRefObject(event))
        assert(entity.json.isEmpty)
        assert(entity.created.isEmpty)
        assert(entity.serialized == "AQID")
        assert(entity.getSerializedRepresentation.toSeq == Array[Byte](1, 2, 3).toSeq)

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))
        refreshTable(CrateAsyncWriteJournalEntity.tableName)

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag3", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[CrateAsyncWriteJournalEntity]()

        dao.replayMessages("tag3", "123", -1, 100, 100) { e =>
          replayedMessages += e
        }

        Then("We get the one created earlier")
        assert(replayedMessages.size == 1)
        assert(replayedMessages.head.tag == "tag3")
        assert(replayedMessages.head.id == "123")
        assert(replayedMessages.head.seq == 1)
        assert(replayedMessages.head.event.get == AnyRefObject(event))
        assert(replayedMessages.head.json.isEmpty)
        assert(replayedMessages.head.created.isDefined)
        assert(replayedMessages.head.serialized == "AQID")
        assert(replayedMessages.head.getSerializedRepresentation.toSeq == Array[Byte](1, 2, 3).toSeq)

      }
    }

  }

}
