/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.PersistentShardedActor
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.s4s0l.betelgeuse.akkacommons.utils.AsyncInitActorTest.{ComplexAsyncInitActor, SampleAsyncInitActor, SimpleAsyncInitActor}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class AsyncInitActorTest extends BgTestRoach {

  private val aService = testWith(new BgPersistenceJournalRoach with BgClusteringSharding {

  })


  feature("Journal roach provides journal configuration to be used by actors") {
    scenario("Actor can init itself before recovery") {
      new WithService(aService) {

        private val actor = system.actorOf(Props(new SampleAsyncInitActor))
        actor ! "init"
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive"))
        actor ! "command"

        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "rec:command"))

        private val actor2 = system.actorOf(Props(new SampleAsyncInitActor))
        actor2 ! "init"
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive"))
        actor2 ! "command"

        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "receiveRecover", "rec:command"))

      }
    }

    scenario("Messages received during init are replayed in proper order after initialization") {
      new WithService(aService) {

        private val actor = system.actorOf(Props(new SimpleAsyncInitActor))
        actor ! "1"
        actor ! "2"
        actor ! "init"
        actor ! "3"
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "rec:1"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "rec:1", "rec:2"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "rec:1", "rec:2", "rec:3"))

      }

    }

    scenario("Messages received during init are replayed in proper order after initialization for persistent actors") {
      new WithService(aService) {

        private val actor = system.actorOf(Props(new SampleAsyncInitActor("2")))
        actor ! "1"
        actor ! "2"
        actor ! "init"
        actor ! "3"

        testKit.expectMsg(14 seconds, List("preStart", "initialReceive"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "rec:1"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "rec:1", "rec:2"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "rec:1", "rec:2", "rec:3"))
      }

    }


    scenario("Messages received during init are replayed in proper order after initialization for complex actors") {
      new WithService(aService) {

        private val actor = service.clusteringShardingExtension.start("x", Props(new ComplexAsyncInitActor()), {
          case (a: String, b: String) => (a, (a, b))
        })
        actor ! ("a", "1")
        Thread.sleep(3000)
        actor ! ("a", "2")
        actor ! ("a", "init")
        actor ! ("a", "3")

        testKit.expectMsg(14 seconds, List("preStart", "initialReceive"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "rec:1"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "rec:1", "rec:2"))
        testKit.expectMsg(14 seconds, List("preStart", "initialReceive", "receiveRecover", "rec:1", "rec:2", "rec:3"))
      }

    }
  }

}

object AsyncInitActorTest {

  class SimpleAsyncInitActor extends AsyncInitActor {
    var queue: List[String] = List[String]()

    override def preStart(): Unit = {
      super.preStart()
      queue = "preStart" :: queue

    }

    override def initialReceive: Receive = {
      case "init" =>
        queue = "initialReceive" :: queue
        initiationComplete()
        sender() ! queue.reverse
    }

    override def receive: Receive = {
      case a =>
        queue = s"rec:$a" :: queue
        sender() ! queue.reverse
    }
  }

  class SampleAsyncInitActor(id: String = "1") extends PersistentActor with TimeoutActor with AsyncInitActor {

    var queue: List[String] = List[String]()


    override def preStart(): Unit = {
      super.preStart()
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
      case a =>
        queue = s"rec:$a" :: queue
        persist("received") { _ =>
          sender() ! queue.reverse
        }
    }

    override def persistenceId: String = id
  }


  class ComplexAsyncInitActor extends PersistentShardedActor with AsyncInitActor
    //  with TimeoutShardedActor
    with ActorLogging {

    var queue: List[String] = List[String]()


    override def preStart(): Unit = {
      super.preStart()
      queue = "preStart" :: queue

    }

    override def initialReceive: Receive = {
      case (_, "init") =>
        queue = "initialReceive" :: queue
        initiationComplete()
        sender() ! queue.reverse
    }

    override def receiveRecover: Receive = {
      case _ =>
        queue = "receiveRecover" :: queue
    }

    override def receiveCommand: Receive = {
      case (_, a) =>
        queue = s"rec:$a" :: queue
        persist("received") { _ =>
          sender() ! queue.reverse
        }
    }

  }

}