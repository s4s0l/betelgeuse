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



package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.Props
import akka.persistence.PersistentActor
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BetelgeuseAkkaClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.BetelgeuseAkkaPersistenceJournalCrate
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaTestWithCrateDb
import org.s4s0l.betelgeuse.akkacommons.utils.AsyncInitActorTest.SampleAsyncInitActor

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

object AsyncInitActorTest {

  class SampleAsyncInitActor extends PersistentActor with AsyncInitActor {

    var queue: List[String] = List[String]()


    override def preStart(): Unit = {
      queue = "preStart" :: queue
    }

    override def initialReceive: Receive = {
      case "init" =>
        queue = "initialReceive" :: queue
        initiationComplete()
        sender() ! queue.reverse
    }

    override def receiveRecover: Receive = {
      case _ =>
        queue = "receiveRecover" :: queue
    }

    override def receiveCommand: Receive = {
      case _ =>
        queue = "receiveCommand" :: queue
        persist("received") { _ =>
          sender() ! queue.reverse
        }
    }

    override def persistenceId: String = "1"
  }

}