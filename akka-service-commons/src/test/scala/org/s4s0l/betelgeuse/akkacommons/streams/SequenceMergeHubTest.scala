
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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestKit
import org.scalatest.FeatureSpecLike
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
class SequenceMergeHubTest
  extends TestKit(ActorSystem("SequenceMergeHubTest"))
    with FeatureSpecLike
    with ScalaFutures {
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(2.second, 300.millis)
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  feature("Can sequence all streams dynamic") {

    scenario("simple test") {
      val source = SequenceMergeHub.defineSource[Int, NotUsed](1)
      val sink = TestSink.probe[Int]
      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      val source1 = Source(List(1, 2, 3))
      val added = hub.addSource(source1)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)
      whenReady(added) { _ =>
        assert(hub.currentlyRunning().contains((source1, NotUsed.getInstance())))
      }

      eventualSeq.request(2).expectNext(2, 3)
      val source2 = hub.addSource(Source(List()))
      eventualSeq.request(2).expectNoMessage(1.second)
      whenReady(source2) { _ =>
        assert(!hub.currentlyRunning().contains((source1, NotUsed.getInstance())))
      }
      hub.addSource(Source(List(4, 5)))
      eventualSeq.expectNext(4)
      eventualSeq.request(1).expectNext(5)
      whenReady(hub.addSource(Source(List(6)))) { _ =>
        eventualSeq.expectNext(6)
        hub.killSource()
      }
      eventualSeq.expectComplete()
    }
  }

  feature("Can play next immediately") {
    scenario("simple test") {
      val source = SequenceMergeHub.defineSource[Int, NotUsed](1)
      val sink = TestSink.probe[Int]
      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      val source1 = Source(List(1, 2, 3))
      hub.addSource(source1)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)
      val source2 = Source(List(4, 5, 6))
      hub.runSource(source2)
      eventualSeq.request(1)
      eventualSeq.expectNext(2) //because of buffer == 1 we see one element from previous stream
      eventualSeq.request(1)
      eventualSeq.expectNext(4)
    }
  }

  feature("Will create default source when no source available in the hub") {
    scenario("simple test") {
      val source = SequenceMergeHub
        .defineSource[Int, NotUsed](1, Some(() => Source.repeat(42)))
      val sink = TestSink.probe[Int]

      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      val source1 = Source(List(1, 2, 3))
      hub.addSource(source1)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)
      eventualSeq.request(1)
      eventualSeq.expectNext(2)
      eventualSeq.request(1)
      eventualSeq.expectNext(3)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)

      hub.killSource()
      eventualSeq.request(1)
      eventualSeq.expectComplete()
    }


    scenario("playing default on demand") {
      val source = SequenceMergeHub
        .defineSource[Int, NotUsed](1, Some(() => Source.repeat(42)))
      val sink = TestSink.probe[Int]

      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      eventualSeq.request(1)
      eventualSeq.expectNoMessage(1.second)
      hub.playDefault()
      eventualSeq.expectNext(42)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)
      Thread.sleep(500) //merge hub is async
      //so we wait here to be sure buffer is populated
      //so the test is deterministic
      val source1 = Source(List(1, 2, 3))
      hub.addSource(source1)

      eventualSeq.request(1)
      eventualSeq.expectNext(42)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)
      eventualSeq.request(1)
      eventualSeq.expectNext(2)
      eventualSeq.request(1)
      eventualSeq.expectNext(3)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)

      hub.killSource()
      eventualSeq.request(1)
      eventualSeq.expectComplete()
    }

    scenario("removing default when adding new source") {
      val source = SequenceMergeHub
        .defineSource[Int, NotUsed](1, Some(() => Source.repeat(42)))
      val sink = TestSink.probe[Int]

      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      val source1 = Source(List(1, 2, 3))
      hub.addSource(source1)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)
      eventualSeq.request(1)
      eventualSeq.expectNext(2)
      eventualSeq.request(1)
      eventualSeq.expectNext(3)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)

      Thread.sleep(500) //merge hub is async
      //so we wait here to be sure buffer is populated
      //so the test is deterministic

      val source2 = Source(List(4, 5))
      hub.addSource(source2)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)
      eventualSeq.request(1)
      eventualSeq.expectNext(4)
      eventualSeq.request(1)
      eventualSeq.expectNext(5)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)


      hub.killSource()
      eventualSeq.request(1)
      eventualSeq.expectComplete()
    }


    scenario("removing default when running new source") {
      val source = SequenceMergeHub
        .defineSource[Int, NotUsed](1, Some(() => Source.repeat(42)))
      val sink = TestSink.probe[Int]

      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      val source1 = Source(List(1, 2, 3))
      hub.addSource(source1)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)
      eventualSeq.request(1)
      eventualSeq.expectNext(2)
      eventualSeq.request(1)
      eventualSeq.expectNext(3)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)

      Thread.sleep(500) //merge hub is async
      //so we wait here to be sure buffer is populated
      //so the test is deterministic

      val source2 = Source(List(4, 5))
      hub.runSource(source2)

      eventualSeq.request(1)
      eventualSeq.expectNext(42)

      eventualSeq.request(1)
      eventualSeq.expectNext(4)
      eventualSeq.request(1)
      eventualSeq.expectNext(5)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)


      hub.killSource()
      eventualSeq.request(1)
      eventualSeq.expectComplete()
    }

    scenario("default does not interfer when new streams are available") {
      val source = SequenceMergeHub
        .defineSource[Int, NotUsed](1, Some(() => Source.repeat(42)))
      val sink = TestSink.probe[Int]

      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      val source1 = Source(List(1, 2, 3))
      hub.addSource(source1)
      val source2 = Source(List(4, 5))
      hub.addSource(source2)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)
      eventualSeq.request(1)
      eventualSeq.expectNext(2)
      eventualSeq.request(1)
      eventualSeq.expectNext(3)
      eventualSeq.request(1)
      eventualSeq.expectNext(4)
      eventualSeq.request(1)
      eventualSeq.expectNext(5)
      eventualSeq.request(1)
      eventualSeq.expectNext(42)


      hub.killSource()
      eventualSeq.request(1)
      eventualSeq.expectComplete()
    }


    scenario("default is repeated when is finite") {
      var count = 99
      val source = SequenceMergeHub
        .defineSource[Int, NotUsed](1, Some(() => {
        count = count + 1
        Source(List(count, count))
      }))
      val sink = TestSink.probe[Int]

      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      val source1 = Source(List(1, 2, 3))
      hub.addSource(source1)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)
      eventualSeq.request(1)
      eventualSeq.expectNext(2)
      eventualSeq.request(1)
      eventualSeq.expectNext(3)
      eventualSeq.request(1)
      eventualSeq.expectNext(100)
      eventualSeq.request(1)
      eventualSeq.expectNext(100)
      eventualSeq.request(1)
      eventualSeq.expectNext(101)
      Thread.sleep(500) //merge hub is async
      //so we wait here to be sure buffer is populated
      //so the test is deterministic
      val source2 = Source(List(4, 5))
      hub.addSource(source2)

      eventualSeq.request(1)
      eventualSeq.expectNext(101)
      eventualSeq.request(1)
      eventualSeq.expectNext(102)
      eventualSeq.request(1)
      eventualSeq.expectNext(4)
      eventualSeq.request(1)
      eventualSeq.expectNext(5)
      eventualSeq.request(1)
      eventualSeq.expectNext(103)
      eventualSeq.request(1)
      eventualSeq.expectNext(103)
      eventualSeq.request(1)
      eventualSeq.expectNext(104)


      hub.killSource()
      eventualSeq.request(1)
      eventualSeq.expectComplete()
    }


  }

}
