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
import akka.http.scaladsl.server.{Directive, Directive0, Directive1}
import com.softwaremill.session._
import org.s4s0l.betelgeuse.akkaauth.common.{AuthInfo, Grant, SerializedToken}

import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
private[akkaauth] trait BgAuthClientDirectives[A] {
  this: BgAuthBase[A] =>

  def bgAuthCsrf: Directive0 =
    readHeader.flatMap {
      case None =>
        CsrfDirectives.randomTokenCsrfProtection(CsrfOptions.checkHeader)
      case Some(_) =>
        pass
    }

  def bgAuthRequire: Directive1[AuthInfo[A]] =
    bgAuthGrantsAllowed()

  def bgAuthGrantsAllowed(grant: Grant*): Directive1[AuthInfo[A]] =
    bgAuthCsrf.tflatMap { _ =>
      requiredToken.flatMap { token =>
        val grantsPresent = grant.toSet
          .forall(it => token.userInfo.grants.contains(it))
        if (grantsPresent) {
          provide(token)
        } else {
          complete(HttpResponse(StatusCodes.Forbidden, entity = "Missing grants"))
        }
      }
    }

  private def requiredToken: Directive1[AuthInfo[A]] = {
    readToken.flatMap {
      case None =>
        complete(HttpResponse(StatusCodes.Unauthorized, entity = "Authorization required"))
      case Some((v, CookieST)) =>
        onComplete(bgAuthClient.extract(v)).flatMap {
          case Success(a) =>
            provide(a)
          case Failure(exception) =>
            complete(HttpResponse(StatusCodes.Forbidden, entity = exception.getMessage))
        }
      case Some((v, HeaderST)) =>
        onComplete(bgAuthClient.resolveApiToken(v)).flatMap {
          case Success(a) =>
            provide(a)
          case Failure(exception) =>
            complete(HttpResponse(StatusCodes.Forbidden, entity = exception.getMessage))
        }
    }
  }

  private def readToken: Directive[Tuple1[Option[(SerializedToken, SetSessionTransport)]]] =
    readHeader.flatMap(_.fold(readCookies)(v => provide(Some(v))))

  private def readHeader: Directive[Tuple1[Option[(SerializedToken, SetSessionTransport)]]] =
    optionalHeaderValueByName(sessionManager.config.sessionHeaderConfig.getFromClientHeaderName)
      .map(_.map(h => (SerializedToken(h), HeaderST: SetSessionTransport)))

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
