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

/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkaauth.client

import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.common.SerializedToken

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait TokenResolver {
  /**
    * Verifies and translates token to known format.
    * User by AuthClient service to check received token if it was
    * invalidated in provider (even when token is not yet expired and
    * passed local validation of signature). When Auth Client receives a token that
    * is not issued by provider it can be used to validate and translate token to
    * provider format.
    *
    */
  def resolveToken(token: SerializedToken)
                  (implicit ec: ExecutionContext,
                   timeout: Timeout,
                   sender: ActorRef = ActorRef.noSender)
  : Future[SerializedToken]

}
