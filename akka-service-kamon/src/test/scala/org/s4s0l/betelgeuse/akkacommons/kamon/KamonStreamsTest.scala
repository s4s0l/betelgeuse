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

import akka.stream.scaladsl.{Sink, Source}
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
class KamonStreamsTest extends BgTestService with ScalaFutures {
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(20.second, 300.millis)
  private val aService = testWith(new BgKamonService with BgClusteringSharding {

  })

  feature("Collecting akka stream stats to kamon") {
    scenario("just a smoke test") {
      new WithService(aService) {

        import KamonStreams._

        val streamName1 = "test1"
        val bm1 = StreamBasicMetrics(streamName1)
        val fm1 = StreamInitiationMetrics(streamName1)
        val im1 = StreamIntervalMetrics(streamName1)
        val dm1 = StreamDemandLatencyMetrics(streamName1)

        val streamName2 = "test2"
        val bm2 = StreamBasicMetrics(streamName2)
        val fm2 = StreamInitiationMetrics(streamName2)
        val im2 = StreamIntervalMetrics(streamName2)
        val dm2 = StreamDemandLatencyMetrics(streamName2)

        for (_ <- 1 to 3) {
          val stream1 = Source(1 to 100)
            .map { it =>
              Thread.sleep(10)
              it
            }
            .withBasicMetrics(bm1)
            .withDemandLatencyMetrics(dm1)
            .withInitiationMetrics(fm1)
            .withIntervalMetrics(im1)
          val fd1 = stream1.runWith(Sink.ignore)
          val stream2 = Source(1 to 100)
            .map { it =>
              Thread.sleep(10)
              it
            }
            .withBasicMetrics(bm2)
            .withDemandLatencyMetrics(dm2)
            .withInitiationMetrics(fm2)
            .withIntervalMetrics(im2)
          val fd2 = stream2.runWith(Sink.ignore)
          whenReady(fd1) { _ => }
          whenReady(fd2) { _ => }
        }
        Thread.sleep(3100) //to see metrics in logs
        //todo validate them
      }
    }
  }
}

