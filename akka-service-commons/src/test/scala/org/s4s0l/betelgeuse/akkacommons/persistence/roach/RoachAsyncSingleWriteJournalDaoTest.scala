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

import akka.actor.ActorRef
import akka.persistence.{BuiltInSerializerHints, PersistentRepr}
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.RoachAsyncWriteJournalDaoTest.{CrateEvent, JsonEvent}
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializer, SimpleSerializer}
import org.s4s0l.betelgeuse.akkacommons.test.DbRoachTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FeatureSpec, GivenWhenThen}

import scala.collection.{immutable, mutable}

/**
  * @author Marcin Wielgus
  */
class RoachAsyncSingleWriteJournalDaoTest extends FeatureSpec
  with GivenWhenThen
  with DbRoachTest
  with MockFactory
  with ScalaFutures {

  implicit val jacksonSerializer: JacksonJsonSerializer = new JacksonJsonSerializer()
  implicit val simple: SimpleSerializer = jacksonSerializer.asSimple
  implicit val hints: BuiltInSerializerHints = new BuiltInSerializerHints()


  feature("Akka journal can be saved in roach db") {

    scenario("Json serializable Events are saved and retrieved") {
      localTx { implicit session =>
        Given("Roach async writer dao with no serializer")


        val dao = new RoachAsyncSingleWriteJournalDao()
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

        When("Next entity is persisted")
        val event2 = JsonEvent("s", 2, Seq("b"))
        val persRepr2 = PersistentRepr.apply(
          payload = event2,
          sequenceNr = 2,
          persistenceId = "tag2/123",
          deleted = false,
          manifest = "manifa",
          sender = ActorRef.noSender,
          writerUuid = "writerTwo"
        )
        val entity2 = RoachAsyncWriteJournal.createEntity(persRepr2)
        dao.save(immutable.Seq(entity2))

        Then("Max seq returns inserted value")
        assert(dao.getMaxSequenceNumber("tag2", "123", -1) == 2)

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
        assert(replayedEntities.head.seq == 2)
        assert(replayedEntities.head.eventClass == entity2.eventClass)
        assert(replayedEntities.head.event == """{"i": 2, "s": "s", "seq": ["b"]}""")
        assert(replayedEntities.head.writerUuid == entity2.writerUuid)
        assert(replayedEntities.head.deleted == entity2.deleted)
        assert(replayedEntities.head.manifest == entity2.manifest)

        assert(replayedRepresentations.lengthCompare(1) == 0)
        assert(replayedRepresentations.head.persistenceId == "tag2/123")
        assert(replayedRepresentations.head.sequenceNr == 2)
        assert(replayedRepresentations.head.payload == event2)
        assert(replayedRepresentations.head.writerUuid == "writerTwo")
        assert(replayedRepresentations.head.sender == ActorRef.noSender)
        assert(replayedRepresentations.head.deleted == entity2.deleted)
        assert(replayedRepresentations.head.manifest == entity2.manifest)
      }
    }


    scenario("String Events are saved and retrieved") {
      localTx { implicit session =>
        Given("Roach async writer dao")

        val dao = new RoachAsyncSingleWriteJournalDao()
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
        assert(entity.eventClass == "org.s4s0l.betelgeuse.akkacommons.serialization.JsonSimpleTypeWrapper")
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
}