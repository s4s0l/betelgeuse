/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-13 13:14
 */

package org.s4s0l.betelgeuse.utils

import com.typesafe.config.Config

import scala.language.implicitConversions
import scala.reflect.runtime.universe

/**
  * @author Marcin Wielgus
  */
object AllUtils extends TryNTimes
  with TryWith
  with CompanionFinder
  with PlaceholderUtils
  with FutureUtils {

  implicit def toConfigOptionApi(typesafeConfig: Config): ConfigOptionApi = ConfigOptionApi.toConfigOptionApi(typesafeConfig)
}
