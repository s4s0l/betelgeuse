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

import akka.actor.{ActorRef, PoisonPill, Props}
import akka.pattern.BackoffSupervisor.{CurrentChild, GetCurrentChild}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.{BgClusteringSharding, BgClusteringShardingExtension}
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceSingleJournalRoachTest._
import org.s4s0l.betelgeuse.akkacommons.persistence.utils
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.s4s0l.betelgeuse.akkacommons.utils.TimeoutShardedActor

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

/**
  * @author Maciej Flak
  */
class BgPersistenceSingleJournalRoachTest
  extends BgTestRoach {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private val aService = testWith(
    new BgPersistenceJournalRoach
      with BgClusteringSharding {}
  )

  feature("Journal roach provides journal configuration to be used by actors") {
    scenario("Actor persists itself ad responds") {
      new WithService(aService) {

        Given("Actor with single event persistence")
        private val actor = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 123))

        When("Sending a command")
        actor ! Cmd("Alala")
        Then("It reports One Event Seen")
        testKit.expectMsg(60 seconds, (1, List("Alala-0")))
        When("Sending a command again")
        actor ! Cmd("Alala")
        Then("It reports Two Events Seen")
        testKit.expectMsg(60 seconds, (2, List("Alala-0", "Alala-1")))
        When("We kill the actor and create it again")
        actor ! PoisonPill
        private val actor3 = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 123))
        And("We send something to it again")
        actor3 ! Cmd("Third")
        Then("Actor reports only two events - last from before the kill and a new one")
        testKit.expectMsg(60 seconds, (3, List("RECOVER:Alala-1", "Third-1")))

      }
    }


    scenario("Duplicated sequence nr") {
      new WithService(aService) {

        import scala.concurrent.duration._

        Given("An actor with single event persistence")
        private val actor = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 5))

        And("Another actor with same id and tag")
        private val childProps = Props.apply(classOf[ExamplePersistentActor], 5)
        private val props = BackoffSupervisor.props(
          Backoff.onStop(
            childProps,
            childName = "myActor",
            minBackoff = 1.millisecond,
            maxBackoff = 1.millisecond,
            randomFactor = 0.001))

        And("The second is wrapped with back off supervisor strategy")
        private val supervicos = system.actorOf(props, name = "mySupervisor")
        //we wait just to be sure both actors got created, ugly...BackoffSupervisor is ugly anyway...
        Thread.sleep(2000)

        When("We send an event to the first one")
        actor ! Cmd("Alala")

        Then("It confirms")
        testKit.expectMsg(60 seconds, (1, List("Alala-0")))

        When("We send message to the other")
        override implicit val timeout: Timeout = 10 seconds

        import akka.pattern.ask

        Await.ready(
          (supervicos ? GetCurrentChild).andThen {
            case Success(CurrentChild(Some(c))) => c ! Cmd("Alala1")
          }, 20 seconds)

        Then("It does not confirm, we assume it died because of persistence failure, and got restarted by supervisor")
        testKit.expectNoMessage(1 second)


        When("We hit it again")
        Await.ready(
          (supervicos ? GetCurrentChild).andThen {
            case Success(CurrentChild(Some(c))) => c ! Cmd("Alala2")
          }, 20 seconds)

        Then("It responds wit confirmation for last message and with one message recovered from journal - it is the one send to the first actor")
        testKit.expectMsg(60 seconds, (2, List("RECOVER:Alala-0", "Alala2-1")))

      }
    }


  }

  feature("Journal is capable of supporting sharded actors") {

    scenario("sharded actor is persistent with passivation timeout and can be restored") {
      new WithService(aService) {

        Given("A sharded actor with single event persistenceK")
        private val sharding = BgClusteringShardingExtension(system)
        private val shard = sharding.start("someTag", Props[ExamplePersistentShardedActor], {
          case c@CmdSharded(i, _) => (i.toString, c)
        })
        And("Shard started?")
        Thread.sleep(10000)

        When("Sending ala event")
        shard ! CmdSharded(1, "ala")
        Then("We get confirmation for ala")
        testKit.expectMsg(5 seconds, List("ala"))
        When("Sending ma event")
        shard ! CmdSharded(1, "ma")
        Then("We get confirmation for ala ma")
        testKit.expectMsg(2 seconds, List("ala", "ma"))
        When("Waiting for passivation")
        testKit.expectMsg(20 seconds, "down")
        And("Sending kota")
        shard ! CmdSharded(1, "kota")
        Then("We see actor recovered just with ma")
        testKit.expectMsg(2 seconds, List(
          "RECOVER:ma",
          "kota"))
      }
    }
  }

}


object BgPersistenceSingleJournalRoachTest {

  case class Cmd(data: String)

  case class Evt(data: String) extends JacksonJsonSerializable

  case class ExampleState(events: List[String] = Nil) {

    def updated(evt: Evt): ExampleState = copy(evt.data :: events)

    def size: Int = events.length

    override def toString: String = events.reverse.toString
  }

  class ExamplePersistentActor(num: Int)
    extends PersistentActor
      with SingleEventPersistence {

    lazy val receiveRecover: Receive = {
      case evt: Evt => updateState(evt.copy(data = s"RECOVER:${evt.data}"))
      case SnapshotOffer(_, snapshot: ExampleState) => state = snapshot
    }
    lazy val receiveCommand: Receive = {
      case Cmd(data) =>
        val sndr = sender()
        LOGGER.info("received {}", data)
        persist(Evt(s"$data-$numEvents")) { event =>
          LOGGER.info("persisted {}", event)
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


  class ExamplePersistentShardedActor
    extends utils.PersistentShardedActor
      with SingleEventPersistence
      with TimeoutShardedActor {

    import scala.concurrent.duration._

    override val timeoutTime: FiniteDuration = 10 seconds
    private var dataReceived = List[String]()
    private var lastSender: ActorRef = _

    override def onPassivationCallback(): Unit = {
      lastSender ! "down"
    }

    override def receiveRecover: Receive = {
      case EvtSharded(_, s) =>
        dataReceived = "RECOVER:" + s :: dataReceived
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
