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

package org.s4s0l.betelgeuse.akkaauth.manager

import akka.http.scaladsl.server.Directive0
import org.s4s0l.betelgeuse.akkaauth.BgAuthProviderDirectives.{CreateApiTokenRequest, CreateUserRequest}
import org.s4s0l.betelgeuse.akkaauth.common.{AuthInfo, TokenId, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.AuthProviderAudit.AuthProviderAuditEvent
import org.s4s0l.betelgeuse.akkaauth.manager.ProviderExceptions.ProviderException
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.UserDetailedInfo

/**
  * @author Marcin Wielgus
  */
trait AuthProviderAudit[T] {
  def logProviderEvent(evt: AuthProviderAuditEvent[T]): Directive0
}

object AuthProviderAudit {

  sealed trait AuthProviderAuditEvent[T]

  case class ProviderError[T](authInfo: Option[AuthInfo[T]], ex: ProviderException)
    extends AuthProviderAuditEvent[T]

  case class ProviderInternalError[T](authInfo: Option[AuthInfo[T]], ex: Throwable)
    extends AuthProviderAuditEvent[T]

  case class GetUserDetails[T](authInfo: AuthInfo[T], info: UserDetailedInfo)
    extends AuthProviderAuditEvent[T]

  case class CreateApiToken[T](authInfo: AuthInfo[T], req: CreateApiTokenRequest, token: TokenId)
    extends AuthProviderAuditEvent[T]

  case class RevokeApiToken[T](authInfo: AuthInfo[T], token: TokenId)
    extends AuthProviderAuditEvent[T]

  case class CreateUser[T](authInfo: AuthInfo[T], attrs: CreateUserRequest, userId: UserId)
    extends AuthProviderAuditEvent[T]

  case class LockUser[T](authInfo: AuthInfo[T], userId: UserId)
    extends AuthProviderAuditEvent[T]

  case class ChangePass[T](authInfo: AuthInfo[T])
    extends AuthProviderAuditEvent[T]

  case class LoginSuccess[T](authInfo: AuthInfo[T])
    extends AuthProviderAuditEvent[T]

  case class UnLockUser[T](authInfo: AuthInfo[T], userId: UserId)
    extends AuthProviderAuditEvent[T]

}
