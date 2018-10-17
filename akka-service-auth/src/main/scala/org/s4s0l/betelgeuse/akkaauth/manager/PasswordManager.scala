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
import org.s4s0l.betelgeuse.akkaauth.common.{PasswordCredentials, UserId}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait PasswordManager {

  /**
    * creates password credentials for user. Initially password will be disabled.
    * When password is disabled [[verifyPassword]] always fails.
    * When for given login there exists a password but is removed it will update
    * userId and password, and un remove it.
    *
    */
  def createPassword(userId: UserId, credentials: PasswordCredentials)
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  /**
    * verifies given credentials returning UserId on success.
    * Fails when password is disabled od removed for given login.
    */
  def verifyPassword(credentials: PasswordCredentials)
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[UserId]

  /**
    * Must be called after [[createPassword]] in order to enable password.
    */
  def enablePassword(login: String)
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[UserId]

  /**
    * Updated the password. Should fail when password is
    * disabled od removed for given login.
    */
  def updatePassword(credentials: PasswordCredentials)
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  /**
    * Deletes password for given login. After this operation only create password is allowed for
    * given login.
    */
  def removePassword(login: String)
                    (implicit ec: ExecutionContext,
                     timeout: Timeout,
                     sender: ActorRef = ActorRef.noSender)
  : Future[Done]
}

object PasswordManager {

  case class PasswordLoginAlreadyTaken(login: String) extends Exception(s"Login has been already initialized: $login")

  case class PasswordNotFound(login: String) extends Exception(s"No credentials found for login $login")

  case class PasswordValidationError(login: String) extends Exception(s"Bad password for login $login")

}