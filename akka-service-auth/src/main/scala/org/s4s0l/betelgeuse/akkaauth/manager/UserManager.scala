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

import akka.Done
import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.common.{UserAttributes, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{Role, UserDetailedInfo}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait UserManager {

  /**
    * Generated next UUID, is NOT be sequential.
    */
  def generateUserId()
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[UserId]

  /**
    * creates a user, when user with given id already exists will fail.
    */
  def createUser(userInfo: UserDetailedInfo)
                (implicit ec: ExecutionContext,
                 timeout: Timeout,
                 sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  def updateRoles(userId: UserId,
                  roles: Set[Role])
                 (implicit ec: ExecutionContext,
                  timeout: Timeout,
                  sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  /**
    * Updates additional attributes. When 'attr' -> None, attribute will be removed.
    * When 'attr' -> Some(value), attr value will be set to value, when
    * user already contains some key which is not included in attrs its value will
    * not be affected.
    */
  def updateAdditionalAttributes(userId: UserId,
                                 attrs: Map[String, Option[String]])
                                (implicit ec: ExecutionContext,
                                 timeout: Timeout,
                                 sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  def getUser(userId: UserId)
             (implicit ec: ExecutionContext,
              timeout: Timeout,
              sender: ActorRef = ActorRef.noSender)
  : Future[UserDetailedInfo]

  /**
    *
    * sets locked flag. If already set it has no effect and will be always successful.
    */
  def lockUser(userId: UserId)
              (implicit ec: ExecutionContext,
               timeout: Timeout,
               sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  /**
    *
    * sets locked flag. If already set it has no effect and will be always successful.
    */
  def unLockUser(userId: UserId)
                (implicit ec: ExecutionContext,
                 timeout: Timeout,
                 sender: ActorRef = ActorRef.noSender)
  : Future[Done]
}

object UserManager {

  case class Role(name: String)

  object Role {
    val MASTER = Role("MASTER")
  }

  case class UserDetailedAttributes(
                                     userAttributed: UserAttributes,
                                     roles: Set[Role],
                                     additionalAttributes: Map[String, String],
                                   )

  case class UserDetailedInfo(
                               userId: UserId,
                               attributes: UserDetailedAttributes,
                               login: Option[String],
                               locked: Boolean
                             )

}
