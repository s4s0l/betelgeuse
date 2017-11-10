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

package org.s4s0l.betelgeuse.akkacommons.utils

import org.s4s0l.betelgeuse.akkacommons.utils.CommonMessages.Headers
import org.scalatest.FeatureSpec

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
class CommonMessagesTest extends FeatureSpec {

  feature("Messages can have ttl") {
    scenario("Setting and getting ttl") {
      val x = Headers().withTtl(1 second)
      assert(x.ttlOpt.isDefined)
      assert(!x.ttl.isOverdue())
      Thread.sleep(1010)
      assert(x.ttl.isOverdue())
    }
  }

}
