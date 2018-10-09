package org.s4s0l.betelgeuse.akkaauth

import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.server.Directives.{optionalCookie, optionalHeaderValueByName, provide, reject, setCookie}
import akka.http.scaladsl.server.{Directive, Directive0, Directive1}
import com.softwaremill.session._
import pdi.jwt.JwtBase64
import pdi.jwt.exceptions.JwtLengthException

/**
  * @author Marcin Wielgus
  */
trait BgDirectives {

  def setSession[T](manager: SessionManager[T], v: T): Directive0 = {
    val encoded = manager.clientSessionManager.encode(v)
    val (header, _, claims, _, signature) = splitToken(encoded)
    setCookie(
      createHeadersCookie(manager.config, header),
      createClaimsCookie(manager.config, claims),
      createSignatureCookie(manager.config, signature),
    )
  }

  def session[T](manager: SessionManager[T]): Directive1[SessionResult[T]] = {
    read(manager.config).flatMap {
      case None => provide(SessionResult.NoSession)
      case Some((v, setSt)) => {
        provide(manager.clientSessionManager.decode(v))
      }
    }
  }

  def requiredSession[T](manager: SessionManager[T]): Directive1[T] =
    optionalSession(manager).flatMap {
      case None => reject(manager.clientSessionManager.sessionMissingRejection)
      case Some(data) => provide(data)
    }

  def optionalSession[T](manager: SessionManager[T]): Directive1[Option[T]] =
    session(manager).map(_.toOption)

  private def read(config: SessionConfig): Directive[Tuple1[Option[(String, SetSessionTransport)]]] =
    readHeader(config).flatMap(_.fold(readCookies(config))(v => provide(Some(v))))

  private def readCookies(config: SessionConfig): Directive[Tuple1[Option[(String, SetSessionTransport)]]] =
    for (
      hd <- optionalCookie(config.sessionCookieConfig.name + "_header");
      cd <- optionalCookie(config.sessionCookieConfig.name + "_claims");
      sd <- optionalCookie(config.sessionCookieConfig.name + "_signature")
    ) yield for (
      h <- hd;
      c <- cd;
      s <- sd
    ) yield (s"${h.value}.${c.value}.${s.value}", CookieST: SetSessionTransport)

  private def readHeader(config: SessionConfig): Directive[Tuple1[Option[(String, SetSessionTransport)]]] =
    optionalHeaderValueByName(config.sessionHeaderConfig.getFromClientHeaderName)
      .map(_.map(h => (h, HeaderST: SetSessionTransport)))

  private def splitToken(token: String): (String, String, String, String, String) = {
    val parts = token.split("\\.")
    val signature = parts.length match {
      case 2 => ""
      case 3 => parts(2)
      case _ => throw new JwtLengthException(s"Expected token [$token] to be composed of 2 or 3 parts separated by dots.")
    }

    (parts(0), JwtBase64.decodeString(parts(0)), parts(1), JwtBase64.decodeString(parts(1)), signature)
  }

  private def createSignatureCookie(config: SessionConfig, value: String) = HttpCookie(
    name = config.sessionCookieConfig.name + "_signature",
    value = value,
    expires = None,
    maxAge = None,
    domain = config.sessionCookieConfig.domain,
    path = config.sessionCookieConfig.path,
    secure = config.sessionCookieConfig.secure,
    httpOnly = true)

  private def createHeadersCookie(config: SessionConfig, value: String) = HttpCookie(
    name = config.sessionCookieConfig.name + "_header",
    value = value,
    expires = None,
    maxAge = None,
    domain = config.sessionCookieConfig.domain,
    path = config.sessionCookieConfig.path,
    secure = config.sessionCookieConfig.secure,
    httpOnly = false)

  private def createClaimsCookie(config: SessionConfig, value: String) = HttpCookie(
    name = config.sessionCookieConfig.name + "_claims",
    value = value,
    expires = None,
    maxAge = None,
    domain = config.sessionCookieConfig.domain,
    path = config.sessionCookieConfig.path,
    secure = config.sessionCookieConfig.secure,
    httpOnly = false)

}
