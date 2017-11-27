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

package org.s4s0l.betelgeuse.akkacommons.patterns.nearcache

import akka.pattern.pipe
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaService
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol.{CacheValue, GetCacheValue}
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Settings
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaServiceSpecLike

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class CacheAccessActorTest extends BetelgeuseAkkaServiceSpecLike[BetelgeuseAkkaService] {
  override def createService(): BetelgeuseAkkaService = new BetelgeuseAkkaService {}

  feature("Near cache holds and reference to some value calculated from some value produced from incoming message") {
    scenario("Cached instance is removed") {
      val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t1",
        (a: Int) => a,
        (a: Float) => a.toString,
        (a: Int) => Future(Some(a.toFloat))
      ))
      accessor.apply(GetCacheValue(10)).pipeTo(self)
      testKit.expectMsg(defaultTimeout.duration, CacheValue(10, Left(Some("10.0"))))
    }

    scenario("Cache expires") {
      Given("Current real value is 'a'")
      var value = "a"
      Given("Cache has timeout of 1 second")
      import scala.concurrent.duration._
      val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t2",
        (a: Int) => a,
        (_: Float) => value,
        (a: Int) => Future(Some(a.toFloat)),
        1 second
      ))
      When("We get cache value")
      accessor.apply(GetCacheValue(10)).pipeTo(self)
      Then("Cached value is 'a'")
      testKit.expectMsg(defaultTimeout.duration, CacheValue(10, Left(Some("a"))))

      When("Current real value is changed to 'b'")
      value = "b"
      When("We get cached value instantaneously")
      accessor.apply(GetCacheValue(10)).pipeTo(self)
      Then("We still get cached value as 'a'")
      testKit.expectMsg(defaultTimeout.duration, CacheValue(10, Left(Some("a"))))

      When("We wait 1100 ms")
      Thread.sleep(1100)
      When("We get value again")
      accessor.apply(GetCacheValue(10)).pipeTo(self)
      Then("We see cached value as 'b'")
      testKit.expectMsg(defaultTimeout.duration, CacheValue(10, Left(Some("b"))))

    }


    scenario("Getting value results in no value to be cached") {
      val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t3",
        (a: Int) => a,
        (a: Float) => a.toString,
        (_: Int) => Future(None)
      ))
      accessor.apply(GetCacheValue(10)).pipeTo(self)
      testKit.expectMsg(defaultTimeout.duration, CacheValue(10, Left(None)))
    }

    scenario("Getting a value throws exception") {
      val ex = new Exception("ex!")
      val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t4",
        (a: Int) => a,
        (a: Float) => a.toString,
        (_: Int) => Future.failed(ex)
      ))
      accessor.apply(GetCacheValue(10)).pipeTo(self)
      testKit.expectMsg(defaultTimeout.duration, CacheValue(10, Right(ex)))
    }

    scenario("Enriching value throws exception") {
      val ex = new Exception("ex!")
      val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t5",
        (a: Int) => a,
        (_: Float) => throw ex,
        (a: Int) => Future(Some(a.toFloat))
      ))
      accessor.apply(GetCacheValue(10)).pipeTo(self)
      testKit.expectMsg(defaultTimeout.duration, CacheValue(10, Right(ex)))
    }
  }


}
