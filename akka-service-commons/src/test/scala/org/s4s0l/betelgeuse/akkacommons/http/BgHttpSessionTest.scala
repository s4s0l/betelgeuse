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



package org.s4s0l.betelgeuse.akkacommons.http

import akka.actor.Props
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.s4s0l.betelgeuse.akkacommons.test.BgServiceSpecLike

/**
  * @author Marcin Wielgus
  */
class BgHttpSessionTest extends BgServiceSpecLike[BgHttpSession] {
//  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def createService(): BgHttpSession = new BgHttpSession {

    lazy val sessionProtocol: SessionManagerActor.Protocol = httpSessionExtension.createSessionProtocol {
      case _ => Props()
    }

    override def httpRoute: Route = {
      get {
        httpSessionExtension.httpSessionGuardedWithActors(sessionProtocol) {
          _ => complete("ok")
        }
      } ~ super.httpRoute

    }
  }

  feature("Manages sessions") {
    scenario("With some stuff todo finish") {
    }
  }

}
