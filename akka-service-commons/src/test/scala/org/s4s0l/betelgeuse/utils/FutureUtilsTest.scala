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

package org.s4s0l.betelgeuse.utils

import org.scalatest.FeatureSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class FutureUtilsTest extends FeatureSpec {
  feature("We can manipulate futures") {
    scenario("Converting list of futures to future of lists") {

      val tasks: List[() => String] = List(() => "a", () => "b")
      val listOfFutures = tasks.map { it =>
        Future(it())
      }
      val futureOfList = AllUtils.listOfFuturesToFutureOfList(listOfFutures)
      val results = Await.result(futureOfList, 1 second)
      assert(results == List("a", "b"))
    }

    scenario("Converting list of futures to future of lists when one future fails") {
      val tasks: List[() => String] = List(() => "a", () => throw new Exception("xxx"))
      val listOfFutures = tasks.map { it =>
        Future(it())
      }
      val futureOfList = AllUtils.listOfFuturesToFutureOfList(listOfFutures)
      assertThrows[Exception](Await.result(futureOfList, 1 second))
    }
  }
}
