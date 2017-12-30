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



package org.s4s0l.betelgeuse.akkacommons.utils

import akka.HackedActor
import akka.actor.{ActorLogging, Stash}

/**
  *
  * TODO: add handling of initiation timeout
  *
  * @author Marcin Wielgus
  */
trait AsyncInitActor extends HackedActor with ActorLogging with Stash {


  private var isInitComplete = false
  //  private var tmpStash = List[(ActorRef, Any)]()

  def initialReceive: Receive

  def initiationComplete(): Unit = {
    if (!isInitComplete) {
      isInitComplete = true
      log.debug("Unstashing ....")
      unstashAll()
      log.debug("unstashed ....")
    }
  }

  def initiating(): Boolean = !isInitComplete

  protected override def hackedAroundReceive(receive: Receive, msg: Any): Unit = {
    if (!isInitComplete) {
      if (initialReceive.isDefinedAt(msg)) {
        initialReceive.apply(msg)
      } else {
        stash()
      }
    } else {
      super.hackedAroundReceive(receive, msg)
    }
  }
}
