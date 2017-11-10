/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-13 14:45
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import akka.actor.Props
import akka.pattern.BackoffSupervisor.{CurrentChild, GetCurrentChild}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.{BetelgeuseAkkaClusteringSharding, BetelgeuseAkkaClusteringShardingExtension}
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaTestWithCrateDb

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
