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
import org.s4s0l.betelgeuse.akkaauth.common.{TokenId, TokenInfo, TokenType}

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
  def saveToken(token: TokenInfo[_ <: TokenType])
               (implicit ec: ExecutionContext)
  : Future[Done]

  /**
    * marks token as revoked, all subsequent calls to isValid return false.
    * When called on revoked token does nothing.
    */
  def revokeToken(tokenId: TokenId)
                 (implicit ec: ExecutionContext)
  : Future[Done]

  /**
    * checks if token was revoked
    */
  def isValid(tokenId: TokenId)
             (implicit ec: ExecutionContext)
  : Future[Boolean]

}
