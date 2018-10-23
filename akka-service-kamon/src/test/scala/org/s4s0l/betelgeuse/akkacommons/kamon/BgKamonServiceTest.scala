
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

package org.s4s0l.betelgeuse.akkacommons.kamon

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import kamon.Kamon
import kamon.metric.MeasurementUnit
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

/**
  * @author Marcin Wielgus
  */
class BgKamonServiceTest extends BgTestService {

  private val aService = testWith(new BgKamonService with BgClusteringSharding {

    lazy val props = Props(new Actor {
      override def receive: Receive = {
        case _ =>
          Thread.sleep(1000)
          Kamon.counter("my.funny.counter", MeasurementUnit.none)
            .refine("spmcustom" -> "mytag")
            .increment()
          sender() ! "pong"
      }
    })

    lazy val shardedActor: ActorRef = clusteringShardingExtension.start("cycki", props, {
      case msg: String => (msg, msg)
    })

    lazy val dupaActor: ActorRef = system.actorOf(props, "dupa")

  })
  aService.to = 20.seconds
  aService.timeout = 20.seconds

  feature("just a kamon test") {
    scenario("alala") {
      new WithService(aService) {
        for (_ <- 1 to 3) {
          val res = Await.result(service.dupaActor ? "ping", 20.seconds)
          assert(res == "pong")
          val res2 = Await.result(service.shardedActor ? s"ping${Random.nextInt(100)}", 20.seconds)
          assert(res2 == "pong")
        }

      }
    }
  }

}
