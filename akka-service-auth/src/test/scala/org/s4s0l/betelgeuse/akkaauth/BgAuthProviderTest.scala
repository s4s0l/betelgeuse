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

import java.util.{Base64, Date}

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.headers.{HttpCookie, _}
import akka.http.scaladsl.model.{StatusCodes, _}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.BgAuthProviderDirectives._
import org.s4s0l.betelgeuse.akkaauth.BgAuthProviderTest._
import org.s4s0l.betelgeuse.akkaauth.common.UserAttributes.{Male, PhoneNumber}
import org.s4s0l.betelgeuse.akkaauth.common.{PasswordCredentials, TokenId, UserAttributes, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{Role, UserDetailedAttributes, UserDetailedInfo}
import org.s4s0l.betelgeuse.akkacommons.serialization.{HttpMarshalling, JacksonJsonSerializer}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

/**
  * @author Marcin Wielgus
  */
class BgAuthProviderTest
  extends BgTestRoach
    with ScalatestRouteTest
    with ScalaFutures {

  val defaultTimeout: FiniteDuration = 15.seconds

  implicit val timeout: Timeout = defaultTimeout

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(defaultTimeout)

  implicit val self: ActorRef = ActorRef.noSender

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(defaultTimeout, 300.millis)

  private val provider = testWith(new BgAuthProviderTestProvider())

  private val client = testWith(new BgAuthProviderTestClient())

  private def providerRoute = Route.seal(provider.service.httpRoute)

  private def clientRoute = Route.seal(client.service.httpRoute)

  feature("Public key is accessible from rest") {
    scenario("Public key is unprotected") {
      Get("/auth/public-key") ~> providerRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe provider.service.bgAuthKeys.publicKeyBase64
      }
    }

    scenario("Public keys via jwks are available unprotected") {
      Get("/auth/.well-known") ~> providerRoute ~> check {
        status shouldBe StatusCodes.OK
        implicit val u7: FromEntityUnmarshaller[JsonWebKeySet] = provider.service.httpMarshalling.unmarshaller
        val rsp = responseAs[JsonWebKeySet]
        rsp.keys should not be empty
        rsp.keys.head.kid shouldBe "default"
      }
    }
  }

  feature("Apis are CSRF protected") {

    scenario("Verify provides csrf cookie when no auth info provided") {
      val cookie = Get("/auth/verify") ~> providerRoute ~> check {
        status shouldBe StatusCodes.Unauthorized
        headers
          .collect {
            case `Set-Cookie`(x) ⇒ x
          }
          .filter(_.name == "XSRF-TOKEN")
          .head
      }
      assertCookieScope(httpOnly = false)(cookie)
    }


    scenario("CSRF cookie has proper params") {
      val cookie = Get("/sample/csrf/get") ~> providerRoute ~> check {
        status shouldBe StatusCodes.OK
        headers
          .collect {
            case `Set-Cookie`(x) ⇒ x
          }
          .filter(_.name == "XSRF-TOKEN")
          .head
      }
      assertCookieScope(httpOnly = false)(cookie)
    }

    scenario("CSRF tokens are random") {
      assert(fetchCsrf() != fetchCsrf())
    }

    scenario("CSRF tokens are required for non get requests") {
      Post("/sample/csrf/post") ~> providerRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("CSRF tokens are not generated when provided") {
      Get("/sample/csrf/get") ~>
        addHeader("X-XSRF-TOKEN", "123456") ~>
        addHeader(Cookie("XSRF-TOKEN" -> "123456")) ~>
        providerRoute ~>
        check {
          status shouldBe StatusCodes.OK
          headers.collect {
            case `Set-Cookie`(x) ⇒ x
          } shouldBe empty
        }
    }
  }

  feature("Users can log in and out") {


    scenario("Logged in user can be verified") {
      ensureAdminExists()
      val context = fetchJwt(fetchCsrf())
      context.verifySecured(Get("/auth/verify"), providerRoute) {
        val resp = responseAs[VerifyRestResponse]
        assert(resp.tokenExpiration.getTime > resp.tokenIssuedAt.getTime)
        assert(resp.userLogin.contains(adminPasswordCredentials.login))
        assert(resp.tokenId.length > 10)
      }
      context.verifySecuredHeaderOnly(Get("/auth/verify"), providerRoute) {}
    }

    scenario("Logging in With bad passwords should fail") {
      ensureAdminExists()
      val csrf = fetchCsrf()

      Post("/auth/login", adminPasswordCredentials.copy(password = "")) ~>
        csrf.csrfHeaders ~> providerRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
      Post("/auth/login", adminPasswordCredentials.copy(password = null)) ~>
        csrf.csrfHeaders ~> providerRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
      Post("/auth/login", adminPasswordCredentials.copy(login = null)) ~>
        csrf.csrfHeaders ~> providerRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
      Post("/auth/login", adminPasswordCredentials.copy(login = "")) ~>
        csrf.csrfHeaders ~> providerRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
      Post("/auth/login", adminPasswordCredentials.copy(password = "wrong")) ~>
        csrf.csrfHeaders ~> providerRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      Post("/auth/login", adminPasswordCredentials.copy(login = "wrong")) ~>
        csrf.csrfHeaders ~> providerRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      Post("/auth/login", Map[String, String]()) ~>
        csrf.csrfHeaders ~> providerRoute ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("Logging in should produce proper cookies") {
      ensureAdminExists()
      val csrf = fetchCsrf()

      Post("/auth/login", adminPasswordCredentials) ~>
        csrf.csrfHeaders ~>
        providerRoute ~>
        check {
          status shouldBe StatusCodes.OK
          val sessionHeaders = headers
            .collect { case `Set-Cookie`(x) ⇒ x }
            .filter(_.name.startsWith(jwtCookiesPrefix))
            .map(it => it.name -> it)
            .toMap

          assertCookieScope(httpOnly = false)(sessionHeaders(jwtCookiesPrefix + "_header"))
          assertCookieScope(httpOnly = false)(sessionHeaders(jwtCookiesPrefix + "_claims"))
          assertCookieScope(httpOnly = true)(sessionHeaders(jwtCookiesPrefix + "_signature"))

          val decodedHeader = Base64.getDecoder.decode(sessionHeaders(jwtCookiesPrefix + "_header").value)
          val jsonHeader = jsonSerializer.simpleFromBinary[Map[String, String]](decodedHeader)

          assert(jsonHeader == Map("typ" -> "JWT", "alg" -> "RS256", "cty" -> "application/json", "kid" -> "default"))

          val decodedClaims = Base64.getDecoder.decode(sessionHeaders(jwtCookiesPrefix + "_claims").value)
          val jsonClaims = jsonSerializer.simpleFromBinary[Map[String, Any]](decodedClaims)

          assert(jsonClaims("exp").asInstanceOf[Long] > 0)
          assert(jsonClaims("iat").asInstanceOf[Long] > 0)
          assert(jsonClaims("jti").asInstanceOf[String].length > 0)
          assert(jsonClaims("sub").asInstanceOf[String].length > 0)

          val staticClaims = jsonClaims - "jti" - "exp" - "iat" - "sub"
          assert(staticClaims == Map(
            "tokenType" -> "access",
            "attributes" -> Map("something" -> "has a value"),
            "roles" -> List("MASTER"),
            "login" -> "admin",
            "iss" -> "betelgeuse-issuer")
          )
        }
    }
  }

  feature("Users can manage themselfs") {
    scenario("Getting user details") {
      val userId = ensureAdminExists()
      val context = fetchJwt(fetchCsrf())
      context.verifySecured(Get("/current-user/details"), providerRoute) {
        responseAs[UserDetailedInfo] shouldBe adminUserDetails(userId)
      }
      context.verifySecuredHeaderOnly(Get("/current-user/details"), providerRoute) {
        responseAs[UserDetailedInfo] shouldBe adminUserDetails(userId)
      }
    }

    scenario("Changing password") {
      ensureAdminExists()
      val context = fetchJwt(fetchCsrf())
      context.verifySecured(Put("/current-user/change-password", NewPassRequest(adminPasswordCredentials.password, "TheNewPassword")), providerRoute) {
        responseAs[JustSuccess] shouldBe JustSuccess()
      }
      context.verifySecuredHeaderOnly(Put("/current-user/change-password", NewPassRequest("TheNewPassword", adminPasswordCredentials.password)), providerRoute) {
        responseAs[JustSuccess] shouldBe JustSuccess()
      }
    }

    scenario("changing pass with wrong input") {
      ensureAdminExists()
      val context = fetchJwt(fetchCsrf())
      context.callCookieSecured(Put("/current-user/change-password", NewPassRequest("invalid", "TheNewPassword")), providerRoute) {
        status shouldBe StatusCodes.Forbidden
      }
      context.callCookieSecured(Put("/current-user/change-password", NewPassRequest(null, "TheNewPassword")), providerRoute) {
        status shouldBe StatusCodes.BadRequest
      }
      context.callCookieSecured(Put("/current-user/change-password", NewPassRequest(adminPasswordCredentials.password, null)), providerRoute) {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  feature("Master user can manage other users") {
    scenario("MASTER role can create user") {
      ensureAdminExists()
      val context = fetchJwt(fetchCsrf())
      val attributes = UserAttributes(family_name = Some("family"), phone_number = Some(PhoneNumber("1234")))
      val credentials = PasswordCredentials("user1", "secret")
      val request = CreateUserRequest(attributes, List("ROLE1"), Map("something" -> "special"), Some(credentials))
      val userId = context.verifySecured(Post("/manage-user/create", request), providerRoute) {
        val id = responseAs[UserId]
        assert(id.id.length > 10)
        id
      }
      //user can login
      val userContext = fetchJwt(fetchCsrf(), credentials)
      userContext.verifySecured(Get("/current-user/details"), providerRoute) {
        responseAs[UserDetailedInfo] shouldBe UserDetailedInfo(
          userId,
          UserDetailedAttributes(
            attributes,
            request.roles.map(it => Role(it)).toSet,
            request.additionalAttributes
          ),
          login = Some(credentials.login),
          locked = false
        )
      }
      //and as he does not have role master it cannot create user
      userContext.callCookieSecured(Post("/manage-user/create", request.copy(credentials = Some(PasswordCredentials("login2", "password")))), providerRoute) {
        status shouldBe StatusCodes.Forbidden
      }
      //subsequent calls should fail as user is already created
      context.callCookieSecured(Post("/manage-user/create", request), providerRoute) {
        status shouldBe StatusCodes.Conflict
      }

    }


    scenario("MASTER role can lock and unlock user") {
      ensureAdminExists()
      val context = fetchJwt(fetchCsrf())
      val credentials = PasswordCredentials("user2", "secret")
      val request = CreateUserRequest(UserAttributes(), List(), Map(), Some(credentials))
      val userId = context.verifySecured(Post("/manage-user/create", request), providerRoute) {
        responseAs[UserId]
      }
      val userCsrfContext = fetchCsrf()

      val successfulLogin = fetchJwt(userCsrfContext, credentials)
      //lock is not available for user
      successfulLogin.callCookieSecured(Put("/manage-user/lock", userId), providerRoute) {
        status shouldBe StatusCodes.Forbidden
      }
      //but admin can do it
      context.verifySecured(Put("/manage-user/lock", userId), providerRoute) {
        responseAs[JustSuccess] shouldBe JustSuccess()
      }
      //      /when user is locked user cannot log in
      Post("/auth/login", credentials) ~>
        userCsrfContext.csrfHeaders ~> providerRoute ~>
        check {
          status shouldBe StatusCodes.Forbidden
        }
      context.verifySecured(Put("/manage-user/un-lock", userId), providerRoute) {
        responseAs[JustSuccess] shouldBe JustSuccess()
      }
      //can login again
      val successfulLogin2 = fetchJwt(userCsrfContext, credentials)
      //but regular user cannot access unlock
      successfulLogin2.callCookieSecured(Put("/manage-user/un-lock", userId), providerRoute) {
        status shouldBe StatusCodes.Forbidden
      }

    }
  }
  feature("auth clients can use authentication tokens from provider") {
    scenario("regular cookie based authentication is possible in client") {
      ensureAdminExists()
      val context = fetchJwt(fetchCsrf())
      context.verifySecured(Get("/protected/any"), clientRoute) {
        responseAs[String] shouldBe adminAdditionalValue
      }
      context.verifySecured(Post("/protected/any"), clientRoute) {
        responseAs[String] shouldBe adminAdditionalValue
      }
      context.verifySecuredHeaderOnly(Get("/protected/any"), clientRoute) {
        responseAs[String] shouldBe adminAdditionalValue
      }
      context.verifySecuredHeaderOnly(Post("/protected/any"), clientRoute) {
        responseAs[String] shouldBe adminAdditionalValue
      }
      //admin has no CUSTOM_ROLE so following should not be accessible for him
      context.callCookieSecured(Get("/protected/role"), clientRoute) {
        status shouldBe StatusCodes.Forbidden
      }
      context.callCookieSecured(Post("/protected/role"), clientRoute) {
        status shouldBe StatusCodes.Forbidden
      }
      context.callHeaderSecured(Get("/protected/role"), clientRoute) {
        status shouldBe StatusCodes.Forbidden
      }
      context.callHeaderSecured(Post("/protected/role"), clientRoute) {
        status shouldBe StatusCodes.Forbidden
      }


      //      to test role based we create user
      val credentials = PasswordCredentials("user3", "secret")
      val request = CreateUserRequest(UserAttributes(), List("CUSTOM_ROLE"), Map("something" -> "special"), Some(credentials))
      context.verifySecured(Post("/manage-user/create", request), providerRoute) {
        responseAs[UserId]
      }
      val userContext = fetchJwt(fetchCsrf(), credentials)
      userContext.verifySecured(Get("/protected/role"), clientRoute) {
        responseAs[String] shouldBe "special"
      }
      userContext.verifySecured(Post("/protected/role"), clientRoute) {
        responseAs[String] shouldBe "special"
      }
      userContext.verifySecuredHeaderOnly(Get("/protected/role"), clientRoute) {
        responseAs[String] shouldBe "special"
      }
      userContext.verifySecuredHeaderOnly(Post("/protected/role"), clientRoute) {
        responseAs[String] shouldBe "special"
      }
    }
    scenario("Can call clients using api tokens") {
      ensureAdminExists()
      val context = fetchJwt(fetchCsrf())

      val apiToken1 = context.verifySecured(
        Post("/current-user/api-token-create",
          CreateApiTokenRequest(allRoles = true, List(), future(60), "apiToken1")),
        providerRoute) {
        responseAs[CreateApiTokenResponse]
      }
      //without token it does not work
      Post("/protected/api") ~> clientRoute ~> check {
        status shouldBe StatusCodes.Forbidden //csrf
      }
      Get("/protected/api") ~> clientRoute ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
      //with token it does
      Post("/protected/api") ~> addHeader("Authorization", s"Bearer ${apiToken1.token}") ~> clientRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe adminAdditionalValue
      }
      Get("/protected/api") ~> addHeader("Authorization", s"Bearer ${apiToken1.token}") ~> clientRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe adminAdditionalValue
      }

      //when we invalidate token
      context.verifySecured(
        Put("/current-user/api-token-invalidate",
          TokenId(apiToken1.tokenId)),
        providerRoute) {
        status shouldBe StatusCodes.OK
        responseAs[JustSuccess] shouldBe JustSuccess()
      }
      //token does not work anymore
      Post("/protected/api") ~> addHeader("Authorization", s"Bearer ${apiToken1.token}") ~> clientRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      Get("/protected/api") ~> addHeader("Authorization", s"Bearer ${apiToken1.token}") ~> clientRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("Api tokens have respected validity time") {
      ensureAdminExists()
      val context = fetchJwt(fetchCsrf())

      val apiToken1 = context.verifySecured(
        Post("/current-user/api-token-create",
          CreateApiTokenRequest(allRoles = true, List(), future(60), "apiToken1")),
        providerRoute) {
        responseAs[CreateApiTokenResponse]
      }
      Get("/protected/master") ~>
        addHeader("Authorization", s"Bearer ${apiToken1.token}") ~>
        clientRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe adminAdditionalValue
      }
      val apiToken2 = context.verifySecured(
        Post("/current-user/api-token-create",
          CreateApiTokenRequest(allRoles = true, List(), future(3), "apiToken2")),
        providerRoute) {
        responseAs[CreateApiTokenResponse]
      }
      Thread.sleep(4000)
      Get("/protected/master") ~>
        addHeader("Authorization", s"Bearer ${apiToken2.token}") ~>
        clientRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("Attempt to grant more roles to token than user has") {
      val context = fetchJwt(fetchCsrf())
      //user does not have role DUMMY
      val apiToken1 = context.verifySecured(
        Post("/current-user/api-token-create",
          CreateApiTokenRequest(allRoles = false, List("DUMMY", "MASTER"), future(60), "apiToken1")),
        providerRoute) {
        responseAs[CreateApiTokenResponse]
      }
      //here only MASTER is required
      Get("/protected/master") ~>
        addHeader("Authorization", s"Bearer ${apiToken1.token}") ~>
        clientRoute ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe adminAdditionalValue
      }
      //here grant DUMMY is required it should not be added to grants so this request should fail
      Get("/protected/dummy") ~>
        addHeader("Authorization", s"Bearer ${apiToken1.token}") ~>
        clientRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }

    }
    scenario("Token revoke invalid requests") {
      val context = fetchJwt(fetchCsrf())
      //user does not have role DUMMY
      val apiToken1 = context.verifySecured(
        Post("/current-user/api-token-create",
          CreateApiTokenRequest(allRoles = false, List("DUMMY", "MASTER"), future(60), "apiToken1")),
        providerRoute) {
        responseAs[CreateApiTokenResponse]
      }
      context.callCookieSecured(
        Put("/current-user/api-token-invalidate",
          TokenId("")),
        providerRoute) {
        status shouldBe StatusCodes.BadRequest
      }

    }
  }


  def fetchJwt(csrf: LoginContext, credentials: PasswordCredentials = adminPasswordCredentials): LoginContext = {
    val cookies = Post("/auth/login", credentials) ~>
      csrf.csrfHeaders ~>
      providerRoute ~>
      check {
        status shouldBe StatusCodes.OK
        headers
          .collect { case `Set-Cookie`(x) ⇒ x }
          .filter(_.name.startsWith(jwtCookiesPrefix))
      }
    csrf.copy(jwt = (
      cookies.find(_.name == jwtCookiesPrefix + "_header").get.value,
      cookies.find(_.name == jwtCookiesPrefix + "_claims").get.value,
      cookies.find(_.name == jwtCookiesPrefix + "_signature").get.value
    ))
  }

  def fetchCsrf(): LoginContext = {
    val csrf = Get("/auth/public-key") ~> providerRoute ~> check {
      status shouldBe StatusCodes.OK
      headers
        .collect {
          case `Set-Cookie`(x) ⇒ x
        }
        .filter(_.name == "XSRF-TOKEN")
        .head.value
    }
    LoginContext(csrf, ("_", "_", "_"))
  }

  private def assertCookieScope(httpOnly: Boolean)(cookie: HttpCookie) = {
    assert(cookie.path.contains("/"))
    assert(cookie.domain.isEmpty)
    assert(!cookie.secure)
    assert(cookie.httpOnly == httpOnly)
    assert(cookie.extension.contains("SameSite=lax"))
  }

  def ensureAdminExists(): UserId = {
    val p = Promise[Done]()
    client.service.bgAuthOnPublicKeyAvailable {
      p.success(Done)
    }
    whenReady(p.future) {
      _ =>
    }
    val adminCreationProcess =
      provider.service.bgAuthPasswordManager.verifyLogin(adminPasswordCredentials.login)
        .flatMap {
          case Some(userId) => Future.successful(userId)
          case None =>
            provider.service.bgAuthManager
              .createUser(adminDetailedAttributes, Some(adminPasswordCredentials))
        }

    whenReady(adminCreationProcess) {
      userID =>
        assert(userID.id.length > 10)
        userID
    }
  }

  case class LoginContext(csrf: String, jwt: (String, String, String)) {

    def csrfHeaders: WithTransformerConcatenation[HttpRequest, HttpRequest] = {
      addHeader("X-XSRF-TOKEN", csrf) ~>
        addHeader(Cookie("XSRF-TOKEN" -> csrf))
    }

    def jwtHeader: WithTransformerConcatenation[HttpRequest, HttpRequest] = {
      val (h, c, s) = jwt
      addHeader("Authorization", s"Bearer $h.$c.$s")
    }

    def jwtUnparsable: LoginContext = {
      val (h, c, s) = jwt
      copy(jwt = (s"X$h", c, s))
    }

    def jwtDataTampered: LoginContext = {
      val (h, c, s) = jwt
      val decodedClaims = Base64.getUrlDecoder.decode(c)
      val jsonClaims = jsonSerializer.simpleFromBinary[Map[String, Any]](decodedClaims)
      val tampered = jsonClaims ++ Map("roles" -> List("ATTACK_DUMMY_ROLE"))
      val encoded = Base64.getUrlEncoder.encodeToString(jsonSerializer.toBinary(tampered))
      copy(jwt = (h, encoded.replace("=", ""), s))
    }

    def jwtSigFault: LoginContext = {
      val (h, c, s) = jwt
      copy(jwt = (h, c, s + "a"))
    }

    def jwtCookies: WithTransformerConcatenation[HttpRequest, HttpRequest] = {
      val (h, c, s) = jwt
      addHeader(Cookie(
        jwtCookiesPrefix + "_header" -> h,
        jwtCookiesPrefix + "_claims" -> c,
        jwtCookiesPrefix + "_signature" -> s))
    }

    def jwtCsrfCookies: WithTransformerConcatenation[HttpRequest, HttpRequest] = {
      val (h, c, s) = jwt
      addHeader("X-XSRF-TOKEN", csrf) ~>
        addHeader(Cookie("XSRF-TOKEN" -> csrf,
          jwtCookiesPrefix + "_header" -> h,
          jwtCookiesPrefix + "_claims" -> c,
          jwtCookiesPrefix + "_signature" -> s))
    }


    def verifySecuredHeaderOnly[T](request: HttpRequest, route: server.Route)(body: => T): T = {
      request ~> jwtHeader ~>
        route ~>
        check {
          status shouldBe StatusCodes.OK
          headers
            .collect { case `Set-Cookie`(x) ⇒ x }
            .filter(_.name == "XSRF-TOKEN") shouldBe empty
          body
        }
    }

    def verifySecured[T](request: HttpRequest, route: server.Route)(body: => T): T = {
      request ~>
        route ~> check {
        if (request.method == HttpMethods.GET) {
          //for gets there is no csrf validation then unauthorized
          status shouldBe StatusCodes.Unauthorized
        } else {
          //missing csrf
          status shouldBe StatusCodes.Forbidden
        }
      }
      request ~> csrfHeaders ~>
        route ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
      if (request.method != HttpMethods.GET) {
        request ~> jwtCookies ~>
          route ~> check {
          status shouldBe StatusCodes.Forbidden
        }
      }
      request ~> jwtDataTampered.jwtCsrfCookies ~>
        route ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      request ~> jwtSigFault.jwtCsrfCookies ~>
        route ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      request ~> jwtUnparsable.jwtCsrfCookies ~>
        route ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      request ~> jwtDataTampered.jwtHeader ~>
        route ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      request ~> jwtSigFault.jwtHeader ~>
        route ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      request ~> jwtUnparsable.jwtHeader ~>
        route ~> check {
        status shouldBe StatusCodes.Forbidden
      }
      callCookieSecured(request, route) {
        status shouldBe StatusCodes.OK
        body
      }
    }

    def callCookieSecured[T](request: HttpRequest, route: Route)(body: => T): T = {
      request ~> jwtCsrfCookies ~>
        route ~>
        check {
          body
        }
    }

    def callHeaderSecured[T](request: HttpRequest, route: Route)(body: => T): T = {
      request ~> jwtHeader ~>
        route ~>
        check {
          body
        }
    }
  }

}

object BgAuthProviderTest {

  val jwtCookiesPrefix = "_sessiondata"
  private val jsonSerializer = new JacksonJsonSerializer()
  val m = new HttpMarshalling(jsonSerializer)
  implicit val m1: ToEntityMarshaller[PasswordCredentials] = m.marshaller
  implicit val m2: ToEntityMarshaller[Map[String, String]] = m.marshaller
  implicit val m3: ToEntityMarshaller[NewPassRequest] = m.marshaller
  implicit val m4: ToEntityMarshaller[CreateUserRequest] = m.marshaller
  implicit val m5: ToEntityMarshaller[UserId] = m.marshaller
  implicit val m6: ToEntityMarshaller[CreateApiTokenRequest] = m.marshaller
  implicit val m7: ToEntityMarshaller[TokenId] = m.marshaller
  implicit val u1: FromEntityUnmarshaller[UserDetailedInfo] = m.unmarshaller
  implicit val u2: FromEntityUnmarshaller[JustSuccess] = m.unmarshaller
  implicit val u3: FromEntityUnmarshaller[UserId] = m.unmarshaller
  implicit val u4: FromEntityUnmarshaller[CreateApiTokenResponse] = m.unmarshaller
  implicit val u5: FromEntityUnmarshaller[VerifyRestResponse] = m.unmarshaller
  private val adminAdditionalValue = "has a value"
  val adminDetailedAttributes = UserDetailedAttributes(
    UserAttributes(
      given_name = Some("Heniek"),
      gender = Some(Male()),
      birthDate = Some(new Date())
    ),
    Set(Role.MASTER),
    Map("something" -> adminAdditionalValue)
  )
  val adminPasswordCredentials = PasswordCredentials("admin", "adminadmin")

  def adminUserDetails(userId: UserId, locked: Boolean = false) = UserDetailedInfo(
    userId, adminDetailedAttributes, Some("admin"), locked
  )

  def future(seconds: Long): Date = new Date(new Date().getTime + 1000 * seconds)
}