
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

package org.s4s0l.betelgeuse.utils

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

/**
  * @author Marcin Wielgus
  */
trait ConfigOptionApi {

  def boolean(path: String): Option[Boolean]

  def string(path: String): Option[String]

  def int(path: String): Option[Int]

  def long(path: String): Option[Long]

  def double(path: String): Option[Double]

  def config(path: String): Option[Config]

  def duration(path: String): Option[FiniteDuration]

  def getFiniteDuration(path: String): FiniteDuration

}

object ConfigOptionApi {

  def toConfigOptionApi(typeSafeConfig: Config): ConfigOptionApi = new ConfigOptionApi {

    override def boolean(path: String): Option[Boolean] = if (typeSafeConfig.hasPath(path))
      Some(typeSafeConfig.getBoolean(path)) else None

    override def string(path: String): Option[String] = if (typeSafeConfig.hasPath(path))
      Some(typeSafeConfig.getString(path)) else None

    override def int(path: String): Option[Int] = if (typeSafeConfig.hasPath(path))
      Some(typeSafeConfig.getInt(path)) else None

    override def long(path: String): Option[Long] = if (typeSafeConfig.hasPath(path))
      Some(typeSafeConfig.getLong(path)) else None

    override def double(path: String): Option[Double] = if (typeSafeConfig.hasPath(path))
      Some(typeSafeConfig.getDouble(path)) else None

    override def config(path: String): Option[Config] = if (typeSafeConfig.hasPath(path))
      Some(typeSafeConfig.getConfig(path)) else None

    override def duration(path: String): Option[FiniteDuration] =
      if (typeSafeConfig.hasPath(path)) {
        Try(FiniteDuration(typeSafeConfig.getDuration(path).toNanos, TimeUnit.NANOSECONDS)) match {
          case Success(duration) => Some(duration)
          case Failure(f) => throw new Exception(s"unable to get duration from configuration at path $path", f)
        }
      } else None

    override def getFiniteDuration(path: String): FiniteDuration =
      FiniteDuration(typeSafeConfig.getDuration(path).toNanos, TimeUnit.NANOSECONDS)
  }
}

