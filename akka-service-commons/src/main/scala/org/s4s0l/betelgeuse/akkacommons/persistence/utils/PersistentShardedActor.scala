/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 21:19
 *
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId
import org.s4s0l.betelgeuse.akkacommons.utils.ShardedActor


/**
  * @author Marcin Wielgus
  */
trait PersistentShardedActor extends akka.persistence.PersistentActor with ShardedActor {

  override def persistenceId: String = {
    getPersistenceId.toString
  }

  def getPersistenceId: PersistenceId = {
    PersistenceId(shardName, shardedActorId)
  }
}
