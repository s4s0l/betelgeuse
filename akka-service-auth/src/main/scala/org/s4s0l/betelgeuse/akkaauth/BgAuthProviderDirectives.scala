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

import java.util.Date

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.model.{DateTime, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.s4s0l.betelgeuse.akkaauth.BgAuthProviderDirectives._
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.AuthManager.{AllRoles, GivenRoles, RoleSet}
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{Role, UserDetailedAttributes, UserDetailedInfo}
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import pdi.jwt.exceptions.JwtLengthException

import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
private[akkaauth] trait BgAuthProviderDirectives[A] {
  this: BgAuthProvider[A] =>

  private implicit val m1: ToEntityMarshaller[SuccessfulLoginResult] = httpMarshalling.marshaller
  private implicit val m3: ToEntityMarshaller[UserId] = httpMarshalling.marshaller
  private implicit val m2: ToEntityMarshaller[UserDetailedInfo] = httpMarshalling.marshaller
  private implicit val m4: ToEntityMarshaller[JustSuccess] = httpMarshalling.marshaller
  private implicit val m5: ToEntityMarshaller[CreateApiTokenResponse] = httpMarshalling.marshaller
  private implicit val u1: FromEntityUnmarshaller[PasswordCredentials] = httpMarshalling.unmarshaller
  private implicit val u2: FromEntityUnmarshaller[NewPassRequest] = httpMarshalling.unmarshaller
  private implicit val u3: FromEntityUnmarshaller[CreateApiTokenRequest] = httpMarshalling.unmarshaller
  private implicit val u4: FromEntityUnmarshaller[TokenId] = httpMarshalling.unmarshaller
  private implicit val u5: FromEntityUnmarshaller[UserId] = httpMarshalling.unmarshaller
  private implicit val u6: FromEntityUnmarshaller[CreateUserRequest] = httpMarshalling.unmarshaller

  def bgAuthProviderDefaultRoutes: Route =
    concat(
      bgAuthProviderLoginRoutes,
      bgAuthProviderSelfUserManagement,
      bgAuthProviderUserManagement
    )

  def bgAuthProviderLoginRoutes: Route = {
    pathPrefix("auth") {
      bgAuthCsrf {
        concat(
          path("login") {
            login
          },
          path("verify") {
            verify
          }
        )
      }
    }
  }

  def bgAuthProviderSelfUserManagement: Route = {
    pathPrefix("current-user") {
      concat(
        path("get-details") {
          bgAuthCurrentUserDetails()
        },
        path("change-password") {
          bgAuthCurrentUserPassChange()
        },
        path("create-api-token") {
          bgAuthCreateApiToken(Set(Grant.API))
        },
        path("invalidate-api-token") {
          bgAuthInvalidateApiToken()
        }
      )
    }
  }

  def bgAuthProviderUserManagement: Route = {
    pathPrefix("manage-user") {
      concat(
        path("lock") {
          bgAuthLockUser(Grant.MASTER)
        },
        path("un-lock") {
          bgAuthUnLockUser(Grant.MASTER)
        }, path("create") {
          bgAuthCreateUser(Grant.MASTER)
        }
      )
    }
  }

  def bgAuthCurrentUserDetails(grantRequired: Grant*): Route =
    get {
      bgAuthGrantsAllowed(grantRequired: _*) { authInfo =>
        onSuccess(userManager.getUser(authInfo.userInfo.userId)) {
          details => complete(details)
        }
      }
    }

  def bgAuthCreateApiToken(grants: Set[Grant], grantRequired: Grant*): Route = {
    post {
      bgAuthGrantsAllowed(grantRequired: _*) { authInfo =>
        entity(as[CreateApiTokenRequest]) { request =>
          val creationProcess = authManager.createApiToken(
            authInfo.userInfo.userId,
            request.asRoleSet,
            grants,
            request.expiryDate
          )
          onSuccess(creationProcess) { accessToken =>
            complete(
              CreateApiTokenResponse(
                accessToken.tokenId.id,
                accessToken.serializedToken.token)
            )
          }
        }
      }
    }
  }

  def bgAuthCreateUser(grantRequired: common.Grant*): Route =
    post {
      bgAuthGrantsAllowed(grantRequired: _*) { _ =>
        entity(as[CreateUserRequest]) { request =>
          val userDetails = UserDetailedAttributes(
            request.userAttributed,
            request.roles.map(it => Role(it)).toSet,
            request.additionalAttributes
          )
          onSuccess(authManager.createUser(userDetails, request.credentials)) { userId =>
            complete(userId)
          }
        }
      }
    }

  def bgAuthInvalidateApiToken(grantRequired: Grant*): Route =
    put {
      bgAuthGrantsAllowed(grantRequired: _*) { authInfo =>
        entity(as[TokenId]) { id =>
          val process = for (
            subjectId <- tokenManager.getSubject(id) if subjectId == authInfo.userInfo.userId;
            inv <- authManager.invalidateApiToken(id)
          ) yield inv
          onSuccess(process) { _ =>
            complete(JustSuccess())
          }
        }
      }
    }

  def bgAuthLockUser(grantRequired: Grant): Route = {
    put {
      bgAuthGrantsAllowed(grantRequired) { _ =>
        entity(as[UserId]) { id =>
          onSuccess(authManager.lockUser(id)) { _ =>
            complete(JustSuccess())
          }
        }
      }
    }
  }

  def bgAuthUnLockUser(grantRequired: Grant): Route = {
    put {
      bgAuthGrantsAllowed(grantRequired) { _ =>
        entity(as[UserId]) { id =>
          onSuccess(authManager.unlockUser(id)) { _ =>
            complete(JustSuccess())
          }
        }
      }
    }
  }

  def bgAuthCurrentUserPassChange(grantRequired: Grant*): Route =
    put {
      bgAuthGrantsAllowed(grantRequired: _*) { authInfo =>
        entity(as[NewPassRequest]) { request =>
          authInfo.userInfo.login match {
            case None =>
              complete(HttpResponse(StatusCodes.BadRequest, entity = "User has no password credentials"))
            case Some(login) =>
              val passwordCredentials = PasswordCredentials(login, request.oldPassword)
              val updateProcess = for (
                userId <- passwordManager.verifyPassword(passwordCredentials) if userId == authInfo.userInfo.userId;
                done <- authManager.changePassword(userId, request.newPassword)
              ) yield done
              onSuccess(updateProcess) { _ =>
                complete(JustSuccess())
              }
          }
        }
      }
    }

  private def verify =
    get {
      bgAuthGrantsAllowed() { authInfo =>
        complete(authInfo.userInfo.userId)
      }
    }

  private def login =
    post {
      entity(as[PasswordCredentials]) { login =>
        onComplete(authManager.login(login)) {
          case Success(token) =>
            setToken(token) {
              complete(SuccessfulLoginResult())
            }
          case Failure(ex) =>
            ex.printStackTrace() //todo: introduce some ex for bad pass and log only when other problems
            complete(HttpResponse(StatusCodes.Unauthorized, entity = "Authorization failed"))
        }
      }
    }

  private def splitToken(token: SerializedToken): (String, String, String) = {
    //todo: PERFORMANCE !!
    val parts = token.token.split("\\.")
    val signature = parts.length match {
      case 2 => ""
      case 3 => parts(2)
      case _ => throw new JwtLengthException(s"Expected token [$token] to be composed of 2 or 3 parts separated by dots.")
    }
    (parts(0), parts(1), signature)
  }

  private def setToken(token: TokenInfo[AccessToken]): Directive0 = {
    val serializedToken = token.tokenType.serializedToken
    val (header, claims, signature) = splitToken(serializedToken)
    val expires = DateTime(token.expiration.getTime)
    setCookie(
      createHeadersCookie(header, expires),
      createClaimsCookie(claims, expires),
      createSignatureCookie(signature, expires),
    )
  }

  private def createSignatureCookie(value: String, expires: DateTime) =
    createCookie(
      nameSuffix = "_signature",
      value = value,
      expires = expires,
      httpOnly = true)

  private def createHeadersCookie(value: String, expires: DateTime) =
    createCookie(
      nameSuffix = "_header",
      value = value,
      expires = expires,
      httpOnly = false)

  private def createClaimsCookie(value: String, expires: DateTime) =
    createCookie(
      nameSuffix = "_claims",
      value = value,
      expires = expires,
      httpOnly = false)

  private def createCookie(nameSuffix: String,
                           value: String,
                           expires: DateTime,
                           httpOnly: Boolean) = HttpCookie(
    name = sessionManager.config.sessionCookieConfig.name + nameSuffix,
    value = value,
    expires = Some(expires),
    maxAge = None,
    domain = sessionManager.config.sessionCookieConfig.domain,
    path = sessionManager.config.sessionCookieConfig.path,
    secure = sessionManager.config.sessionCookieConfig.secure,
    httpOnly = httpOnly)

}

object BgAuthProviderDirectives {

  case class SuccessfulLoginResult()
    extends JacksonJsonSerializable

  case class JustSuccess(status: String = "ok")
    extends JacksonJsonSerializable

  case class NewPassRequest(oldPassword: String, newPassword: String)
    extends JacksonJsonSerializable

  case class CreateApiTokenResponse(tokenId: String,
                                    token: String)
    extends JacksonJsonSerializable

  case class CreateApiTokenRequest(allRoles: Boolean,
                                   roles: List[String],
                                   expiryDate: Date)
    extends JacksonJsonSerializable {
    def asRoleSet: RoleSet = if (allRoles) {
      AllRoles()
    } else {
      GivenRoles(roles.map(it => Role(it)).toSet)
    }
  }

  case class CreateUserRequest(userAttributed: UserAttributes,
                               roles: List[String],
                               additionalAttributes: Map[String, String],
                               credentials: Option[PasswordCredentials])
    extends JacksonJsonSerializable

}
