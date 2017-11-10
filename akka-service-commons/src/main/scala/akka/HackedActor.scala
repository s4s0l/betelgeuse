/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package akka

import akka.actor.Actor

/**
  * @author Marcin Wielgus
  */
trait HackedActor extends Actor {
  override def aroundReceive(receive: Receive, msg: Any): Unit = super.aroundReceive(receive, msg)
}
