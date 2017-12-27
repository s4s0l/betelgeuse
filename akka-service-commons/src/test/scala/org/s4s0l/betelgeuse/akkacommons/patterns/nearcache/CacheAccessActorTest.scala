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

package org.s4s0l.betelgeuse.akkacommons.patterns.nearcache

import akka.actor.ActorRef
import akka.pattern.pipe
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.Protocol.{GetCacheValue, GetCacheValueNotOk, GetCacheValueOk, GetCacheValueResult}
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActor.{Settings, ValueOwnerFacade}
import org.s4s0l.betelgeuse.akkacommons.patterns.nearcache.CacheAccessActorTest.VOF
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class CacheAccessActorTest extends BgTestService {
  private val aService = testWith(new BgService {})

  feature("Near cache holds and reference to some value calculated from some value produced from incoming message") {
    scenario("Cached instance is reachable and cached indeed") {
      new WithService(aService) {
        Given("Some cache")
        @volatile
        private var valueAccess = 0
        @volatile
        private var enrichmentCount = 0
        private val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t1",
          (a: Int) => a,
          (a: Float) => Future {
            enrichmentCount = enrichmentCount + 1
            a.toString
          },
          VOF((a: Int) => {
            valueAccess = valueAccess + 1
            Future(ValueOwnerFacade.OwnerValueOk(a, a.toFloat))
          })
        ))
        When("We ask it for a value")
        private val value = GetCacheValue(10)
        accessor.apply(value).pipeTo(self)
        Then("We got enriched value")
        testKit.expectMsg(to, GetCacheValueOk(value.messageId, "10.0"))
        And("Value is build from owners data")
        assert(valueAccess == 1)
        And("is enriched")
        assert(enrichmentCount == 1)

        When("We access same value again")
        private val value1 = GetCacheValue(10)
        accessor.apply(value1).pipeTo(self)
        Then("We get the same result")
        testKit.expectMsg(to, GetCacheValueOk(value1.messageId, "10.0"))
        And("Owner was not bothered again")
        assert(valueAccess == 1)
        And("Enrichment was not performed")
        assert(enrichmentCount == 1)
      }
    }

    scenario("Cache expires") {
      new WithService(aService) {
        Given("Current real value is 'a'")
        private var value = "a"
        Given("Cache has timeout of 1 second")


        private val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t2",
          (a: Int) => a,
          (_: Float) => Future(value),
          VOF((a: Int) => Future(ValueOwnerFacade.OwnerValueOk(a, a.toFloat))),
          1 second
        ))
        When("We get cache value")
        val get1 = GetCacheValue(10)
        accessor.apply(get1).pipeTo(self)
        Then("Cached value is 'a'")
        testKit.expectMsg(to, GetCacheValueOk(get1.messageId, "a"))

        When("Current real value is changed to 'b'")
        value = "b"
        When("We get cached value instantaneously")
        val get2 = GetCacheValue(10)
        accessor.apply(get2).pipeTo(self)
        Then("We still get cached value as 'a'")
        testKit.expectMsg(to, GetCacheValueOk(get2.messageId, "a"))

        When("We wait 1100 ms")
        Thread.sleep(1100)
        When("We get value again")
        val get3 = GetCacheValue(10)
        accessor.apply(get3).pipeTo(self)
        Then("We see cached value as 'b'")

        testKit.expectMsg(to, GetCacheValueOk(get3.messageId, "b"))
      }
    }


    scenario("Getting value results when value is missing") {
      new WithService(aService) {
        private val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t3",
          (a: Int) => a,
          (a: Float) => Future(a.toString),
          VOF((a: Int) => Future(ValueOwnerFacade.OwnerValueNotOk(a, new Exception("No Value"))))
        ))
        private val value = GetCacheValue(10)
        accessor.apply(value).pipeTo(self)
        assert(testKit.expectMsgClass(to, classOf[GetCacheValueNotOk[Float]]).ex.getMessage == "No Value")
      }
    }

    scenario("Getting a value throws exception") {
      new WithService(aService) {
        private val ex = new Exception("ex!")
        private val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t4",
          (a: Int) => a,
          (a: Float) => Future(a.toString),
          VOF((_: Int) => Future.failed(ex))
        ))
        private val value = GetCacheValue(10)
        accessor.apply(value).pipeTo(self)
        testKit.expectMsg(to, GetCacheValueNotOk(value.messageId, ex))
      }
    }

    scenario("Value enriching future throws exception") {
      new WithService(aService) {
        val ex = new Exception("ex!")
        private val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t5",
          (a: Int) => a,
          (_: Float) => Future(throw ex),
          VOF((a: Int) => Future(ValueOwnerFacade.OwnerValueOk(a, a.toFloat)))
        ))
        private val value = GetCacheValue(10)
        accessor.apply(value).pipeTo(self)
        testKit.expectMsg(to, GetCacheValueNotOk(value.messageId, ex))
      }
    }


    scenario("Value enriching throws exception") {
      new WithService(aService) {
        val ex = new Exception("ex!")
        private val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t5-duo",
          (a: Int) => a,
          (_: Float) => throw ex,
          VOF((a: Int) => Future(ValueOwnerFacade.OwnerValueOk(a, a.toFloat)))
        ))
        private val value = GetCacheValue(10)
        accessor.apply(value).pipeTo(self)
        testKit.expectMsg(to, GetCacheValueNotOk(value.messageId, ex))
      }
    }

    scenario("Messages requesting cache value are queued when value is not in the cache and needs to be retrieved from owner") {
      new WithService(aService) {
        @volatile
        private var valueAccess = 0
        @volatile
        private var enrichmentCount = 0

        Given("Slow owner facade")
        private val accessor = CacheAccessActor.start[Int, Int, String, Float](Settings("t6",
          (a: Int) => a,
          (a: Float) => Future {
            enrichmentCount = enrichmentCount + 1
            a.toString
          },
          VOF((a: Int) => {
            valueAccess = valueAccess + 1
            Thread.sleep(1100)
            Future(ValueOwnerFacade.OwnerValueOk(a, a.toFloat))
          })
        ))
        When("We ask it for a value")
        private val value1 = GetCacheValue(10)
        accessor.apply(value1).pipeTo(self)
        private val value2 = GetCacheValue(10)
        accessor.apply(value2).pipeTo(self)
        private val value3 = GetCacheValue(10)
        accessor.apply(value3).pipeTo(self)
        Then("We no immediate response")
        testKit.expectNoMsg(1 second)

        And("We do get answers finally")
        private val valuesCollected = mutable.Set[GetCacheValueResult[String]]()

        valuesCollected.add(testKit.expectMsgClass(to, classOf[GetCacheValueOk[String]]))
        valuesCollected.add(testKit.expectMsgClass(to, classOf[GetCacheValueOk[String]]))
        valuesCollected.add(testKit.expectMsgClass(to, classOf[GetCacheValueOk[String]]))


        assert(valuesCollected.contains(GetCacheValueOk(value1.messageId, "10.0")))
        assert(valuesCollected.contains(GetCacheValueOk(value2.messageId, "10.0")))
        assert(valuesCollected.contains(GetCacheValueOk(value3.messageId, "10.0")))


        And("Value is build from owners data only once ")
        assert(valueAccess == 1)
        assert(enrichmentCount == 1)

      }
    }
  }


}


object CacheAccessActorTest {

  case class VOF(x: Int => Future[ValueOwnerFacade.OwnerValueResult[Int, Float]]) extends ValueOwnerFacade[Int, Int, Float] {
    override def apply(getterMessage: Int)
                      (implicit executionContext: ExecutionContext, sender: ActorRef)
    : Future[ValueOwnerFacade.OwnerValueResult[Int, Float]] = {
      x(getterMessage)
    }
  }

}