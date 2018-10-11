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
import org.s4s0l.betelgeuse.akkaauth.common
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{Role, UserDetailedAttributes, UserDetailedInfo}
import org.s4s0l.betelgeuse.akkaauth.manager._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class AuthManagerImpl[A](
                          userManager: UserManager,
                          tokenManager: TokenManager,
                          passwordManager: PasswordManager,
                          additional: AdditionalUserAttrsManager[A],
                          tokenFactory: TokenFactory
                        )
  extends AuthManager[A] {

  override def login(credentials: common.Credentials)
                    (implicit ec: ExecutionContext)
  : Future[AccessToken] = {
    credentials match {
      case pc: PasswordCredentials =>
        for (
          userId <- passwordManager.verifyPassword(pc);
          userDetails <- userManager.getUser(userId) if !userDetails.locked;
          tokenUserInfo <- createTokenUserInfo(
            userDetails = userDetails,
            grants = userDetails.attributes.roles.map(it => Grant(it.name))
          );
          issuedToken <- tokenFactory.issueToken(tokenUserInfo, additional.marshallAttrs(_));
          _ <- tokenManager.saveToken(issuedToken.tokenInfo)
        ) yield issuedToken.tokenInfo.tokenType
    }
  }

  private def createTokenUserInfo(
                                   userDetails: UserDetailedInfo,
                                   grants: Set[Grant])
                                 (implicit ec: ExecutionContext) = {
    additional.mapAttrsToToken(userDetails)
      .map { tokenAttributes =>
        common.UserInfo(
          login = userDetails.login,
          userId = userDetails.userId,
          grants = grants,
          attributes = tokenAttributes)
      }

  }

  override def changePassword(userId: common.UserId, newPassword: String)
                             (implicit ec: ExecutionContext)
  : Future[Done] = {
    for (
      userDetails <- userManager.getUser(userId) if !userDetails.locked;
      res <- passwordManager.updatePassword(PasswordCredentials(userDetails.login.get, newPassword))
    ) yield res
  }

  override def lockUser(userId: common.UserId)
                       (implicit ec: ExecutionContext)
  : Future[Done] = {
    //TODO lock all tokens
    userManager.lockUser(userId)
  }

  override def unlockUser(userId: common.UserId)
                         (implicit ec: ExecutionContext)
  : Future[Done] = {
    userManager.unLockUser(userId)
  }

  override def createUser(attrs: UserDetailedAttributes,
                          password: Option[Credentials])
                         (implicit ec: ExecutionContext)
  : Future[common.UserId] = {
    password match {
      case None =>
        for (
          userId <- userManager.generateUserId();
          _ <- userManager.createUser(UserDetailedInfo(
            userId = userId,
            attributes = attrs,
            login = None,
            locked = false
          ))
        ) yield userId
      case Some(pc: PasswordCredentials) =>
        for (
          userId <- userManager.generateUserId();
          _ <- passwordManager.createPassword(userId, pc);
          _ <- userManager.createUser(UserDetailedInfo(
            userId = userId,
            attributes = attrs,
            login = Some(pc.login),
            locked = false
          ));
          _ <- passwordManager.enablePassword(pc.login)
        ) yield userId
    }
  }

  override def createApiToken(userId: UserId,
                              roles: Set[Role],
                              grants: Set[Grant],
                              expiryDate: Date)
                             (implicit ec: ExecutionContext)
  : Future[common.AccessToken] = {
    for (
      userDetails <- userManager.getUser(userId) if !userDetails.locked;
      tokenAttrs <- createTokenUserInfo(
        userDetails = userDetails,
        grants = userDetails.attributes.roles
          .intersect(roles)
          .map(it => Grant(it.name)) ++ grants
      );
      token <- tokenFactory.issueToken(tokenAttrs, additional.marshallAttrs(_));
      _ <- tokenManager.saveToken(token.tokenInfo)
    ) yield token.tokenInfo.tokenType
  }

  override def invalidateApiToken(tokenId: TokenId)
                                 (implicit ec: ExecutionContext)
  : Future[Done] = {
    tokenManager.revokeToken(tokenId)
  }

  override def resolveApiToken(accessToken: common.AccessToken)
                              (implicit ec: ExecutionContext)
  : Future[common.AccessToken] = {
    for (
      authInfo <- tokenFactory.verify(accessToken, additional.unMarshallAttrs);
      valid <- tokenManager.isValid(authInfo.tokenInfo.tokenType.tokenId) if valid
    ) yield authInfo.tokenInfo.tokenType
  }
}
