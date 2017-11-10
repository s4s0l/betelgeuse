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