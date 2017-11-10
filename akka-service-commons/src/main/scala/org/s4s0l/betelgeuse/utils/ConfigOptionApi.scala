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

import com.typesafe.config.Config

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

}

object ConfigOptionApi {
  def toConfigOptionApi(typesafeConfig: Config): ConfigOptionApi = new ConfigOptionApi {

    override def boolean(path: String): Option[Boolean] = if (typesafeConfig.hasPath(path))
      Some(typesafeConfig.getBoolean(path)) else None

    override def string(path: String): Option[String] = if (typesafeConfig.hasPath(path))
      Some(typesafeConfig.getString(path)) else None

    override def int(path: String): Option[Int] = if (typesafeConfig.hasPath(path))
      Some(typesafeConfig.getInt(path)) else None

    override def long(path: String): Option[Long] = if (typesafeConfig.hasPath(path))
      Some(typesafeConfig.getLong(path)) else None

    override def double(path: String): Option[Double] = if (typesafeConfig.hasPath(path))
      Some(typesafeConfig.getDouble(path)) else None

    override def config(path: String): Option[Config] = if (typesafeConfig.hasPath(path))
      Some(typesafeConfig.getConfig(path)) else None
  }
}

