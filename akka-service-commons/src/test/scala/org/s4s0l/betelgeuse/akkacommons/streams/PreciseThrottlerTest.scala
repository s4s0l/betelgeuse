/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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

package org.s4s0l.betelgeuse.akkacommons.streams

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.s4s0l.betelgeuse.akkacommons.streams.IntervalStats._
import org.s4s0l.betelgeuse.akkacommons.streams.PreciseThrottler._
import org.scalatest.FeatureSpecLike
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
class PreciseThrottlerTest extends TestKit(ActorSystem("MySpec", ConfigFactory.parseString(
  ""
  //  """
  //    |akka.stream.materializer.dispatcher="my-dispatcher"
  //    |my-dispatcher {
  //    |  # Dispatcher is the name of the event-based dispatcher
  //    |  type = Dispatcher
  //    |  # What kind of ExecutionService to use
  //    |  executor = "fork-join-executor"
  //    |  # Configuration for the fork join pool
  //    |  fork-join-executor {
  //    |    # Min number of threads to cap factor-based parallelism number to
  //    |    parallelism-min = 20
  //    |    # Parallelism (threads) ... ceil(available processors * factor)
  //    |    parallelism-factor = 3.0
  //    |    # Max number of threads to cap factor-based parallelism number to
  //    |    parallelism-max = 40
  //    |  }
  //    |  # Throughput defines the maximum number of messages to be
  //    |  # processed per actor before the thread jumps to the next actor.
  //    |  # Set to 1 for as fair as possible.
  //    |  throughput = 1000
  //    |}
  //  """.stripMargin
))) with FeatureSpecLike
  with ScalaFutures {

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(25.second, 10.millis)
  private implicit val ec: ExecutionContext = system.dispatcher

  feature("precise stream throttling") {
    scenario("Here we see how regular throttle works, stddev > 17") {
      val silenceStream = Source(1 to 500)
        .throttle(1, 1.millis, maximumBurst = 100, costCalculation = _ => 20, mode = ThrottleMode.shaping)
        .viaIntervalStatsMat()
        .toMat(Sink.seq)(Keep.left)
        .run()
      whenReady(silenceStream.future) { x =>
        println(x)
        //if this test stops passing this means akka got better and we can
        //consider moving back to standard solution
        assert(x.stdDev > 15f)
      }
    }

    scenario("the new way") {
      val silenceStream = Source(1 to 500)
        .viaPreciseThrottler(20.millis, 100)
        .viaIntervalStatsMat()
        .toMat(Sink.seq)(Keep.left)
        .run()


      whenReady(silenceStream.future) { x =>
        println(x)
        assert(x.avgInterval >= 18)
        assert(x.avgInterval <= 22)
        assert(x.stdDev <= 10)
      }

    }


    scenario("concurrent") {
      var count = 0
      var sum = 0d
      val silenceStream = (1 to 50).map(_ => Source(1 to 500)
        .viaPreciseThrottler(20.millis, 100)
        .viaIntervalStatsMat()
        .toMat(Sink.seq)(Keep.left)
        .run())

      silenceStream.foreach(it => whenReady(it.future) { x =>
        println(x)
        count = count + 1
        sum = sum + x.stdDev
      }
      )
      println("WARM-UP SCORE:" + (sum / count))
      count = 0
      sum = 0d

      val silenceStream2 = (1 to 150).map(_ => Source(1 to 500)
        .viaPreciseThrottler(20.millis, 100)
        .viaIntervalStatsMat()
        .toMat(Sink.seq)(Keep.left)
        .run())

      silenceStream2.foreach(it => whenReady(it.future) { x =>
        println(x)
        count = count + 1
        sum = sum + x.stdDev
      }
      )
      println("OVERALL SCORE:" + (sum / count))
    }


    scenario("the new way with akka scheduler") {
      val silenceStream = Source(1 to 500)
        .viaPreciseThrottlerAkka(20.millis, 100)
        .viaIntervalStatsMat()
        .toMat(Sink.seq)(Keep.left)
        .run()


      whenReady(silenceStream.future) { x =>
        println(x)
        assert(x.avgInterval >= 18)
        assert(x.avgInterval <= 22)
        assert(x.stdDev <= 10)
      }
      Thread.sleep(3000)
    }

    scenario("the new way with akka scheduler - slow consumer") {

      val d = Source(0 to 10000)
        .viaPreciseThrottlerAkka(20.millis, 100)
        .toMat(TestSink.probe[Int])(Keep.right)
        .run()

      d.request(1)
      d.expectNext(0)
      d.expectNoMessage(500.millis)
      d.request(1)
      d.expectNext(1)
      d.expectNoMessage(500.millis)

    }


    scenario("the new way with akka scheduler - slow producer") {

      val (s, d) = TestSource.probe[Int]
        .viaPreciseThrottlerAkka(20.millis, 100)
        .toMat(TestSink.probe[Int])(Keep.both)
        .run()

      d.request(10)
      d.expectNoMessage(500.millis)
      s.sendNext(42)
      d.expectNext(41.millis, 42)
      d.expectNoMessage(500.millis)
      s.sendNext(43)
      s.sendNext(44)
      s.sendNext(45)
      s.sendNext(46)
      s.sendNext(47)
      d.expectNext(43, 44, 45, 46, 47)

      s.sendComplete()
      d.expectComplete()
    }

    scenario("concurrent akka scheduler") {
      var count = 0
      var sum = 0d
      val silenceStream = (1 to 50).map(_ => Source(1 to 500)
        .viaPreciseThrottlerAkka(20.millis, 100)
        .viaIntervalStatsMat()
        .toMat(Sink.seq)(Keep.left)
        .run())

      silenceStream.foreach(it => whenReady(it.future) { x =>
        println(x)
        count = count + 1
        sum = sum + x.stdDev
      }
      )
      println("WARM-UP SCORE:" + (sum / count))
      count = 0
      sum = 0d
      val silenceStream2 = (1 to 150).map(_ => Source(1 to 500)
        .viaPreciseThrottlerAkka(20.millis, 100)
        .viaIntervalStatsMat()
        .toMat(Sink.seq)(Keep.left)
        .run())

      silenceStream2.foreach(it => whenReady(it.future) { x =>
        println(x)
        count = count + 1
        sum = sum + x.stdDev
      }
      )
      println("OVERALL SCORE:" + (sum / count))
    }


  }
}


object PreciseThrottlerTest {


}
