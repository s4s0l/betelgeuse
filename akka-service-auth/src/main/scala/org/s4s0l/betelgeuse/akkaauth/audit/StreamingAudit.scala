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

package org.s4s0l.betelgeuse.akkaauth.audit

import akka.Done
import akka.http.scaladsl.model.{HttpMethod, RemoteAddress, Uri}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import org.s4s0l.betelgeuse.akkaauth.audit.StreamingAuditDto._
import org.s4s0l.betelgeuse.akkaauth.client.AuthClientAudit
import org.s4s0l.betelgeuse.akkaauth.client.AuthClientAudit._
import org.s4s0l.betelgeuse.akkaauth.common.{AdditionalAttrsManager, AuthInfo}
import org.s4s0l.betelgeuse.akkaauth.manager.AuthProviderAudit
import org.s4s0l.betelgeuse.akkaauth.manager.AuthProviderAudit._
import org.s4s0l.betelgeuse.utils.UuidUtils

import scala.concurrent.Future
import scala.language.implicitConversions

/**
  * @author Marcin Wielgus
  */

class StreamingAudit[A](
                         serviceInfo: ServiceInfo,
                         attrsMapper: AdditionalAttrsManager[A],
                         onEvent: StreamingAuditDto => Future[Done])
  extends AuthClientAudit[A]
    with AuthProviderAudit[A] {

  override def logClientEvent(evt: AuthClientAudit.AuthClientAuditEvent[A]): Directive0 =
    extractClientIP.flatMap { implicit ip =>
      extractMethod.flatMap { implicit method =>
        extractUri.flatMap { implicit uri =>
          onSuccess(handleClientEvent(routeInfo, evt)).flatMap { _ =>
            pass
          }
        }
      }
    }

  override def logProviderEvent(evt: AuthProviderAudit.AuthProviderAuditEvent[A]): Directive0 = {
    extractClientIP.flatMap { implicit ip =>
      extractMethod.flatMap { implicit method =>
        extractUri.flatMap { implicit uri =>
          onSuccess(handleProviderEvent(routeInfo, evt)).flatMap { _ =>
            pass
          }
        }
      }
    }
  }

  private def routeInfo(implicit remoteIp: RemoteAddress,
                        uri: Uri,
                        method: HttpMethod)
  : RouteInfo = RouteInfo(
    remoteIp.toString(),
    method.value,
    uri.toString(),
    uri.path.toString()
  )

  private def nextId = UuidUtils.timeBasedUuid().toString

  private def handleProviderEvent(routeInfo: RouteInfo,
                                  evt: AuthProviderAudit.AuthProviderAuditEvent[A])
  : Future[Done] = {
    val event = evt match {
      case ProviderError(auth, ex) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "providerError", auth,
          errorMessage = Some(ex.description)
        )
      case ProviderInternalError(auth, ex) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "providerInternalError", auth,
          errorMessage = Some(ex.getMessage)
        )
      case GetUserDetails(auth, x) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "getUserDetails", auth,
          inBehalfOfUserId = Some(x.userId.id)
        )
      case CreateApiToken(auth, _, token) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "createApiToken", auth,
          tokenId = Some(token.id)
        )
      case RevokeApiToken(auth, token) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "revokeApiToken", auth,
          tokenId = Some(token.id)
        )
      case CreateUser(auth, _, userId) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "createUser", auth,
          inBehalfOfUserId = Some(userId.id)
        )
      case LockUser(auth, userId) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "lockUser", auth,
          inBehalfOfUserId = Some(userId.id)
        )
      case UnLockUser(auth, userId) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "unLockUser", auth,
          inBehalfOfUserId = Some(userId.id)
        )
      case ChangePass(auth) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "changePassword", auth,
        )
      case LoginSuccess(auth) =>
        AuthProviderEventDto(nextId,
          serviceInfo, routeInfo, "login", auth,
        )
    }
    onEvent(event)
  }

  private def handleClientEvent(routeInfo: RouteInfo,
                                evt: AuthClientAudit.AuthClientAuditEvent[A])
  : Future[Done] = {
    val event = evt match {
      case CsrfMissing() =>
        AuthClientEventDto(nextId,
          serviceInfo, routeInfo, "csrfMissing",
        )
      case TokenMissing() =>
        AuthClientEventDto(nextId,
          serviceInfo, routeInfo, "tokenMissing",
        )
      case Granted(token) =>
        AuthClientEventDto(nextId,
          serviceInfo, routeInfo, "granted",
          authInfo = token
        )
      case InsufficientGrants(token, grantsMissing) =>
        AuthClientEventDto(nextId,
          serviceInfo, routeInfo, "insufficientGrants",
          authInfo = token,
          missingGrants = grantsMissing.map(_.name).toList
        )
      case TokenInvalid(ex) =>
        AuthClientEventDto(nextId,
          serviceInfo, routeInfo, "tokenInvalid",
          errorMessage = Some(ex.description)
        )
      case InternalAuthError(ex) =>
        AuthClientEventDto(nextId,
          serviceInfo, routeInfo, "internalAuthError",
          errorMessage = Some(ex.getMessage)
        )
    }
    onEvent(event)
  }

  private implicit def toDtoOpt(auth: Option[AuthInfo[A]]): Option[AuthInfoDto] =
    auth.flatMap(toDto)

  private implicit def toDto(auth: AuthInfo[A]): Option[AuthInfoDto] =
    Some(AuthInfoDto(
      auth.tokenInfo.tokenType.tokenId.id,
      auth.tokenInfo.tokenType.tokenTypeName,
      auth.userInfo.login,
      auth.userInfo.userId.id,
      attrsMapper.marshallAttrs(auth.userInfo.attributes)
    ))
}
