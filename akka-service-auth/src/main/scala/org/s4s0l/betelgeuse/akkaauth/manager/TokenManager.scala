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
import org.s4s0l.betelgeuse.akkaauth.common.{TokenId, TokenInfo, TokenType, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.TokenManager.TokenCreationParams

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait TokenManager {

  /**
    * saves token, does not persist serialized token itself,
    * persists token attributes for debugging info.
    * Token saved is valid after save until revoke is called.
    * Apart from explicit attributes should persist type of token.
    */
  def saveToken(creationParams: TokenCreationParams)
               (implicit ec: ExecutionContext,
                timeout: Timeout,
                sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  /**
    * marks token as revoked, all subsequent calls to isValid return false.
    * When called on revoked token does nothing.
    */
  def revokeToken(tokenId: TokenId)
                 (implicit ec: ExecutionContext,
                  timeout: Timeout,
                  sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  /**
    * checks if token was revoked
    */
  def isValid(tokenId: TokenId)
             (implicit ec: ExecutionContext,
              timeout: Timeout,
              sender: ActorRef = ActorRef.noSender)
  : Future[Boolean]

  def getSubject(tokenId: TokenId)
                (implicit ec: ExecutionContext,
                 timeout: Timeout,
                 sender: ActorRef = ActorRef.noSender)
  : Future[UserId]

}

object TokenManager {

  case class TokenPurpose(purposeName: String) extends AnyVal

  case class TokenCreationParams(token: TokenInfo[_ <: TokenType],
                                 userId: UserId,
                                 purpose: TokenPurpose,
                                 description: Option[String]
                                )

}
