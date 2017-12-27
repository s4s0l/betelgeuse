/*
 * Copyright© 2017 the original author or authors.
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

package org.s4s0l.betelgeuse.akkacommons.persistence.journal

import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbAccess
import scalikejdbc.DBSession

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait JournalReader {

  def dbDispatcher: ExecutionContext = dbAccess.dbDispatcher

  def allActorsAsync(actorType: String)(implicit executionContext: ExecutionContext = dbDispatcher): Future[Seq[PersistenceId]] = {
    dbAccess.queryAsync { implicit session => allActors(actorType) }
  }

  def allActors(actorType: String)(implicit dBSession: DBSession): List[PersistenceId]

  protected def dbAccess: DbAccess
}
