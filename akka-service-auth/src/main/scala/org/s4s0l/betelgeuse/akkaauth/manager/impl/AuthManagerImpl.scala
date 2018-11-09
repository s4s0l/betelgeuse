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

import java.util.Date

import akka.Done
import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.client.ClientExceptions.TokenInvalidException
import org.s4s0l.betelgeuse.akkaauth.client.TokenVerifier.TokenRevoked
import org.s4s0l.betelgeuse.akkaauth.common
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.AuthManager.RoleSet
import org.s4s0l.betelgeuse.akkaauth.manager.ProviderExceptions.UserLocked
import org.s4s0l.betelgeuse.akkaauth.manager.TokenManager.{TokenCreationParams, TokenPurpose}
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{UserDetailedAttributes, UserDetailedInfo}
import org.s4s0l.betelgeuse.akkaauth.manager._
import org.s4s0l.betelgeuse.akkaauth.manager.impl.AuthManagerImpl._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class AuthManagerImpl[A](
                          userManager: UserManager,
                          tokenManager: TokenManager,
                          passwordManager: PasswordManager,
                          beforeUserCreate: UserDetailedInfo => Future[UserDetailedInfo],
                          tokenFactory: TokenFactory[A]
                        )
  extends AuthManager[A] {


  override def login(credentials: common.Credentials)
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[AuthInfo[A]] = {
    credentials match {
      case pc: PasswordCredentials =>
        for (
          userId <- passwordManager.verifyPassword(pc);
          userDetails <- userManager.getUser(userId).check { ud =>
            if (ud.locked) throw UserLocked(ud.userId)
          };
          issuedToken <- tokenFactory.issueLoginToken(userDetails, userDetails.attributes.roles.map(it => Grant(it.name)));
          _ <- tokenManager
            .saveToken(
              TokenCreationParams(
                token = issuedToken.tokenInfo,
                userId = userId,
                purpose = TokenPurpose("login"),
                description = None))
        ) yield issuedToken
    }
  }

  override def changePassword(userId: common.UserId, newPassword: String)
                             (implicit ec: ExecutionContext,
                              timeout: Timeout,
                              sender: ActorRef = ActorRef.noSender)
  : Future[Done] = {
    for (
      userDetails <- userManager.getUser(userId).check { ud =>
        if (ud.locked) throw UserLocked(ud.userId)
      };
      res <- passwordManager.updatePassword(PasswordCredentials(userDetails.login.get, newPassword))
    ) yield res
  }

  override def lockUser(userId: common.UserId)
                       (implicit ec: ExecutionContext,
                        timeout: Timeout,
                        sender: ActorRef = ActorRef.noSender)
  : Future[Done] = {
    //TODO lock all tokens
    userManager.lockUser(userId)
  }

  override def unlockUser(userId: common.UserId)
                         (implicit ec: ExecutionContext,
                          timeout: Timeout,
                          sender: ActorRef = ActorRef.noSender)
  : Future[Done] = {
    userManager.unLockUser(userId)
  }

  override def createUser(attrs: UserDetailedAttributes,
                          password: Option[Credentials])
                         (implicit ec: ExecutionContext,
                          timeout: Timeout,
                          sender: ActorRef = ActorRef.noSender)
  : Future[common.UserId] = {
    password match {
      case None =>
        for (
          userId <- userManager.generateUserId();
          info <- beforeUserCreate(UserDetailedInfo(
            userId = userId,
            attributes = attrs,
            login = None,
            locked = false
          ));
          _ <- userManager.createUser(info)
        ) yield userId
      case Some(pc: PasswordCredentials) =>
        for (
          userId <- userManager.generateUserId();
          _ <- passwordManager.createPassword(userId, pc);
          info <- beforeUserCreate(UserDetailedInfo(
            userId = userId,
            attributes = attrs,
            login = Some(pc.login),
            locked = false
          ));
          _ <- userManager.createUser(info);
          _ <- passwordManager.enablePassword(pc.login)
        ) yield userId
    }
  }

  override def createApiToken(userId: UserId,
                              roles: RoleSet,
                              grants: Set[Grant],
                              expiryDate: Date,
                              description: String)
                             (implicit ec: ExecutionContext,
                              timeout: Timeout,
                              sender: ActorRef = ActorRef.noSender)
  : Future[common.AccessToken] = {
    for (
      userDetails <- userManager.getUser(userId).check { ud =>
        if (ud.locked) throw UserLocked(ud.userId)
      };
      token <- tokenFactory.issueApiToken(userDetails, calculateGrants(roles, userDetails) ++ grants, expiryDate);
      _ <- tokenManager
        .saveToken(
          TokenCreationParams(
            token = token.tokenInfo,
            userId = userId,
            purpose = TokenPurpose("api"),
            description = Some(description)))
    ) yield token.tokenInfo.tokenType
  }

  private def calculateGrants(roles: RoleSet, userDetails: UserDetailedInfo) = {
    roles match {
      case AuthManager.AllRoles() =>
        userDetails.attributes.roles
          .map(it => Grant(it.name))
      case AuthManager.GivenRoles(roleSet) =>
        userDetails.attributes.roles
          .intersect(roleSet)
          .map(it => Grant(it.name))
    }
  }

  override def invalidateApiToken(tokenId: TokenId)
                                 (implicit ec: ExecutionContext,
                                  timeout: Timeout,
                                  sender: ActorRef = ActorRef.noSender)
  : Future[Done] = {
    tokenManager.revokeToken(tokenId)
  }

  override def resolveToken(accessToken: common.SerializedToken)
                           (implicit ec: ExecutionContext,
                            timeout: Timeout,
                            sender: ActorRef = ActorRef.noSender)
  : Future[common.SerializedToken] = {
    for (
      authInfo <- tokenFactory.verify(accessToken);
      _ <- tokenManager.isValid(authInfo.tokenInfo.tokenType.tokenId).check { valid =>
        if (!valid) throw TokenInvalidException(TokenRevoked())
      }
    ) yield authInfo.tokenInfo.tokenType.serializedToken
  }
}

object AuthManagerImpl {

  implicit class CustomIfComprehension[T](f: Future[T]) {
    def check(fun: T => Unit)(implicit ec: ExecutionContext): Future[T] = {
      f.map { element =>
        fun(element)
        element
      }
    }
  }

}