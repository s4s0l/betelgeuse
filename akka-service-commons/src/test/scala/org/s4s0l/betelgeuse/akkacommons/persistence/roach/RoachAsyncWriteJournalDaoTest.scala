/*
 * Copyright© 2017 the original author or authors.
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

import akka.persistence.PersistentRepr
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.RoachAsyncWriteJournalDaoTest._
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer}
import org.s4s0l.betelgeuse.akkacommons.test.DbRoachTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FeatureSpec, GivenWhenThen}
import scalikejdbc.DBSession

import scala.collection.{immutable, mutable}

/**
  * @author Marcin Wielgus
  */
class RoachAsyncWriteJournalDaoTest extends FeatureSpec with GivenWhenThen with DbRoachTest with MockFactory with ScalaFutures {

  feature("Akka journal can be savedin roach db") {
    scenario("Regular Events are saved and retrieved") {
      sqlExecution { implicit session =>
        Given("Crate async writer dao with no serializer")
        val dao = new RoachAsyncWriteJournalDao(None)
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

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[RoachAsyncWriteJournalEntity]()

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
        (jjs.toBinary _).expects(*).returning(Array[Byte](0x41, 0x42, 0x43))

        val dao = new RoachAsyncWriteJournalDao(Some(jjs))
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

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag2", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[RoachAsyncWriteJournalEntity]()

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


    scenario("Roach serializable Events are passed but not saved and they don't trip") {
      sqlExecution { implicit session =>
        Given("Crate async writer dao with no serializer")

        val jjs = mock[JacksonJsonSerializer]
        (jjs.toBinary _).expects(*).never()

        val dao = new RoachAsyncWriteJournalDao(Some(jjs))
        And("Some regular event")
        val event = CrateEvent("s", 1, Seq("a"))
        When("Entity is created")
        val entity = dao.createEntity("tag3", "123", 1, Array[Byte](1, 2, 3), PersistentRepr.apply(
          payload = event, sequenceNr = 1, persistenceId = "tag3/123", deleted = false
        ))
        Then("It has all fields set as in request")
        assert(entity.tag == "tag3")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.event.isEmpty)
        assert(entity.json.isEmpty)
        assert(entity.created.isEmpty)
        assert(entity.serialized == "AQID")
        assert(entity.getSerializedRepresentation.toSeq == Array[Byte](1, 2, 3).toSeq)

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag3", "123", -1) == 1)

        When("Replaying this entity")

        val replayedMessages = mutable.Buffer[RoachAsyncWriteJournalEntity]()

        dao.replayMessages("tag3", "123", -1, 100, 100) { e =>
          replayedMessages += e
        }

        Then("We get the one created earlier but without event")
        assert(replayedMessages.size == 1)
        assert(replayedMessages.head.tag == "tag3")
        assert(replayedMessages.head.id == "123")
        assert(replayedMessages.head.seq == 1)
        assert(replayedMessages.head.event.isEmpty)
        assert(replayedMessages.head.json.isEmpty)
        assert(replayedMessages.head.created.isDefined)
        assert(replayedMessages.head.serialized == "AQID")
        assert(replayedMessages.head.getSerializedRepresentation.toSeq == Array[Byte](1, 2, 3).toSeq)

      }
    }

  }


  feature("Akka journal can delete messages up to sequence id") {
    scenario("All events up to newest are deleted") {
      sqlExecution { implicit session =>

        Given("Crate async writer dao with no serializer")
        val dao = new RoachAsyncWriteJournalDao(None)
        And("3 events are setup")
        setup3Events(dao, "5")

        When("We delete messages up to sequence num 3")
        dao.deleteUpTo("tag", "5", 3)

        Then("Max seq returns last inserted value")
        assert(dao.getMaxSequenceNumber("tag", "5", -1) == 3)

        val replayedMessagesAfterCleaning = mutable.Buffer[RoachAsyncWriteJournalEntity]()

        dao.replayMessages("tag", "5", -1, 100, 100) { e =>
          replayedMessagesAfterCleaning += e
        }

        Then("Only last message is returned")
        assert(replayedMessagesAfterCleaning.size == 1)
        assert(replayedMessagesAfterCleaning.head.tag == "tag")
        assert(replayedMessagesAfterCleaning.head.id == "5")
        assert(replayedMessagesAfterCleaning.head.seq == 3)
        assert(replayedMessagesAfterCleaning.head.event.isEmpty)
        assert(replayedMessagesAfterCleaning.head.json.isEmpty)
        assert(replayedMessagesAfterCleaning.head.created.isDefined)
        assert(replayedMessagesAfterCleaning.head.getSerializedRepresentation.toSeq == Array[Byte](7, 8, 9).toSeq)
      }

    }

    scenario("Deleting upTo sequence number > max throws") {
      sqlExecution { implicit session =>
        Given("Crate async writer dao with no serializer")
        val dao = new RoachAsyncWriteJournalDao(None)
        And("3 events are setup")
        setup3Events(dao, "4")

        When("We delete messages upto sequence higher then max (3)")
        Then("It throws")
        assertThrows[Exception](dao.deleteUpTo("tag", "4", 4))

        When("We chack the state of events")
        val replayedMessagesAfterCleaning = mutable.Buffer[RoachAsyncWriteJournalEntity]()

        dao.replayMessages("tag", "4", -1, 100, 100) { e =>
          replayedMessagesAfterCleaning += e
        }

        Then("None were deleted")
        assert(replayedMessagesAfterCleaning.size == 3)

      }
    }
  }


  def setup3Events(dao: RoachAsyncWriteJournalDao, id: String)(implicit session: DBSession): Unit = {
    val event1 = RegularEvent("s", 1, Seq("a"))
    val event2 = RegularEvent("s", 2, Seq("b"))
    val event3 = RegularEvent("s", 3, Seq("c"))
    When("Entities are created")
    val entity1 = dao.createEntity("tag", id, 1, Array[Byte](1, 2, 3), PersistentRepr.apply(
      payload = event1, sequenceNr = 1, persistenceId = s"tag/$id", deleted = false
    ))

    val entity2 = dao.createEntity("tag", id, 2, Array[Byte](4, 5, 6), PersistentRepr.apply(
      payload = event2, sequenceNr = 2, persistenceId = s"tag/$id", deleted = false
    ))

    val entity3 = dao.createEntity("tag", id, 3, Array[Byte](7, 8, 9), PersistentRepr.apply(
      payload = event3, sequenceNr = 2, persistenceId = s"tag/$id", deleted = false
    ))

    When("This entities are persisted")
    dao.save(immutable.Seq(entity1))
    dao.save(immutable.Seq(entity2))
    dao.save(immutable.Seq(entity3))

    Then("Max seq returns last inserted value")
    assert(dao.getMaxSequenceNumber("tag", id, -1) == 3)


    When("Replaying those events")

    val replayedMessages = mutable.Buffer[RoachAsyncWriteJournalEntity]()

    dao.replayMessages("tag", id, -1, 100, 100) { e =>
      replayedMessages += e
    }

    Then("We get all messages and last is what we expected")
    assert(replayedMessages.size == 3)
    assert(replayedMessages.last.tag == "tag")
    assert(replayedMessages.last.id == id)
    assert(replayedMessages.last.seq == 3)
    assert(replayedMessages.last.event.isEmpty)
    assert(replayedMessages.last.json.isEmpty)
    assert(replayedMessages.last.created.isDefined)
    assert(replayedMessages.last.getSerializedRepresentation.toSeq == Array[Byte](7, 8, 9).toSeq)
  }

}


object RoachAsyncWriteJournalDaoTest {

  case class RegularEvent(s: String, i: Int, seq: Seq[String])

  case class JsonEvent(s: String, i: Int, seq: Seq[String]) extends JacksonJsonSerializable

  case class CrateEvent(s: String, i: Int, seq: Seq[String])


}