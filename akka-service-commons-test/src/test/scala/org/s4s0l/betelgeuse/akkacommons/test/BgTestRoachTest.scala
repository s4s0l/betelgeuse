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

package org.s4s0l.betelgeuse.akkacommons.test

import akka.actor.{ActorRef, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}
import org.s4s0l.betelgeuse.akkacommons.BgServiceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.persistence.{BgPersistenceExtension, utils}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoachTest._
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.s4s0l.betelgeuse.akkacommons.utils.TimeoutShardedActor
import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class BgTestRoachTest extends BgTestService with BgTestRoach {

  private val serviceOne = testWith(new BgPersistenceJournalRoach() {

    override def systemName: String = defaultSystemName + portBase

    override def portBase = 1
  })
  private val serviceTwo = testWith(new BgPersistenceJournalRoach() {

    override def systemName: String = defaultSystemName + portBase

    override def portBase = 2
  })


  feature("Testing of two services is possible with roach db") {
    scenario("Both services work and have their databases initiated") {
      new WithService(serviceOne) {

        When("Using persistence Extension")
        val extension = BgPersistenceExtension.apply(system)
        Then("Default pool name is system name")
        assert(extension.defaultPoolName == "BgTestRoachTest1")
        assert(extension.defaultPoolName == BgServiceExtension.get(system).serviceInfo.id.systemName)
        Then("Flyway migration is performed")
        val x: Option[String] = extension.dbAccess.query { implicit session =>
          sql"select test_value from test_table1".map(_.string(1)).first().apply()
        }
        assert(x.get == "value")
        And("Tables are in proper schema")
        val expectedSchema: String = BgServiceExtension(system).serviceInfo.id.systemName.toLowerCase
        assert(extension.defaultSchemaName == expectedSchema)
        assert(extension.dbAccess.query { implicit session =>
          val unsafeSchema = SQLSyntax.createUnsafely(expectedSchema)
          sql"""show tables from $unsafeSchema.public"""
            .map(_.string(1)).list.apply()
        }.contains("test_table1"))
      }

      new WithService(serviceTwo) {

        When("Using persistence Extension")
        val extension = BgPersistenceExtension.apply(system)
        Then("Default pool name is system name")
        assert(extension.defaultPoolName == "BgTestRoachTest2")
        assert(extension.defaultPoolName == BgServiceExtension.get(system).serviceInfo.id.systemName)
        Then("Flyway migration is performed")
        val x: Option[String] = extension.dbAccess.query { implicit session =>
          sql"select test_value from test_table2".map(_.string(1)).first().apply()
        }
        assert(x.get == "value")
        And("Tables are in proper schema")
        val expectedSchema: String = BgServiceExtension(system).serviceInfo.id.systemName.toLowerCase
        assert(extension.defaultSchemaName == expectedSchema)
        assert(extension.dbAccess.query { implicit session =>
          val unsafeSchema = SQLSyntax.createUnsafely(expectedSchema)
          sql"""show tables from $unsafeSchema.public"""
            .map(_.string(1)).list.apply()
        }.contains("test_table2"))
      }
    }

    scenario("Actor persists itself ad responds") {
      new WithService(serviceTwo) {
        val actor: ActorRef = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 123))
        assert(actor != null)
        actor ! Cmd("Alala")
        testKit.expectMsg(60 seconds, (1, List("Alala-0")))
        actor ! Cmd("Alala")
        testKit.expectMsg(60 seconds, (2, List("Alala-0", "Alala-1")))
        val actor2: ActorRef = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 456))
        actor2 ! Cmd("Alala")
        testKit.expectMsg(60 seconds, (1, List("Alala-0")))
      }
      new WithService(serviceOne) {
        val actor: ActorRef = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 123))
        assert(actor != null)
        actor ! Cmd("Ole")
        testKit.expectMsg(60 seconds, (1, List("Ole-0")))
        actor ! Cmd("Ole")
        testKit.expectMsg(60 seconds, (2, List("Ole-0", "Ole-1")))
        val actor2: ActorRef = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 456))
        actor2 ! Cmd("Ole")
        testKit.expectMsg(60 seconds, (1, List("Ole-0")))
      }
    }
  }


}


object BgTestRoachTest {

  case class Cmd(data: String)

  case class Evt(data: String) extends JacksonJsonSerializable

  case class ExampleState(events: List[String] = Nil) {

    def updated(evt: Evt): ExampleState = copy(evt.data :: events)

    def size: Int = events.length

    override def toString: String = events.reverse.toString
  }

  class ExamplePersistentActor(num: Int) extends PersistentActor {

    lazy val receiveRecover: Receive = {
      case evt: Evt => updateState(evt)
      case SnapshotOffer(_, snapshot: ExampleState) => state = snapshot
    }
    lazy val receiveCommand: Receive = {
      case Cmd(data) =>
        val sndr = sender()
        LOGGER.info("received")
        persist(Evt(s"$data-$numEvents")) { event =>
          LOGGER.info("persisted")
          updateState(event)
          context.system.eventStream.publish(event)
          sndr ! (lastSequenceNr, state.events.reverse)
          if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
            saveSnapshot(state)
        }
    }
    private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)
    val snapShotInterval = 1000
    var state = ExampleState()

    override def preStart(): Unit = {
      LOGGER.info(s"Pre starting $num")
    }

    override def postStop(): Unit = {
      LOGGER.info(s"Post stopping $num")
      super.postStop()
    }

    override def persistenceId = s"example/$num"

    def updateState(event: Evt): Unit =
      state = state.updated(event)

    def numEvents: Int =
      state.size

  }

  case class CmdSharded(num: Int, data: String)

  case class EvtSharded(num: Int, data: String) extends JacksonJsonSerializable


  class ExamplePersistentShardedActor extends utils.PersistentShardedActor with TimeoutShardedActor {

    import scala.concurrent.duration._

    override val timeoutTime: FiniteDuration = 10 seconds
    private var dataReceived = List[String]()
    private var lastSender: ActorRef = _

    override def onPassivationCallback(): Unit = {
      lastSender ! "down"
    }

    override def receiveRecover: Receive = {
      case EvtSharded(_, s) =>
        dataReceived = s :: dataReceived
    }

    override def receiveCommand: Receive = {
      case CmdSharded(i, data) =>
        lastSender = sender()
        persist(EvtSharded(i, data)) { _ =>
          dataReceived = data :: dataReceived
          lastSender ! dataReceived.reverse
        }
    }


  }

}