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

import java.security.interfaces.RSAPublicKey
import java.util.{Base64, Date}

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.model.{DateTime, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, ExceptionHandler, Route}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.BgAuthProviderDirectives._
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.AuthManager.{AllRoles, GivenRoles, RoleSet}
import org.s4s0l.betelgeuse.akkaauth.manager.AuthProviderAudit
import org.s4s0l.betelgeuse.akkaauth.manager.AuthProviderAudit._
import org.s4s0l.betelgeuse.akkaauth.manager.ProviderExceptions._
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{Role, UserDetailedAttributes, UserDetailedInfo}
import org.s4s0l.betelgeuse.akkaauth.manager.impl.LoggingProviderAudit
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable
import org.s4s0l.betelgeuse.utils.AllUtils._
import pdi.jwt.exceptions.JwtLengthException

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

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
  private implicit val m6: ToEntityMarshaller[VerifyRestResponse] = httpMarshalling.marshaller
  private implicit val m7: ToEntityMarshaller[JsonWebKeySet] = httpMarshalling.marshaller
  private implicit val u1: FromEntityUnmarshaller[PasswordCredentials] = httpMarshalling.unmarshaller
  private implicit val u2: FromEntityUnmarshaller[NewPassRequest] = httpMarshalling.unmarshaller
  private implicit val u3: FromEntityUnmarshaller[CreateApiTokenRequest] = httpMarshalling.unmarshaller
  private implicit val u4: FromEntityUnmarshaller[TokenId] = httpMarshalling.unmarshaller
  private implicit val u5: FromEntityUnmarshaller[UserId] = httpMarshalling.unmarshaller
  private implicit val u6: FromEntityUnmarshaller[CreateUserRequest] = httpMarshalling.unmarshaller
  private implicit val u7: FromEntityUnmarshaller[JsonWebKeySet] = httpMarshalling.unmarshaller


  lazy val bgAuthProviderRestTimeout: FiniteDuration = config.getDuration("bg.auth.provider.rest-api-timeout")

  lazy val bgAuthProviderAudit: AuthProviderAudit[A] = new LoggingProviderAudit()

  def bgAuthProviderDefaultRoutes: Route =
    bgAuthHandleExceptions(None) {
      concat(
        bgAuthProviderLoginRoutes,
        bgAuthProviderSelfUserManagement,
        bgAuthProviderUserManagement
      )
    }


  def bgAuthProviderLoginRoutes: Route = {
    pathPrefix("auth") {
      bgAuthCsrf {
        concat(
          concat(
            path("public-key") {
              bgAuthGetKey()
            },
            path(pm = ".well-known") {
              bgAuthGetJWKS()
            }

          ),
          concat(
            path("login") {
              login
            },
            path("verify") {
              verify
            }
          )
        )
      }
    }
  }

  def bgAuthHandleExceptions(authInfo: Option[AuthInfo[A]]): Directive0 = handleExceptions(ExceptionHandler {
    case ex: PasswordLoginAlreadyTaken =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.Conflict, entity = "Login already taken"))
      }
    case ex: PasswordValidationError =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.Forbidden, entity = "Bad password"))
      }
    case ex: PasswordNotFound =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.Forbidden, entity = "Password not found"))
      }
    case ex: TokenDoesNotExist =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.NotFound, entity = "Not found"))
      }
    case ex: TokenAlreadyExist =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.Conflict, entity = "Token already exists"))
      }
    case ex: TokenIllegalState =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "Internal error"))
      }
    case ex: UserDoesNotExist =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.NotFound, entity = "Not found"))
      }
    case ex: UserLocked =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.Forbidden, entity = "Not allowed"))
      }
    case ex: UserAlreadyExist =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.Conflict, entity = "User already exists"))
      }
    case ex: UserIllegalState =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "Internal error"))
      }
    case ex: BadRequestFormat =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.BadRequest, entity = ex.message))
      }
    case ex: ProviderException =>
      bgAuthProviderAudit.log(ProviderError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "Internal error"))
      }
    case ex =>
      bgAuthProviderAudit.log(ProviderInternalError(authInfo, ex)) {
        complete(HttpResponse(StatusCodes.InternalServerError, entity = "Internal error"))
      }
  })

  def bgAuthProviderSelfUserManagement: Route = {
    pathPrefix("current-user") {
      concat(
        path("details") {
          bgAuthCurrentUserDetails()
        },
        path("change-password") {
          bgAuthCurrentUserPassChange()
        },
        path("api-token-create") {
          bgAuthCreateApiToken(Set(Grant.API))
        },
        path("api-token-invalidate") {
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
        bgAuthHandleExceptions(Some(authInfo)) {
          implicit val to: Timeout = bgAuthProviderRestTimeout
          onSuccess(bgAuthUserManager.getUser(authInfo.userInfo.userId)) {
            details =>
              bgAuthProviderAudit.log(GetUserDetails(authInfo, details)) {
                complete(details)
              }
          }
        }
      }
    }

  def bgAuthGetKey(): Route =
    get {
      complete(bgAuthKeys.publicKeyBase64)
    }

  def bgAuthGetJWKS(): Route = get {
    val rsaKey = bgAuthKeys.publicKey.asInstanceOf[RSAPublicKey]

    complete(JsonWebKeySet(List(JsonWebKey(
      n = Base64.getUrlEncoder.encodeToString(rsaKey.getModulus.toByteArray),
      e = Base64.getUrlEncoder.encodeToString(rsaKey.getPublicExponent.toByteArray),
      kid = "default"
    ))))
  }

  def bgAuthCreateApiToken(grants: Set[Grant], grantRequired: Grant*): Route = {
    post {
      bgAuthGrantsAllowed(grantRequired: _*) { authInfo =>
        bgAuthHandleExceptions(Some(authInfo)) {
          entity(as[CreateApiTokenRequest]) { request =>
            implicit val to: Timeout = bgAuthProviderRestTimeout
            val creationProcess = bgAuthManager.createApiToken(
              authInfo.userInfo.userId,
              request.asRoleSet,
              grants,
              request.expiryDate,
              request.description
            )
            onSuccess(creationProcess) { accessToken =>
              bgAuthProviderAudit.log(CreateApiToken(authInfo, request, accessToken.tokenId)) {
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
    }
  }

  def bgAuthCreateUser(grantRequired: common.Grant*): Route =
    post {
      bgAuthGrantsAllowed(grantRequired: _*) { authInfo =>
        bgAuthHandleExceptions(Some(authInfo)) {
          entity(as[CreateUserRequest]) { request =>
            implicit val to: Timeout = bgAuthProviderRestTimeout
            val userDetails = UserDetailedAttributes(
              request.userAttributed,
              request.roles.map(it => Role(it)).toSet,
              request.additionalAttributes
            )
            onSuccess(bgAuthManager.createUser(userDetails, request.credentials)) { userId =>
              bgAuthProviderAudit.log(CreateUser(authInfo, request, userId)) {
                complete(userId)
              }
            }
          }
        }
      }
    }

  def bgAuthInvalidateApiToken(grantRequired: Grant*): Route =
    put {
      bgAuthGrantsAllowed(grantRequired: _*) { authInfo =>
        bgAuthHandleExceptions(Some(authInfo)) {
          implicit val to: Timeout = bgAuthProviderRestTimeout
          entity(as[TokenId]) {
            case TokenId(a) if a == null || a.trim.isEmpty =>
              throw BadRequestFormat("Token id cannot be empty")
            case id =>
              val process = for (
                subjectId <- bgAuthTokenManager.getSubject(id) if subjectId == authInfo.userInfo.userId;
                inv <- bgAuthManager.invalidateApiToken(id)
              ) yield inv
              onSuccess(process) { _ =>
                bgAuthProviderAudit.log(RevokeApiToken(authInfo, id)) {
                  complete(JustSuccess())
                }
              }
          }
        }
      }
    }

  def bgAuthLockUser(grantRequired: Grant): Route = {
    put {
      bgAuthGrantsAllowed(grantRequired) { authInfo =>
        bgAuthHandleExceptions(Some(authInfo)) {
          entity(as[UserId]) { id =>
            implicit val to: Timeout = bgAuthProviderRestTimeout
            onSuccess(bgAuthManager.lockUser(id)) { _ =>
              bgAuthProviderAudit.log(LockUser(authInfo, id)) {
                complete(JustSuccess())
              }
            }
          }
        }
      }
    }
  }

  def bgAuthUnLockUser(grantRequired: Grant): Route = {
    put {
      bgAuthGrantsAllowed(grantRequired) { authInfo =>
        bgAuthHandleExceptions(Some(authInfo)) {
          entity(as[UserId]) { id =>
            implicit val to: Timeout = bgAuthProviderRestTimeout
            onSuccess(bgAuthManager.unlockUser(id)) { _ =>
              bgAuthProviderAudit.log(UnLockUser(authInfo, id)) {
                complete(JustSuccess())
              }
            }
          }
        }
      }
    }
  }

  def bgAuthCurrentUserPassChange(grantRequired: Grant*): Route =
    put {
      bgAuthGrantsAllowed(grantRequired: _*) { authInfo =>
        bgAuthHandleExceptions(Some(authInfo)) {
          entity(as[NewPassRequest]) {
            case NewPassRequest(null, _) | NewPassRequest(_, null) =>
              throw BadRequestFormat("Passwords missing")
            case NewPassRequest(oldPassword, newPassword) =>
              authInfo.userInfo.login match {
                case None =>
                  throw BadRequestFormat("User has no password credentials")
                case Some(login) =>
                  val passwordCredentials = PasswordCredentials(login, oldPassword)
                  implicit val to: Timeout = bgAuthProviderRestTimeout
                  val updateProcess = for (
                    userId <- bgAuthPasswordManager.verifyPassword(passwordCredentials) if userId == authInfo.userInfo.userId;
                    done <- bgAuthManager.changePassword(userId, newPassword)
                  ) yield done
                  onSuccess(updateProcess) { _ =>
                    bgAuthProviderAudit.log(ChangePass(authInfo)) {
                      complete(JustSuccess())
                    }
                  }
              }
          }
        }
      }
    }

  private def verify =
    get {
      bgAuthGrantsAllowed() { authInfo =>
        bgAuthHandleExceptions(Some(authInfo)) {
          onSuccess(bgAuthVerifyRestAdditionalAttributes(authInfo)) { additionalAttrs =>
            complete(VerifyRestResponse(
              userLogin = authInfo.userInfo.login,
              userId = authInfo.userInfo.userId.id,
              userAttributes = additionalAttrs,
              tokenId = authInfo.tokenInfo.tokenType.tokenId.id,
              tokenType = authInfo.tokenInfo.tokenType.tokenTypeName,
              tokenGrants = authInfo.userInfo.grants.map(_.name).toList,
              tokenExpiration = authInfo.tokenInfo.expiration,
              tokenIssuedAt = authInfo.tokenInfo.issuedAt,
              tokenIssuer = authInfo.tokenInfo.issuer
            ))
          }
        }
      }
    }

  protected def bgAuthVerifyRestAdditionalAttributes(authInfo: AuthInfo[A]): Future[Map[String, String]] = {
    Future.successful(Map())
  }

  private def login =
    post {
      entity(as[PasswordCredentials]) { login =>
        implicit val to: Timeout = bgAuthProviderRestTimeout
        login match {
          case PasswordCredentials(_, "") | PasswordCredentials("", _) | PasswordCredentials(null, _) | PasswordCredentials(_, null) =>
            throw BadRequestFormat("Credentials missing")
          case _ =>
            onSuccess(bgAuthManager.login(login)) { token =>
              setToken(token) {
                bgAuthProviderAudit.log(AuthProviderAudit.LoginSuccess(token.tokenType.tokenId, login.login)) {
                  complete(SuccessfulLoginResult())
                }
              }
            }
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
                           httpOnly: Boolean) = {
    val cookie = HttpCookie(
      name = sessionManager.config.sessionCookieConfig.name + nameSuffix,
      value = value,
      expires = Some(expires),
      maxAge = None,
      domain = sessionManager.config.sessionCookieConfig.domain,
      path = sessionManager.config.sessionCookieConfig.path,
      secure = sessionManager.config.sessionCookieConfig.secure,
      httpOnly = httpOnly)
    config.getString("bg.auth.jwt.same-site") match {
      case "lax" => cookie.copy(extension = Some("SameSite=lax"))
      case "strict" => cookie.copy(extension = Some("SameSite=strict"))
      case _ => cookie
    }
  }

}

object BgAuthProviderDirectives {

  case class JsonWebKeySet(keys: List[JsonWebKey])

  /**
    *
    * @param n   modulus for a standard pem
    * @param e   exponent for a standard pem
    * @param kty key type
    * @param alg algorithm for the keye
    * @param use how the key was meant to be used
    * @param kid unique identifier for the key
    */
  case class JsonWebKey(n: String, e: String, kty: String = "RSA", alg: String = "RS256", use: String = "sig", kid: String)


  case class SuccessfulLoginResult()
    extends JacksonJsonSerializable

  case class JustSuccess(status: String = "ok")
    extends JacksonJsonSerializable

  case class NewPassRequest(oldPassword: String, newPassword: String)
    extends JacksonJsonSerializable

  case class VerifyRestResponse(userLogin: Option[String],
                                userId: String,
                                userAttributes: Map[String, String],
                                tokenId: String,
                                tokenType: String,
                                tokenGrants: List[String],
                                tokenExpiration: Date,
                                tokenIssuedAt: Date,
                                tokenIssuer: Option[String]
                               )
    extends JacksonJsonSerializable

  case class CreateApiTokenResponse(tokenId: String,
                                    token: String)
    extends JacksonJsonSerializable

  case class CreateApiTokenRequest(allRoles: Boolean,
                                   roles: List[String],
                                   expiryDate: Date,
                                   description: String)
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
