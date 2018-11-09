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
package org.s4s0l.betelgeuse.akkaauth

import akka.http.scaladsl.server.Directives.{complete, get, path, pathPrefix, post, _}
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkaauth.audit.BgAuthClientStreamingAudit
import org.s4s0l.betelgeuse.akkaauth.common.Grant
import org.s4s0l.betelgeuse.akkaauth.manager.AdditionalUserAttrsManager
import org.s4s0l.betelgeuse.akkacommons.BgServiceId
import org.s4s0l.betelgeuse.akkacommons.http.BgHttp


/**
  * @author Marcin Wielgus
  */
class BgAuthProviderTestClient
  extends BgAuthClient[String]
    with BgAuthClientStreamingAudit[String]
    with BgHttp {

  override protected def systemName: String = "BgAuthProviderTestClient"

  override protected def portBase: Int = 2

  override def customizeConfiguration
  : Config = super.customizeConfiguration
    .withFallback(clusteringClientCreateConfig(bgAuthProviderServiceId))

  override def httpRoute: Route = {
    super.httpRoute ~
      pathPrefix("protected") {
        path("any") {
          bgAuthRequire { info =>
            get {
              complete(info.userInfo.attributes)
            } ~
              post {
                complete(info.userInfo.attributes)
              }
          }
        } ~
          path("role") {
            bgAuthGrantsAllowed(Grant("CUSTOM_ROLE")) { info =>
              get {
                complete(info.userInfo.attributes)
              } ~
                post {
                  complete(info.userInfo.attributes)
                }
            }
          } ~
          path("dummy") {
            bgAuthGrantsAllowed(Grant("DUMMY")) { _ =>
              get {
                complete("This should not be possible")
              }
            }
          } ~
          path("master") {
            bgAuthGrantsAllowed(Grant.MASTER) { info =>
              get {
                complete(info.userInfo.attributes)
              }
            }
          } ~
          path("api") {
            bgAuthGrantsAllowed(Grant.API) { info =>
              get {
                complete(info.userInfo.attributes)
              } ~
                post {
                  complete(info.userInfo.attributes)
                }
            }
          }

      }
  }

  override protected def bgAuthProviderServiceId
  : BgServiceId
  = BgServiceId("BgAuthProviderTestProvider", 1)

  override protected def jwtAttributeMapper
  : AdditionalUserAttrsManager[String]
  = SampleJwtAttributes
}