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

package org.s4s0l.betelgeuse.akkaauth.manager

import java.util.Date

import akka.Done
import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.client.TokenResolver
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.AuthManager.RoleSet
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{Role, UserDetailedAttributes}

import scala.concurrent.{ExecutionContext, Future}


/**
  * @author Marcin Wielgus
  */
trait AuthManager[A] extends TokenResolver {

  def login(credentials: Credentials)
           (implicit ec: ExecutionContext,
            timeout: Timeout,
            sender: ActorRef = ActorRef.noSender)
  : Future[TokenInfo[AccessToken]]

  def changePassword(userId: UserId, newPassword: String)
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  def lockUser(userId: UserId)
              (implicit ec: ExecutionContext,
               timeout: Timeout,
               sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  def unlockUser(userId: UserId)
                (implicit ec: ExecutionContext,
                 timeout: Timeout,
                 sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  def createUser(attrs: UserDetailedAttributes,
                 password: Option[Credentials])
                (implicit ec: ExecutionContext,
                 timeout: Timeout,
                 sender: ActorRef = ActorRef.noSender)
  : Future[UserId]

  def createApiToken(userId: UserId,
                     roles: RoleSet,
                     grants: Set[Grant],
                     expiryDate: Date,
                     description: String)
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[AccessToken]

  def invalidateApiToken(tokenId: TokenId)
                        (implicit ec: ExecutionContext,
                         timeout: Timeout,
                         sender: ActorRef = ActorRef.noSender)
  : Future[Done]

}

object AuthManager {

  sealed trait RoleSet

  case class AllRoles() extends RoleSet

  case class GivenRoles(roles: Set[Role]) extends RoleSet

}
