/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 04:05
 *
 */

package org.s4s0l.betelgeuse.akkacommons.http

import akka.actor.Props
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.s4s0l.betelgeuse.akkacommons.test.BetelgeuseAkkaServiceSpecLike

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaHttpSessionTest extends BetelgeuseAkkaServiceSpecLike[BetelgeuseAkkaHttpSession] {
//  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def createService():BetelgeuseAkkaHttpSession = new BetelgeuseAkkaHttpSession {

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
