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

package org.s4s0l.betelgeuse.akkaauth.client.impl

import akka.http.scaladsl.server.{Directive0, Directives}
import org.s4s0l.betelgeuse.akkaauth.client.AuthClientAudit

/**
  * @author Marcin Wielgus
  */
class ClientAuditStack[T](seq: Seq[AuthClientAudit[T]])
  extends AuthClientAudit[T] {
  override def logClientEvent(evt: AuthClientAudit.AuthClientAuditEvent[T])
  : Directive0 = {
    seq.reverse.foldLeft(Directives.pass) { (c, next) =>
      c.tflatMap(_ => next.logClientEvent(evt))
    }
  }
}
