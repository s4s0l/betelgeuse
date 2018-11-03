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

package org.s4s0l.betelgeuse.akkaauth.client

import akka.http.scaladsl.server.Directive0
import org.s4s0l.betelgeuse.akkaauth.client.AuthClientAudit.AuthClientAuditEvent
import org.s4s0l.betelgeuse.akkaauth.client.ClientExceptions.ClientException
import org.s4s0l.betelgeuse.akkaauth.common.{AuthInfo, Grant}

/**
  * @author Marcin Wielgus
  */
trait AuthClientAudit[T] {

  def log(evt: AuthClientAuditEvent[T]): Directive0

}

object AuthClientAudit {

  sealed trait AuthClientAuditEvent[T]

  case class CsrfMissing[T]()
    extends AuthClientAuditEvent[T]

  case class TokenMissing[T]()
    extends AuthClientAuditEvent[T]

  case class Granted[T](token: AuthInfo[T])
    extends AuthClientAuditEvent[T]

  case class InsufficientGrants[T](token: AuthInfo[T], grantsMissing: Seq[Grant])
    extends AuthClientAuditEvent[T]

  case class TokenInvalid[T](ex: ClientException)
    extends AuthClientAuditEvent[T]

  case class InternalAuthError[T](ex: Throwable)
    extends AuthClientAuditEvent[T]

}
