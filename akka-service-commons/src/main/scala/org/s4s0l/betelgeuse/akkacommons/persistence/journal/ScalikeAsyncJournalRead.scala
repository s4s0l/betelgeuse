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



package org.s4s0l.betelgeuse.akkacommons.persistence.journal

import scalikejdbc.{DBSession, _}

/**
  * todo totalna fuszerka
  *
  * @author Marcin Wielgus
  */
object ScalikeAsyncJournalRead {


  def getAllAgregates(actorType: String): DBSession => List[PersistenceId] = implicit session => {
    sql"select distinct id from crate_async_write_journal_entity where tag = $actorType"
      .map(_.string(1))
      .list()
      .apply()
      .map(PersistenceId(actorType, _))
  }

}
