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

  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  feature("Can sequence all streams dynamic") {
    scenario("simple test") {
      val source = SequenceMergeHub.defineSource[Int, NotUsed]()
      val sink = TestSink.probe[Int]
      val (hub, eventualSeq) = source.toMat(sink)(Keep.both).run()

      val source1 = Source(List(1, 2, 3))
      hub.addSource(source1)
      eventualSeq.request(1)
      eventualSeq.expectNext(1)

      assert(hub.running == Some((source1, NotUsed.getInstance())))

      eventualSeq.request(2).expectNext(2, 3)
      hub.addSource(Source(List()))
      eventualSeq.request(2).expectNoMessage(1.second)
      assert(hub.running != Some((source1, NotUsed.getInstance())))
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

}
