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

package org.s4s0l.betelgeuse.akkaauth.manager.impl

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethod, RemoteAddress, Uri}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import org.s4s0l.betelgeuse.akkaauth.common.AuthInfo
import org.s4s0l.betelgeuse.akkaauth.manager.AuthProviderAudit
import org.s4s0l.betelgeuse.akkaauth.manager.AuthProviderAudit._

/**
  * @author Marcin Wielgus
  */
class LoggingProviderAudit[T](marker: String = "[S]")
                             (implicit actorSystem: ActorSystem) extends AuthProviderAudit[T] {

  private lazy val LOGGER = akka.event.Logging.getLogger(actorSystem, this)

  override def logProviderEvent(evt: AuthProviderAudit.AuthProviderAuditEvent[T]): Directive0 = {
    extractClientIP.flatMap { implicit ip =>
      extractMethod.flatMap { implicit method =>
        extractUri.flatMap { implicit uri =>
          evt match {
            case ProviderError(auth, ex) =>
              if (LOGGER.isWarningEnabled)
                LOGGER.warning(formatOpt(auth, s"Provider warning: ${ex.description}"))
            case ProviderInternalError(auth, ex) =>
              if (LOGGER.isErrorEnabled)
                LOGGER.error(formatOpt(auth, s"Provider error"), ex)
            case GetUserDetails(auth, _) =>
              if (LOGGER.isDebugEnabled)
                LOGGER.debug(format(auth, s"getting user details"))
            case CreateApiToken(auth, req, token) =>
              if (LOGGER.isInfoEnabled)
                LOGGER.info(format(auth, s"Api token created ${token.id} with [${if (req.allRoles) "all" else req.roles.mkString(",")}]"))
            case RevokeApiToken(auth, token) =>
              if (LOGGER.isInfoEnabled)
                LOGGER.info(format(auth, s"Api token revoked ${token.id}"))
            case CreateUser(auth, req, userId) =>
              if (LOGGER.isInfoEnabled)
                LOGGER.info(format(auth, s"User created ${req.credentials.map(_.login).getOrElse("<>")}(${userId.id})"))
            case LockUser(auth, userId) =>
              if (LOGGER.isInfoEnabled)
                LOGGER.info(format(auth, s"User locked ${userId.id}"))
            case UnLockUser(auth, userId) =>
              if (LOGGER.isInfoEnabled)
                LOGGER.info(format(auth, s"User un locked ${userId.id}"))
            case ChangePass(auth) =>
              if (LOGGER.isInfoEnabled)
                LOGGER.info(format(auth, s"User changed password"))
            case LoginSuccess(auth) =>
              if (LOGGER.isInfoEnabled)
                LOGGER.info(format(auth, s"Login successful, tokenId=${auth.tokenInfo.tokenType.tokenId.id}"))
          }
          pass
        }
      }
    }
  }

  def formatOpt(authInfo: Option[AuthInfo[T]], message: String)(implicit remoteIp: RemoteAddress,
                                                                uri: Uri,
                                                                method: HttpMethod): String = {
    val authenticated = authInfo
      .map(authIn => formatAuthInfo(authIn))
      .getOrElse("")
    s"$marker $authenticated<$remoteIp> - ${method.value} $uri: $message"
  }

  def format(authInfo: AuthInfo[T], message: String)(implicit remoteIp: RemoteAddress,
                                                     uri: Uri,
                                                     method: HttpMethod): String = {
    s"$marker ${formatAuthInfo(authInfo)}<$remoteIp> - ${method.value} $uri: $message"
  }


  def formatAuthInfo(authIn: AuthInfo[T]): String = {
    s"${authIn.userInfo.login.getOrElse("???")}(${authIn.userInfo.userId.id})"
  }
}
