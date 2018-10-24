/*
 * Copyright© 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.s4s0l.betelgeuse.utils

import java.time.{Duration => ConfigDuration}
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
  * @author Marcin Wielgus
  */
trait ConfigUtils {
  implicit def toFiniteDuration(configDuration: ConfigDuration): FiniteDuration = {
    Try(FiniteDuration(configDuration.toNanos, TimeUnit.NANOSECONDS)) match {
      case Success(duration) => duration
      case Failure(f) => throw new Exception(s"unable to get duration from configuration $configDuration", f)
    }
  }
}
