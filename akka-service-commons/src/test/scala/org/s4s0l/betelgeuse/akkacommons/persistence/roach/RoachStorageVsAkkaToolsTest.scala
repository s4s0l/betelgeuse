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

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.ShardRegion.StartEntity
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.RoachStorageVsAkkaToolsTest._
import org.s4s0l.betelgeuse.akkacommons.serialization.{BgSerializationJackson, JacksonJsonSerializable}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.s4s0l.betelgeuse.akkacommons.utils.ShardedActor

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.reflect.{ClassTag, _}

/**
  * @author Marcin Wielgus
  */
class RoachStorageVsAkkaToolsTest
  extends
    BgTestRoach {


  private val aService = testWith(new
      BgPersistenceJournalRoach
        with BgSerializationJackson
        with BgPersistenceSnapStoreRoach
        with BgClusteringSharding {
  })


  feature("FSM persistence works") {

    scenario("sharding remember entities") {
      val ref = aService.service.clusteringShardingExtension
        .start("sampleShard", Props(new ShardedActor {
          val wasStarted = System.currentTimeMillis()

          override def receive: Receive = {
            case "kill" =>
              context.stop(self)
              sender() ! (wasStarted, self)
            case _: String => sender() ! (wasStarted, self)
          }
        }), {
          case string: String => (string, string)
          case se@StartEntity(eee) => (eee, se)
        })

      import akka.pattern.ask
      implicit val ec: ExecutionContextExecutor = aService.execContext
      implicit val to: FiniteDuration = 10.seconds
      implicit val timeout: Timeout = to
      Await.result(ref ? "aaa", to)
      Await.result(ref ? "aaa", to)
      Await.result(ref ? "aaa", to)
      Await.result(ref ? "aaa", to)
      Await.result(ref ? "aaa", to)
      Await.result(ref ? "aaa", to)
      Await.result(ref ? "aaa", to)
      Thread.sleep(1000)
      val x = Await.result(ref ? "aaa", to).asInstanceOf[(Long, ActorRef)]
      Await.result(x._2 ? "kill", to)
      Thread.sleep(2500)
      val x2 = Await.result(ref ? "aaa", to).asInstanceOf[(Long, ActorRef)]
      assert(x2._1 < 2500L)

    }


    scenario("Simple fsm can be persisted") {

      def start(logTag: String) = aService.system.actorOf(Props(
        new Actor
          with PersistentFSM[State, Data, Event] {

          override def domainEventClassTag: ClassTag[RoachStorageVsAkkaToolsTest.Event]
          = classTag[RoachStorageVsAkkaToolsTest.Event]

          override def applyEvent(domainEvent: RoachStorageVsAkkaToolsTest.Event,
                                  currentData: Data): Data = {
            domainEvent match {
              case EventImpl(xx, true) =>
                log.debug(s"As $logTag apply A total count = ${currentData.aCount + currentData.bCount}")
                currentData.copy(
                  aCount = currentData.aCount + 1,
                  messages = xx :: currentData.messages
                )
              case EventImpl(xx, false) =>
                log.debug(s"As $logTag apply B total count ${currentData.aCount + currentData.bCount}")
                currentData.copy(
                  bCount = currentData.bCount + 1,
                  messages = xx :: currentData.messages
                )
            }
          }

          override def persistenceId: String = "justAnSampleActor"

          when(StateA) {
            case Event(_: Boolean, d) =>
              sender() ! d
              stay()
            case Event(message: String, _) =>
              goto(StateB) applying EventImpl(message, ab = true)
          }

          when(StateB) {
            case Event(_: Boolean, d) =>
              sender() ! d
              stay()
            case Event(message: String, _) =>
              goto(StateA) applying EventImpl(message, ab = false)
          }

          whenUnhandled {
            case Event(_: SaveSnapshotSuccess, _) => stay()
          }

          startWith(StateA, Data(0, 0, List()))

        }
      ), "sample")

      val ref = start("one")

      import akka.pattern.ask
      implicit val ec: ExecutionContextExecutor = aService.execContext
      implicit val to: FiniteDuration = 10.seconds
      implicit val timeout: Timeout = to
      Await.result(ref ? true, to)
      ref ! "a"
      ref ! "b"
      ref ! "c"
      ref ! "d"
      ref ! "e"
      ref ! "f"
      ref ! "g"
      ref ! "h"
      ref ! "i"
      ref ! "j"
      val data = Await.result(ref ? true, to)
      assert(data == Data(5, 5, List(
        "a", "b", "c", "d",
        "e", "f", "g", "h",
        "i", "j"
      ).reverse))


      Thread.sleep(7000)
      aService.system.stop(ref)

      Thread.sleep(1000)

      val ref2 = start("two")
      val data2 = Await.result(ref2 ? true, to)
      assert(data2 == data)
    }
  }
}

object RoachStorageVsAkkaToolsTest {

  trait State extends FSMState

  case object StateA extends State {
    override def identifier: String = "stateA"
  }

  case object StateB extends State {
    override def identifier: String = "stateB"
  }

  case class Data(aCount: Int, bCount: Int, messages: List[String])
    extends JacksonJsonSerializable

  trait Event extends JacksonJsonSerializable

  case class EventImpl(msg: String, ab: Boolean) extends Event

}
