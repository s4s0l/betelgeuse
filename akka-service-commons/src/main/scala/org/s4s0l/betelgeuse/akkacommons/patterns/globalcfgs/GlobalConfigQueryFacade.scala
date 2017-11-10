/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-13 13:14
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs

import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId

/**
  * @author Marcin Wielgus
  */
trait GlobalConfigQueryFacade {

  def getGlobalConfigIds(actorType: String): List[PersistenceId]

}
