/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.utils

import akka.actor.SupervisorStrategy._
import akka.actor.{OneForOneStrategy, SupervisorStrategy, SupervisorStrategyConfigurator}

/**
  * @author Marcin Wielgus
  */
class EscalatingSupervisorStrategy extends SupervisorStrategyConfigurator {

  override def create(): SupervisorStrategy = OneForOneStrategy()({
    case _: Exception => Escalate
  })
}
