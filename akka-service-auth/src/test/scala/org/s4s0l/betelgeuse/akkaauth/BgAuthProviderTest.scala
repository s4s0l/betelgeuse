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

/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkaauth

import akka.Done
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Promise
import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
class BgAuthProviderTest extends BgTestRoach with ScalaFutures {
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.second, 300.millis)

  private val provider = testWith(new BgAuthProviderTestProvider())

  private val client = testWith(new BgAuthProviderTestClient())


  feature("Smoke test") {
    scenario("xxx") {
      val p = Promise[Done]()
      client.service.bgAuthOnPublicKeyAvailable {
        p.success(Done)
      }
      whenReady(p.future) { _ =>

      }
    }
  }

}
