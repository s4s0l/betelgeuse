/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 12:28
 */

package org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs


import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigSupervisorActor.ConfigurationAwaitRestore
import akka.pattern._
import org.s4s0l.betelgeuse.akkacommons.patterns.globalcfgs.GlobalConfigActor.{ConfigValue, GetConfig}
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


