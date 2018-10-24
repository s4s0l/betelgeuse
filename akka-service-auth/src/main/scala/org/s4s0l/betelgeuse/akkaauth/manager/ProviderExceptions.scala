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

import org.s4s0l.betelgeuse.akkaauth.common.{TokenId, UserId}

/**
  * @author Marcin Wielgus
  */
object ProviderExceptions {

  sealed trait ProviderException

  case class PasswordLoginAlreadyTaken(login: String)
    extends Exception(s"Login has been already initialized: $login")
      with ProviderException

  case class PasswordNotFound(login: String)
    extends Exception(s"No credentials found for login $login")
      with ProviderException

  case class PasswordNotEnabled(login: String)
    extends Exception(s"Password disabled for login $login")
      with ProviderException

  case class PasswordAlreadyEnabled(login: String) extends
    Exception(s"Password is already enabled for login $login")
    with ProviderException

  case class PasswordValidationError(login: String)
    extends Exception(s"Bad password for login $login")
      with ProviderException

  case class TokenDoesNotExist(tokenId: TokenId)
    extends Exception(s"Token does not exist $tokenId")
      with ProviderException

  case class TokenAlreadyExist(tokenId: TokenId)
    extends Exception(s"Token already exist $tokenId")
      with ProviderException

  case class TokenIllegalState(tokenId: TokenId)
    extends Exception(s"Token is in illegal state to perform this operation $tokenId")
      with ProviderException

  case class UserDoesNotExist(userId: UserId)
    extends Exception(s"User does not exist $userId")
      with ProviderException

  case class UserAlreadyExist(userId: UserId)
    extends Exception(s"User already exist $userId")
      with ProviderException

  case class UserIllegalState(userId: UserId)
    extends Exception(s"User is in illegal state to perform this operation $userId")
      with ProviderException

  case class UserLocked(userId: UserId)
    extends Exception(s"User is locked $userId")
      with ProviderException

}
