/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 03:07
 *
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.journal

import scalikejdbc.DBSession
import scalikejdbc._

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
