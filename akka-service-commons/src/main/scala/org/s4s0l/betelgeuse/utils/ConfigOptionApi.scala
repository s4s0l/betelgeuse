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

