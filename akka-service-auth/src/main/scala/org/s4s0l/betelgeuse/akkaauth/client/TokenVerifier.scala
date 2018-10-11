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

package org.s4s0l.betelgeuse.akkaauth.client

import org.s4s0l.betelgeuse.akkaauth.common.{AuthInfo, TokenType}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait TokenVerifier {

  def verify[A](tokenTy: TokenType,
                attrsUnmarshaller: Map[String, String] => A)
               (implicit ec: ExecutionContext)
  : Future[AuthInfo[A]]

}

object TokenVerifier {

  sealed trait TokenInvalidReason

  case object TokenExpired extends TokenInvalidReason

  case object TokenFormatError extends TokenInvalidReason

  case object TokenSignatureError extends TokenInvalidReason

  case object TokenRevoked extends TokenInvalidReason

  case object TokenUnknownIssuer extends TokenInvalidReason

}
