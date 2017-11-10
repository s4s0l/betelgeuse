package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */
case class DbLocksSettings(
                                 maxDuration: Duration = 60 seconds,
                                 lockAttemptCount: Int = 15,
                                 lockAttemptInterval: Duration = 2 second
                               )
