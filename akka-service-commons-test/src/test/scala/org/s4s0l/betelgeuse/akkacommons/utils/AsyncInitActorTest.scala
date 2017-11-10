/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 04:05
 *
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.Props
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BetelgeuseAkkaClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BetelgeuseAkkaPersistenceJournalCrate
import org.s4s0l.betelgeuse.akkacommons.test.{BetelgeuseAkkaTestWithCrateDb, DbCrateTest}
import org.s4s0l.betelgeuse.akkacommons.utils.AsyncInitActorTestClasses.SampleAsyncInitActor
import scalikejdbc.DBSession

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class AsyncInitActorTest extends BetelgeuseAkkaTestWithCrateDb[BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringSharding] {
  override def createService(): BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringSharding = new BetelgeuseAkkaPersistenceJournalCrate with BetelgeuseAkkaClusteringSharding {

  }


  feature("Journal crate provides journal configuration to be used by actors") {
    scenario("Actor persists itself ad responds") {

      val actor = system.actorOf(Props(new SampleAsyncInitActor))
      actor ! "init"
      testKit.expectMsg(14 seconds, List("preStart", "initialReceive"))
      actor ! "command"

      testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "receiveCommand"))
      refreshTable("crate_async_write_journal_entity")
      val actor2 = system.actorOf(Props(new SampleAsyncInitActor))
      actor2 ! "init"
      testKit.expectMsg(14 seconds, List("preStart", "initialReceive"))
      actor2 ! "command"

      testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "receiveRecover", "receiveCommand"))

    }
  }

}
