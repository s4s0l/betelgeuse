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

package org.s4s0l.betelgeuse.akkacommons.patterns.sd

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObject.{RestProtocolContext, _}
import org.s4s0l.betelgeuse.akkacommons.patterns.sd.OriginStateActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol.ValueMissingException

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
trait OriginStateRestProtocol[T <: AnyRef, V] extends Actions[String, T, V] {

  def originStateActorProtocol: OriginStateActor.Protocol[T]

  override def actions: Map[ActionDesc, (Action[String, V], RestProtocolContext) => Future[RestCommandResult[_]]] =
    super.actions ++ Map(
      Query("publication-status") -> ((a: Action[String, V], c: RestProtocolContext) => getPublicationStatus(a)(c.executionContext, c.sender))
    )


  def getPublicationStatus(msg: Action[String, V])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[PublicationStatuses]] =
    originStateActorProtocol.publishStatus(GetPublicationStatus(msg.id, msg.messageId)).map {
      case GetPublicationStatusOk(value, correlationId) => RestCommandOk(value, correlationId)
      case GetPublicationStatusNotOk(ex: ValueMissingException, correlationId) => RestCommandNotOk(ex, correlationId, StatusCodes.NotFound)
      case GetPublicationStatusNotOk(ex, correlationId) => RestCommandNotOk(ex, correlationId)
    }

}


