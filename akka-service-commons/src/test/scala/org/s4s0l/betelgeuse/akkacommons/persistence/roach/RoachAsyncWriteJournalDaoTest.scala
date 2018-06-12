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

import akka.actor.{ActorRef, ActorSystem}
import akka.persistence.PersistentRepr
import com.typesafe.config.ConfigFactory
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.RoachAsyncWriteJournalDaoTest._
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.akkacommons.test.DbRoachTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FeatureSpec, GivenWhenThen}
import scalikejdbc.DBSession

import scala.collection.{immutable, mutable}

/**
  * @author Marcin Wielgus
  */
class RoachAsyncWriteJournalDaoTest
  extends FeatureSpec
    with GivenWhenThen
    with DbRoachTest
    with MockFactory
    with ScalaFutures {

  private implicit val serializer: RoachSerializer = new RoachSerializer(ActorSystem(getClass.getSimpleName), ConfigFactory.empty())

  feature("Akka journal can be saved in roach db") {

    scenario("Json serializable Events are saved and retrieved") {
      localTx { implicit session =>
        Given("Roach async writer dao with no serializer")

        val dao = new RoachAsyncWriteJournalDao()
        And("Some regular event")
        val event = JsonEvent("s", 1, Seq("a"))
        val persRepr = PersistentRepr.apply(
          payload = event,
          sequenceNr = 1,
          persistenceId = "tag2/123",
          deleted = false,
          manifest = "manifa",
          sender = ActorRef.noSender,
          writerUuid = "writerOne"
        )
        When("Entity is created")
        val entity = RoachAsyncWriteJournal.createEntity(persRepr)
        Then("It has all fields set as in request")
        assert(entity.tag == "tag2")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.eventClass == event.getClass.getName)
        assert(entity.event == """{"s":"s","i":1,"seq":["a"]}""")
        assert(entity.writerUuid == "writerOne")
        assert(!entity.deleted)
        assert(entity.manifest == "manifa")

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag2", "123", -1) == 1)

        When("Replaying this entity")

        val replayedEntities = mutable.Buffer[RoachAsyncWriteJournalEntity]()
        val replayedRepresentations = mutable.Buffer[PersistentRepr]()

        dao.replayMessages("tag2", "123", -1, 100, 100) {
          e =>
            replayedEntities += e
            replayedRepresentations += RoachAsyncWriteJournal.createRepresentation(e)
        }

        Then("We get the one created earlier")
        assert(replayedEntities.lengthCompare(1) == 0)
        assert(replayedEntities.head.tag == "tag2")
        assert(replayedEntities.head.id == "123")
        assert(replayedEntities.head.seq == 1)
        assert(replayedEntities.head.eventClass == entity.eventClass)
        assert(replayedEntities.head.event == """{"i": 1, "s": "s", "seq": ["a"]}""")
        assert(replayedEntities.head.writerUuid == entity.writerUuid)

        assert(replayedEntities.head.deleted == entity.deleted)
        assert(replayedEntities.head.manifest == entity.manifest)


        assert(replayedRepresentations.lengthCompare(1) == 0)
        assert(replayedRepresentations.head.persistenceId == "tag2/123")
        assert(replayedRepresentations.head.sequenceNr == 1)
        assert(replayedRepresentations.head.payload == event)
        assert(replayedRepresentations.head.writerUuid == entity.writerUuid)
        assert(replayedRepresentations.head.sender == ActorRef.noSender)
        assert(replayedRepresentations.head.deleted == entity.deleted)
        assert(replayedRepresentations.head.manifest == entity.manifest)
      }
    }


    scenario("String Events are saved and retrieved") {
      localTx { implicit session =>
        Given("Roach async writer dao")

        val dao = new RoachAsyncWriteJournalDao()
        And("Some string event")
        val event = "a string value"
        val persRepr = PersistentRepr.apply(
          payload = event,
          sequenceNr = 1,
          persistenceId = "tag9/123",
          deleted = false,
          manifest = "manifa",
          sender = ActorRef.noSender,
          writerUuid = "writerOne"
        )
        When("Entity is created")
        val entity = RoachAsyncWriteJournal.createEntity(persRepr)
        Then("It has all fields set as in request")
        assert(entity.tag == "tag9")
        assert(entity.id == "123")
        assert(entity.seq == 1)
        assert(entity.eventClass == "org.s4s0l.betelgeuse.akkacommons.persistence.roach.JsonSimpleTypeWrapper")
        assert(entity.event == """{"string":"a string value"}""")
        assert(entity.writerUuid == "writerOne")

        assert(!entity.deleted)
        assert(entity.manifest == "manifa")

        When("This entity is persisted")
        dao.save(immutable.Seq(entity))

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag9", "123", -1) == 1)

        When("Replaying this entity")

        val replayedEntities = mutable.Buffer[RoachAsyncWriteJournalEntity]()
        val replayedRepresentations = mutable.Buffer[PersistentRepr]()

        dao.replayMessages("tag9", "123", -1, 100, 100) {
          e =>
            replayedEntities += e
            replayedRepresentations += RoachAsyncWriteJournal.createRepresentation(e)
        }

        Then("We get the one created earlier")
        assert(replayedEntities.lengthCompare(1) == 0)
        assert(replayedEntities.head.tag == "tag9")
        assert(replayedEntities.head.id == "123")
        assert(replayedEntities.head.seq == 1)
        assert(replayedEntities.head.eventClass == entity.eventClass)
        assert(replayedEntities.head.event == """{"string": "a string value"}""")
        assert(replayedEntities.head.writerUuid == entity.writerUuid)
        assert(replayedEntities.head.deleted == entity.deleted)
        assert(replayedEntities.head.manifest == entity.manifest)


        assert(replayedRepresentations.lengthCompare(1) == 0)
        assert(replayedRepresentations.head.persistenceId == "tag9/123")
        assert(replayedRepresentations.head.sequenceNr == 1)
        assert(replayedRepresentations.head.payload == event)
        assert(replayedRepresentations.head.writerUuid == entity.writerUuid)
        assert(replayedRepresentations.head.sender == ActorRef.noSender)
        assert(replayedRepresentations.head.deleted == entity.deleted)
        assert(replayedRepresentations.head.manifest == entity.manifest)
      }
    }

    scenario("Non json serializable events fail") {
      localTx { implicit session =>
        Given("Some regular event")
        val event = CrateEvent("s", 1, Seq("a"))
        When("Entity is created")
        intercept[ClassCastException](RoachAsyncWriteJournal.createEntity(PersistentRepr.apply(
          payload = event, sequenceNr = 1, persistenceId = "tag3/123",
          deleted = false,
          manifest = "manifa",
          sender = ActorRef.noSender,
          writerUuid = "writerOne"
        )))

      }
    }

  }


  feature("Akka journal can delete messages up to sequence id") {
    scenario("All events up to newest are deleted") {
      localTx { implicit session =>

        Given("Crate async writer dao with no serializer")
        val dao = new RoachAsyncWriteJournalDao()
        And("3 events are setup")
        setup3Events(dao, "5")

        When("We delete messages up to sequence num 3")
        dao.deleteUpTo("tag", "5", 3)


        Then("Max seq returns last inserted value")
        assert(dao.getMaxSequenceNumber("tag", "5", -1) == 3)

        val replayedMessagesAfterCleaning = mutable.Buffer[RoachAsyncWriteJournalEntity]()

        dao.replayMessages("tag", "5", -1, 100, 100) {
          e =>
            replayedMessagesAfterCleaning += e
        }

        Then("No messages are returned")
        assert(replayedMessagesAfterCleaning.lengthCompare(0) == 0)


        When("next message is inserted")
        setup3Events(dao, "5", dao.getMaxSequenceNumber("tag", "5", -1) + 1)


        Then("It has sequence number not lost")
        dao.replayMessages("tag", "5", -1, 100, 100) {
          e =>
            replayedMessagesAfterCleaning += e
        }
        assert(replayedMessagesAfterCleaning.size == 3)
        assert(replayedMessagesAfterCleaning.head.tag == "tag")
        assert(replayedMessagesAfterCleaning.head.id == "5")
        assert(replayedMessagesAfterCleaning.head.seq == 4)
        assert(replayedMessagesAfterCleaning.head.eventClass == classOf[RegularEvent].getName)
        assert(replayedMessagesAfterCleaning.head.event == """{"i": 1, "s": "s", "seq": ["a"]}""")
        assert(replayedMessagesAfterCleaning.head.writerUuid == "writerOne")
        assert(!replayedMessagesAfterCleaning.head.deleted)
        assert(replayedMessagesAfterCleaning.head.manifest == "manifa")
      }

    }

    scenario("Deleting upTo sequence number > max does not throw") {
      localTx { implicit session =>
        Given("Crate async writer dao with no serializer")

        val dao = new RoachAsyncWriteJournalDao()
        And("3 events are setup")
        setup3Events(dao, "4")

        When("We delete messages up to sequence higher then max (3)")
        dao.deleteUpTo("tag", "4", 4)

        When("We check the state of events")
        val replayedMessagesAfterCleaning = mutable.Buffer[RoachAsyncWriteJournalEntity]()

        dao.replayMessages("tag", "4", -1, 100, 100) {
          e =>
            replayedMessagesAfterCleaning += e
        }

        Then("All were deleted")
        assert(replayedMessagesAfterCleaning.lengthCompare(0) == 0)

      }
    }
  }

  def setup3Events(dao: RoachAsyncWriteJournalDao, id: String, seqStart: Long = 1)
                  (implicit session: DBSession): Unit = {
    val event1 = RegularEvent("s", 1, Seq("a"))
    val event2 = RegularEvent("s", 2, Seq("b"))
    val event3 = RegularEvent("s", 3, Seq("c"))
    When("Entities are created")
    val entity1 = RoachAsyncWriteJournal.createEntity(PersistentRepr.apply(
      payload = event1, sequenceNr = seqStart, persistenceId = s"tag/$id",
      deleted = false,
      manifest = "manifa",
      sender = ActorRef.noSender,
      writerUuid = "writerOne"
    ))

    val entity2 = RoachAsyncWriteJournal.createEntity(PersistentRepr.apply(
      payload = event2, sequenceNr = seqStart + 1, persistenceId = s"tag/$id",
      deleted = false,
      manifest = "manifa",
      sender = ActorRef.noSender,
      writerUuid = "writerOne"
    ))

    val entity3 = RoachAsyncWriteJournal.createEntity(PersistentRepr.apply(
      payload = event3, sequenceNr = seqStart + 2, persistenceId = s"tag/$id",
      deleted = false,
      manifest = "manifa",
      sender = ActorRef.noSender,
      writerUuid = "writerOne"
    ))

    When("This entities are persisted")
    dao.save(immutable.Seq(entity1))
    dao.save(immutable.Seq(entity2))
    dao.save(immutable.Seq(entity3))

    Then("Max seq returns last inserted value")
    assert(dao.getMaxSequenceNumber("tag", id, -1) == 2 + seqStart)


    When("Replaying those events")

    val replayedMessages = mutable.Buffer[RoachAsyncWriteJournalEntity]()

    dao.replayMessages("tag", id, -1, 100, 100) {
      e =>
        replayedMessages += e
    }

    Then("We get all messages and last is what we expected")
    assert(replayedMessages.lengthCompare(3) == 0)
    assert(replayedMessages.last.tag == "tag")
    assert(replayedMessages.last.id == id)
    assert(replayedMessages.last.seq == 2 + seqStart)
    assert(replayedMessages.last.eventClass == classOf[RegularEvent].getName)
    assert(replayedMessages.last.event == """{"i": 3, "s": "s", "seq": ["c"]}""")
    assert(replayedMessages.last.writerUuid == "writerOne")
    assert(!replayedMessages.last.deleted)
    assert(replayedMessages.last.manifest == "manifa")
  }
}


object RoachAsyncWriteJournalDaoTest {

  case class RegularEvent(s: String, i: Int, seq: Seq[String]) extends JacksonJsonSerializable

  case class JsonEvent(s: String, i: Int, seq: Seq[String]) extends JacksonJsonSerializable

  case class CrateEvent(s: String, i: Int, seq: Seq[String])


}