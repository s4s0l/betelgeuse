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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.s4s0l.betelgeuse.akkacommons.http.BgHttp

/**
  * @author Marcin Wielgus
  */
trait BgAuthHttpProvider[A]
  extends BgAuthProvider[A]
    with BgAuthProviderDirectives[A]
    with BgHttp {

  override def httpRoute: Route = {
    bgAuthProviderDefaultRoutes ~
      super.httpRoute
  }
}
