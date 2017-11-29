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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import akka.actor.{ActorRef, Props}
import akka.pattern.BackoffSupervisor.{CurrentChild, GetCurrentChild}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.{BetelgeuseAkkaClusteringSharding, BetelgeuseAkkaClusteringShardingExtension}
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BetelgeuseAkkaPersistenceJournalCrateTest._
import org.s4s0l.betelgeuse.akkacommons.persistence.utils
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaTestWithCrateDb
import org.s4s0l.betelgeuse.akkacommons.utils.TimeoutShardedActor

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaPersistenceJournalCrateTest extends BetelgeuseAkkaTestWithCrateDb[BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringSharding] {
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def createService(): BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringSharding = new BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringSharding {

  }

  feature("Journal crate provides journal configuration to be used by actors") {
    scenario("Actor persists itself ad responds") {
      LOGGER.info("actor creation")
      val actor = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 123))
      LOGGER.info("actor created")
      assert(actor != null)

      actor ! Cmd("Alala")
      LOGGER.info("Send message")
      testKit.expectMsg(60 seconds, (1, List("Alala-0")))
      actor ! Cmd("Alala")
      LOGGER.info("Send message")
      testKit.expectMsg(60 seconds, (2, List("Alala-0", "Alala-1")))

      LOGGER.info("actor creation")
      val actor2 = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 456))
      LOGGER.info("actor created")
      actor2 ! Cmd("Alala")
      LOGGER.info("Send message")
      testKit.expectMsg(60 seconds, (1, List("Alala-0")))

    }


    scenario("Duplicated sequence nr") {
      import scala.concurrent.duration._
      val actor = system.actorOf(Props.apply(classOf[ExamplePersistentActor], 5))
      val childProps = Props.apply(classOf[ExamplePersistentActor], 5)
      val props = BackoffSupervisor.props(
        Backoff.onStop(
          childProps,
          childName = "myActor",
          minBackoff = 1.millisecond,
          maxBackoff = 1.millisecond,
          randomFactor = 0.001))
      val supervicos = system.actorOf(props, name = "mySupervisor")
      //we wait just to be sure both actors got created, ugly...BackoffSupervisor is ugly anyway...
      Thread.sleep(2000)

      actor ! Cmd("Alala")
      testKit.expectMsg(60 seconds, (1, List("Alala-0")))
      implicit val timeout: Timeout = 10 seconds
      import akka.pattern.ask
      Await.ready(
        (supervicos ? GetCurrentChild).andThen {
          case Success(CurrentChild(Some(c))) => c ! Cmd("Alala1")
        }, 20 seconds)

      testKit.expectNoMsg(1 second)


      Await.ready(
        (supervicos ? GetCurrentChild).andThen {
          case Success(CurrentChild(Some(c))) => c ! Cmd("Alala2")
        }, 20 seconds)

      testKit.expectMsg(60 seconds, (2, List("Alala-0", "Alala2-1")))

    }


  }

  feature("Journal is capable of supporting sharded actors") {

    scenario("sharded actor is persistent with passivation timeout and can be restored") {
      val sharding = BetelgeuseAkkaClusteringShardingExtension(system)
      val shard = sharding.start("aaaaa", Props[ExamplePersistentShardedActor], {
        case c@CmdSharded(i, _) => (i.toString, c)
      })
      LOGGER.info("Shard started?")
      Thread.sleep(10000)
      shard ! CmdSharded(1, "ala")
      LOGGER.info("Message sent")
      testKit.expectMsg(5 seconds,List("ala"))
      testKit.expectMsg(20 seconds, "down")
      shard ! CmdSharded(1, "ma")
      testKit.expectMsg(2 seconds,List("ala","ma"))
      shard ! CmdSharded(1, "kota")
      testKit.expectMsg(2 seconds,List("ala","ma", "kota"))
    }

  }

}


object BetelgeuseAkkaPersistenceJournalCrateTest {

  case class Cmd(data: String)

  case class Evt(data: String)

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
    private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)
    val snapShotInterval = 1000
    val receiveCommand: Receive = {
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

  case class EvtSharded(num: Int, data: String)


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