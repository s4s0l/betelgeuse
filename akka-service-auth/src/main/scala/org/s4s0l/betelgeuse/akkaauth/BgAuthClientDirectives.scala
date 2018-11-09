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

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.util.Tuple
import akka.http.scaladsl.server.{Directive, Directive0, Directive1, RouteResult}
import akka.util.Timeout
import com.softwaremill.session.{SetSessionTransport, _}
import org.s4s0l.betelgeuse.akkaauth.BgAuthClientDirectives.{LocalResolution, RemoteResolution, ResolutionType}
import org.s4s0l.betelgeuse.akkaauth.client.AuthClientAudit
import org.s4s0l.betelgeuse.akkaauth.client.AuthClientAudit._
import org.s4s0l.betelgeuse.akkaauth.client.ClientExceptions.ClientException
import org.s4s0l.betelgeuse.akkaauth.client.impl.{ClientAuditStack, LoggingClientAudit}
import org.s4s0l.betelgeuse.akkaauth.common.{AuthInfo, Grant, SerializedToken}
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

/**
  * @author Marcin Wielgus
  */
private[akkaauth] trait BgAuthClientDirectives[A] {
  this: BgAuthBase[A] =>
  //  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgAuthClientDirectives[_]])

  lazy val bgAuthClientResolveTimeout: FiniteDuration = config.getDuration("bg.auth.client.token-resolve-timeout")
  lazy val bgAuthClientExtractTimeout: FiniteDuration = config.getDuration("bg.auth.client.token-extract-timeout")


  def bgAuthClientAudits: Seq[AuthClientAudit[A]] = Seq(new LoggingClientAudit())

  lazy val bgAuthClientAudit: AuthClientAudit[A] = new ClientAuditStack[A](bgAuthClientAudits)

  private lazy val check = CsrfOptions.checkHeader

  def bgAuthCsrf: Directive0 = {
    readHeader.flatMap {
      case None =>
        recoverRejections { rejections =>
          if (rejections.contains(check.manager.csrfManager.tokenInvalidRejection)) {
            bgAuthClientAudit.logClientEvent(CsrfMissing()) {
              reject(rejections: _*)
            }
          }
          RouteResult.Rejected(rejections)
        }.tflatMap { _ =>
          CsrfDirectives.randomTokenCsrfProtection(check)
        }
      case Some(_) =>
        pass
    }
  }

  def bgAuthRequire: Directive1[AuthInfo[A]] =
    bgAuthGrantsAllowed()

  def bgAuthGrantsAllowed(grant: Grant*): Directive1[AuthInfo[A]] =
    bgAuthCsrf.tflatMap { _ =>
      requiredToken.flatMap { token =>
        val grantsPresent = grant.toSet
          .forall(it => token.userInfo.grants.contains(it))
        if (grantsPresent) {
          bgAuthClientAudit.logClientEvent(Granted[A](token)).tflatMap { _ =>
            provide(token)
          }
        } else {
          val missingGrants = grant.toSet -- token.userInfo.grants
          val auditEvent = InsufficientGrants[A](token, missingGrants.toSeq)
          bgAuthClientAudit.logClientEvent(auditEvent).tflatMap { _ =>
            complete(HttpResponse(StatusCodes.Forbidden, entity = "Missing grants"))
          }
        }
      }
    }

  private def bgAuthResolutionType(token: AuthInfo[A], transport: SetSessionTransport): ResolutionType = {
    if (token.userInfo.grants.contains(Grant.API)) {
      RemoteResolution
    } else {
      LocalResolution
    }
  }

  private def handleAuthExceptions[R: Tuple](block: AuthInfo[A] => Directive[R])
  : Try[AuthInfo[A]] => Directive[R] = {
    case Success(a) => block(a)
    case Failure(ex: ClientException) =>
      bgAuthClientAudit.logClientEvent(TokenInvalid(ex)).tflatMap { _ =>
        complete(HttpResponse(StatusCodes.Forbidden, entity = "Not Allowed"))
      }
    case Failure(exception) =>
      bgAuthClientAudit.logClientEvent(InternalAuthError(exception)).tflatMap { _ =>
        complete(HttpResponse(StatusCodes.Forbidden, entity = "Not Allowed"))
      }
  }

  private def requiredToken: Directive1[AuthInfo[A]] = {
    readToken.flatMap {
      case None =>
        bgAuthClientAudit.logClientEvent(TokenMissing()).tflatMap { _ =>
          complete(HttpResponse(StatusCodes.Unauthorized, entity = "Authorization required"))
        }
      case Some((v, transport)) =>
        implicit val to: Timeout = bgAuthClientExtractTimeout
        onComplete(bgAuthClient.extract(v)).flatMap {
          handleAuthExceptions { a =>
            bgAuthResolutionType(a, transport) match {
              case LocalResolution => provide(a)
              case RemoteResolution =>
                resolveRemotely(v)
            }
          }
        }
    }
  }

  private def resolveRemotely(v: SerializedToken): Directive1[AuthInfo[A]] = {
    implicit val to: Timeout = bgAuthClientResolveTimeout
    onComplete(bgAuthClient.resolveApiToken(v)).flatMap {
      handleAuthExceptions { auth2 =>
        provide(auth2)
      }
    }
  }

  private def readToken: Directive[Tuple1[Option[(SerializedToken, SetSessionTransport)]]] =
    readHeader.flatMap(_.fold(readCookies)(v => provide(Some(v))))

  private def readHeader: Directive[Tuple1[Option[(SerializedToken, SetSessionTransport)]]] =
    optionalHeaderValueByName(sessionManager.config.sessionHeaderConfig.getFromClientHeaderName)
      .flatMap {
        case None =>
          provide(None)
        case Some(value) if value.startsWith("Bearer ") =>
          val token = value.substring(7)
          provide(Some((SerializedToken(token), HeaderST: SetSessionTransport)))
        case Some(_) =>
          complete(HttpResponse(StatusCodes.BadRequest, entity = "Authorization not supported"))
      }

  private def readCookies: Directive[Tuple1[Option[(SerializedToken, SetSessionTransport)]]] =
    for (
      hd <- optionalCookie(sessionManager.config.sessionCookieConfig.name + "_header");
      cd <- optionalCookie(sessionManager.config.sessionCookieConfig.name + "_claims");
      sd <- optionalCookie(sessionManager.config.sessionCookieConfig.name + "_signature")
    ) yield for (
      h <- hd;
      c <- cd;
      s <- sd
    ) yield (
      SerializedToken(s"${h.value}.${c.value}.${s.value}"),
      CookieST: SetSessionTransport
    )
}

object BgAuthClientDirectives {

  sealed trait ResolutionType

  case object LocalResolution extends ResolutionType

  case object RemoteResolution extends ResolutionType

}