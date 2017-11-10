/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 03:07
 *
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs

import org.s4s0l.betelgeuse.akkacommons.persistence.journal.{PersistenceId, ScalikeAsyncJournalRead}
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbAccess

/**
  * @author Marcin Wielgus
  */
class GlobalConfigCrateQueryFacade(dbAccess: DbAccess) extends GlobalConfigQueryFacade {


  override def getGlobalConfigIds(actorType: String): List[PersistenceId] = {
    dbAccess.query(ScalikeAsyncJournalRead.getAllAgregates(actorType))
  }
}
