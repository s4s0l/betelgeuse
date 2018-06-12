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
        assert(hub.currentlyRunning() == Some((source1, NotUsed.getInstance())))
      }

      eventualSeq.request(2).expectNext(2, 3)
      val source2 = hub.addSource(Source(List()))
      eventualSeq.request(2).expectNoMessage(1.second)
      whenReady(source2) { _ =>
        assert(hub.currentlyRunning() != Some((source1, NotUsed.getInstance())))
      }
      hub.addSource(Source(List(4, 5)))
      eventualSeq.expectNext(4)
      eventualSeq.request(1).expectNext(5)
      whenReady(hub.addSource(Source(List(6)))) { _ =>
        hub.killSource()
      }
      eventualSeq.expectNext(6)
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

}
