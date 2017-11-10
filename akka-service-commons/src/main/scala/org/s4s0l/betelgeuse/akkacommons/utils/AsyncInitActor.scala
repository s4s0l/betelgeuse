/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-18 01:11
 *
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import akka.HackedActor
import akka.actor.{ActorContext, ActorLogging, ActorRef, Stash, StashSupport}

/**
  * @author Marcin Wielgus
  */
trait AsyncInitActor extends HackedActor with ActorLogging {


  private var isInitComplete = false
  private var tmpStash = List[(ActorRef, Any)]()

  def initialReceive: Receive

  def initiationComplete(): Unit = {
    if (!isInitComplete) {
      isInitComplete = true
      log.debug("Unstashing ....")
      tmpStash.reverse.foreach { it =>
        self.tell(it._2, it._1)
      }
      tmpStash = List()
      log.debug("unstashed ....")
    }
  }

  def initiating(): Boolean = !isInitComplete

  override def aroundReceive(receive: Receive, msg: Any): Unit = {
    if (!isInitComplete) {
      if (initialReceive.isDefinedAt(msg)) {
        initialReceive.apply(msg)
      } else {
        tmpStash = (sender(), msg) :: tmpStash
      }
    } else {
      super.aroundReceive(receive, msg)
    }
  }
}
