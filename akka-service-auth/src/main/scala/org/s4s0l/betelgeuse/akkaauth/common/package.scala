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

import java.util.Date

/**
  * @author Marcin Wielgus
  */
package object common {

  sealed trait Credentials

  case class PasswordCredentials(login: String, password: String) extends Credentials

  case class UserInfo[A](login: Option[String], userId: UserId, grants: Set[Grant], attributes: A)

  case class AuthInfo[A](
                          userInfo: UserInfo[A],
                          tokenInfo: TokenInfo[AccessToken]
                        )


  case class Grant(name: String)

  object Grant {
    val API = Grant("API")
  }

  sealed trait TokenType {
    def tokenId: TokenId

    def serializedToken: SerializedToken
  }

  case class AccessToken(tokenId: TokenId, serializedToken: SerializedToken) extends TokenType

  case class RefreshToken(tokenId: TokenId, serializedToken: SerializedToken) extends TokenType

  case class TokenInfo[T <: TokenType](
                                        expiration: Date,
                                        issuedAt: Date,
                                        issuer: Option[String],
                                        tokenType: T
                                      )

  case class SerializedToken(token: String)

  case class TokenId(id: String)

  case class UserId(id: String)

}
