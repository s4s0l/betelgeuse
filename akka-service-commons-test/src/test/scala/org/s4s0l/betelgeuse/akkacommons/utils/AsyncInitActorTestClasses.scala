/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-14 21:19
 *
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import akka.persistence.PersistentActor

/**
  * @author Marcin Wielgus
  */
object AsyncInitActorTestClasses {

  class SampleAsyncInitActor extends PersistentActor with AsyncInitActor {

    var queue: List[String] = List[String]()


    override def preStart(): Unit = {
      queue = "preStart" :: queue
    }

    override def initialReceive: Receive = {
      case "init" =>
        queue = "initialReceive" :: queue
        initiationComplete()
        sender() ! queue.reverse
    }

    override def receiveRecover: Receive = {
      case _ =>
        queue = "receiveRecover" :: queue
    }

    override def receiveCommand: Receive = {
      case _ =>
        queue = "receiveCommand" :: queue
        persist("received") { _ =>
          sender() ! queue.reverse
        }
    }

    override def persistenceId: String = "1"
  }

}