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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.s4s0l.betelgeuse.akkacommons.persistence.JournalReader
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbAccess
import scalikejdbc.{DBSession, _}

/**
  * @author Marcin Wielgus
  */
class CrateJournalReader(protected val dbAccess: DbAccess) extends JournalReader {

  override def allActors(actorType: String)(implicit dBSession: DBSession): List[PersistenceId] = {
    val table = SQLSyntax.createUnsafely(s"crate_async_write_journal_entity")
    sql"select distinct id from $table where tag = $actorType"
      .map(_.string(1))
      .list()
      .apply()
      .map(PersistenceId(actorType, _))
  }


}
