/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-18 01:11
 *
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.Actor

/**
  * @author Marcin Wielgus
  */
trait ShardedActor extends Actor {

  def shardName: String = self.path.parent.parent.name

  def shardedActorId: String = self.path.name

  lazy val shardedActorIdSplitted: Array[String] = {
    //TODO this shit usues regexpr inside
    shardedActorId.split('_')
  }



}
