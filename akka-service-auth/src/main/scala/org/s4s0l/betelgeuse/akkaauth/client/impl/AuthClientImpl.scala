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

package org.s4s0l.betelgeuse.akkaauth.client.impl

import akka.actor.ActorRef
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkaauth.client.{AuthClient, TokenResolver, TokenVerifier}
import org.s4s0l.betelgeuse.akkaauth.common

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class AuthClientImpl[A](
                         verifier: TokenVerifier[A],
                         externalResolver: TokenResolver
                       )
  extends AuthClient[A] {

  override def extract(token: common.SerializedToken)
                      (implicit ec: ExecutionContext,
                       timeout: Timeout,
                       sender: ActorRef = ActorRef.noSender)
  : Future[common.AuthInfo[A]] = {
    verifier.verify(token)
  }

  override def resolveApiToken(accessToken: common.SerializedToken)
                              (implicit ec: ExecutionContext,
                               timeout: Timeout,
                               sender: ActorRef = ActorRef.noSender)
  : Future[common.AuthInfo[A]] = {
    for (
      external <- externalResolver.resolveToken(accessToken);
      extracted <- extract(external)
    ) yield extracted
  }
}
