/*
 * Copyright© 2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * Copyright© 2017 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib

import akka.actor.ActorRef
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObject._
import org.s4s0l.betelgeuse.akkacommons.patterns.statedistrib.OriginStatePublishingActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedId
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.JournalReader

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
trait OriginStatePublishingRestProtocol[T <: AnyRef, V] extends BaseProtocol[String, T, V]
  with Actions[String, T, V] {

  def publishingActor: OriginStatePublishingActor.Protocol[T]

  override def actions: Map[ActionDesc, (Action[String, V], ExecutionContext, ActorRef) => Future[RestCommandResult[_]]] =
    super.actions ++ Map(
      Query("publication-status") -> (getPublicationStatus(_: Action[String, V])(_: ExecutionContext, _: ActorRef)),
      Idempotent("publish", Set("versionedId")) -> (publishVersion(_: Action[String, V])(_: ExecutionContext, _: ActorRef))
    )


  def getPublicationStatus(msg: Action[String, V])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[PublicationStatuses]] =
    publishingActor.publishStatus(GetPublicationStatus(msg.id, msg.messageId)).map {
      case GetPublicationStatusOk(value, correlationId) => RestCommandOk(value, correlationId)
      case GetPublicationStatusNotOk(ex, correlationId) => RestCommandNotOk(ex, correlationId)
    }


  def publishVersion(msg: Action[String, V])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[Any]] =
    publishingActor.publish(PublishVersion(VersionedId(msg.params("versionedId")), msg.messageId)).map {
      case PublishVersionOk(correlationId) => RestCommandOk(NoPayload, correlationId)
      case PublishVersionNotOk(ex, correlationId) => RestCommandNotOk(ex, correlationId)
    }

}

object OriginStatePublishingRestProtocol {

  def apply[T <: AnyRef, V](actorProtocol: OriginStatePublishingActor.Protocol[T], settings: BaseProtocolSettings[String, T, V])(implicit journalReaderToUse: JournalReader)
  : OriginStatePublishingRestProtocol[T, V]
  = new OriginStatePublishingRestProtocol[T, V] {

    override def publishingActor: OriginStatePublishingActor.Protocol[T] = actorProtocol

    override val baseProtocolSettings: BaseProtocolSettings[String, T, V] = settings
  }
}
