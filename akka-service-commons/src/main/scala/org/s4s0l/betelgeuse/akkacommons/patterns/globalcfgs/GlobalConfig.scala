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

package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs


import akka.actor.{Actor, ActorRef}
import akka.pattern._
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigActor.{ConfigValue, GetConfig}
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigSupervisorActor.ConfigurationAwaitRestore
import org.s4s0l.betelgeuse.akkacommons.persistence.journal.PersistenceId
import org.s4s0l.betelgeuse.utils.AllUtils
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
class GlobalConfig[T](configName: String, supervisorRef: ActorRef) {
  private val LOGGER = LoggerFactory.getLogger(getClass)

  import AllUtils._

  def apply(key: String)(implicit timeout: Timeout, executionContext: ExecutionContext, sender: ActorRef = Actor.noSender): Future[Option[T]] = {
    (supervisorRef ? GetConfig(PersistenceId(configName, key)))
      .map(_.asInstanceOf[ConfigValue[T]])
      .map(_.value)
      .mapAll {
        case Success(x) => x
        case Failure(f) =>
          LOGGER.error(s"Unable to get config value $key in config $configName", f)
          None

      }
  }

  def awaitInit(): Unit = {

    implicit val timeout: Timeout = Timeout(10 seconds)
    Await.result(supervisorRef ? ConfigurationAwaitRestore, 10 seconds)
  }


}


