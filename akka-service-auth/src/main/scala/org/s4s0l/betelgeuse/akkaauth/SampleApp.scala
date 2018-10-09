package org.s4s0l.betelgeuse.akkaauth

import java.util.UUID

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Route}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.softwaremill.session.{CsrfDirectives, CsrfOptions, SessionConfig, SessionManager}
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.http.BgHttp

/**
  * @author Marcin Wielgus
  */
object SampleApp extends BgService with BgHttp
  with BgDirectives {

  implicit val encoder: CustomJwtEncoder = new CustomJwtEncoder(Keys.pub, Keys.key)
  val sessionConfig: SessionConfig = SessionConfig.default("do not use _ do not use _ do not use _ do not use _ do not use _ do not use _ do not use _ ")
  implicit val sessionManager: SessionManager[AuthenticationInfo] = new SessionManager[AuthenticationInfo](sessionConfig)
  implicit val ulm: FromEntityUnmarshaller[UserLogin] = httpMarshalling.unmarshaller[UserLogin]
  implicit val ulx: FromEntityUnmarshaller[Map[String, String]] = httpMarshalling.unmarshaller[Map[String, String]]
  implicit val mlx: ToEntityMarshaller[Map[String, String]] = httpMarshalling.marshaller[Map[String, String]]

  override def httpRoute: Route = {
    super.httpRoute ~
      get {
        getFromResourceDirectory("page")
      } ~
      CsrfDirectives.randomTokenCsrfProtection(CsrfOptions.checkHeader) {
        pathPrefix("auth") {
          doLogin() ~
            verify()
        } ~
          pathPrefix("noauth") {
            get {
              complete("ok")
            } ~
              post {
                entity(as[Map[String, String]]) { echo =>
                  complete(echo)
                }
              }
          }
      }
  }

  private def verifyPassword(login: UserLogin) =
    Credentials(Some(BasicHttpCredentials(login.login, login.password))) match {
      case p: Credentials.Provided =>
        p.verify("adminadmin", identity)
      case _ => false
    }

  private def doLogin() = {
    path("login") {
      post {
        entity(as[UserLogin]) { login =>
          if (verifyPassword(login)) {
            val auth = AuthenticationInfo(
              UUID.randomUUID().toString,
              login.login,
              List("role1", "role2"),
              sessionConfig.sessionMaxAgeSeconds.map(it => sessionManager.clientSessionManager.nowMillis + it * 1000).get,
              Map("custom" -> "attribute")
            )
            setSession(sessionManager, auth) {
              complete("ok")
            }
          } else {
            //todo clear cookies
            reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2("bgRealm")))
          }
        }
      }
    }
  }

  private def verify() = {
    path("verify") {
      requiredSession[AuthenticationInfo](sessionManager) { authInfo =>
        post {
          entity(as[Map[String, String]]) { echo =>
            complete(echo)
          }
        } ~ get {
          complete(authInfo.login)
        }
      }

    }
  }
}
