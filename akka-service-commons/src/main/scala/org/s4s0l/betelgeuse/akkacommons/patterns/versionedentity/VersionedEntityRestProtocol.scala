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
 * Copyright© 2017 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObject
import org.s4s0l.betelgeuse.akkacommons.http.rest.RestDomainObject._
import org.s4s0l.betelgeuse.akkacommons.patterns.versionedentity.VersionedEntityActor.Protocol._
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.JournalReader
import org.s4s0l.betelgeuse.akkacommons.utils.QA

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Protocol adapter from [[VersionedEntityActor]] to [[RestDomainObject]].
  *
  * @author Marcin Wielgus
  */
trait VersionedEntityRestProtocol[T <: AnyRef, V] extends DomainObjectProtocol[String, T, V]
  with Gets[String, T, V]
  with Updates[String, T, V]
  with Creates[String, T, V] {


  // todo it should return version as header
  override def get(msg: Get[String, V])
                  (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
  : Future[RestCommandResult[T]] = {
    versionedEntityActorProtocol.getLatestValue(GetLatestValue(msg.id, msg.messageId)).map {
      case GetLatestValueOk(correlationId, _, value) => RestCommandOk(value.asInstanceOf[T], correlationId)
      case GetLatestValueNotOk(correlationId, _, ex: ValueMissingException) => RestCommandNotOk[T](ex, correlationId, StatusCodes.NotFound)
      case GetLatestValueNotOk(correlationId, _, ex) => RestCommandNotOk[T](ex, correlationId)
    }.recover { case ex: Throwable => RestCommandNotOk[T](ex, msg.messageId) }
  }

  //todo handle optimistic locks, version should be red from headers
  // or from optional T => VersionedId mapping, if any of it is present then
  // it should do setVersionedValue
  override def update(msg: Update[String, T, V])
                     (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
  : Future[RestCommandResult[String]] = {
    versionedEntityActorProtocol.setValue(SetValue(msg.id, msg.value, msg.messageId)).map {
      case SetValueOk(correlationId, _) => RestCommandOk(msg.id, correlationId)
      case SetValueValidationError(correlationId, ex) => RestCommandNotOk[String](ex, correlationId, StatusCodes.BadRequest)
      case SetValueNotOk(correlationId, ex) => RestCommandNotOk[String](ex, correlationId)
    }.recover { case ex: Throwable => RestCommandNotOk[String](ex, msg.messageId) }
  }

  override def list(msg: GetList[V])
                   (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
  : Future[RestCommandResult[List[String]]] = {
    journalRead.allActorsAsync(domainObjectType)(journalRead.dbDispatcher)
      .map(x => RestCommandOk(x.map(_.uniqueId).toList, msg.messageId))
      .recover { case ex: Throwable => RestCommandNotOk(ex, msg.messageId) }
  }

  override def create(msg: Create[String, T, V])
                     (implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout)
  : Future[RestCommandResult[String]] = {
    versionedEntityActorProtocol.setVersionedValue(SetVersionedValue(VersionedId(msg.id, 1), msg.value, msg.messageId)).map {
      case SetValueOk(correlationId, versionedId) => RestCommandOk(versionedId.id, correlationId)
      case SetValueValidationError(correlationId, ex) => RestCommandNotOk[String](ex, correlationId, StatusCodes.BadRequest)
      case SetValueNotOk(correlationId, ex) => RestCommandNotOk[String](ex, correlationId)
    }.recover { case ex: Throwable => RestCommandNotOk[String](ex, msg.messageId) }
  }

  protected def journalRead: JournalReader

  protected def versionedEntityActorProtocol: VersionedEntityActor.Protocol[T]

  override def generateId: String = QA.uuid


}

