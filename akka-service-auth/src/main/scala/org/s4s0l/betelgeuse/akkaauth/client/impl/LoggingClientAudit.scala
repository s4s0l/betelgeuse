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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethod, RemoteAddress, Uri}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import org.s4s0l.betelgeuse.akkaauth.client.AuthClientAudit
import org.s4s0l.betelgeuse.akkaauth.client.AuthClientAudit._
import org.s4s0l.betelgeuse.akkaauth.common.AuthInfo

/**
  * @author Marcin Wielgus
  */
class LoggingClientAudit[T](marker: String = "[S]")
                           (implicit actorSystem: ActorSystem)
  extends AuthClientAudit[T] {

  private lazy val LOGGER = akka.event.Logging.getLogger(actorSystem, this)

  override def logClientEvent(evt: AuthClientAudit.AuthClientAuditEvent[T]): Directive0 = {
    extractClientIP.flatMap { implicit ip =>
      extractMethod.flatMap { implicit method =>
        extractUri.flatMap { implicit uri =>
          evt match {
            case CsrfMissing() =>
              if (LOGGER.isWarningEnabled)
                LOGGER.warning(format("Csrf missing"))
            case TokenMissing() =>
              if (LOGGER.isWarningEnabled)
                LOGGER.warning(format("Token missing"))
            case Granted(token) =>
              if (LOGGER.isDebugEnabled)
                LOGGER.debug(formatWithToken(token, s"Granted access"))
            case InsufficientGrants(token, grantsMissing) =>
              if (LOGGER.isDebugEnabled)
                LOGGER.debug(formatWithToken(token, s"Not enough grants [${grantsMissing.map(_.name).mkString(", ")}]"))
            case TokenInvalid(ex) =>
              if (LOGGER.isWarningEnabled)
                LOGGER.warning(format(s"Token invalid: ${ex.description}"))
            case InternalAuthError(ex) =>
              if (LOGGER.isErrorEnabled)
                LOGGER.error(format(s"Internal auth error : ${ex.getMessage}"), ex)
          }
          pass
        }
      }
    }
  }

  def formatWithToken(authIn: AuthInfo[T], message: String)(implicit remoteIp: RemoteAddress,
                                                            uri: Uri,
                                                            method: HttpMethod): String = {
    val authInfo = formatAuthInfo(authIn)
    s"$marker $authInfo<$remoteIp> - ${method.value} $uri: $message"
  }

  def formatAuthInfo(authIn: AuthInfo[T]): String = {
    s"${authIn.userInfo.login.getOrElse("???")}(${authIn.userInfo.userId.id})"
  }

  def format(message: String)(implicit remoteIp: RemoteAddress,
                              uri: Uri,
                              method: HttpMethod): String = {
    s"$marker <$remoteIp> - ${method.value} ${uri.path}: $message"
  }


}
