/*
 * Copyright© 2019 by Ravenetics Sp. z o.o. - All Rights Reserved
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

package org.s4s0l.betelgeuse.akkacommons.tools

import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class ScalaScriptEngineActorTest extends BgTestService {

  private val aService = testWith(new BgService {
    lazy val scriptEngine: ScalaScriptEngineActor.Protocol = ScalaScriptEngineActor.start()
  })

  feature("Scala script engine actor can perform some scala scripting") {
    scenario("Executes test successfully") {
      new WithService(aService) {
        Given("script")
        private val script = "1+1"
        When("Script is evaluated")
        private val res = service.scriptEngine.eval[Int](script)(execContext, 20 seconds)
        Then("script evaluation succeeds, first time it may take longer")
        assert(Await.result(res, 20 seconds) == 2)
        When("Script is evaluated again")
        private val res2 = service.scriptEngine.eval[Int]("2+2")(execContext, 1 second)
        Then("script evaluation succeeds, response is blazing fast...")
        assert(Await.result(res2, 1 second) == 4)
      }

    }
  }
}
