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



package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import java.io.PrintWriter
import java.sql.{Connection, SQLException, SQLFeatureNotSupportedException}
import java.util.logging.Logger

import javax.sql.DataSource
import scalikejdbc.ConnectionPool
import scalikejdbc.ConnectionPool.DEFAULT_NAME

/**
  * @author Marcin Wielgus
  */
class DummyDataSource(name: Any = DEFAULT_NAME) extends DataSource {
  override def getConnection: Connection = ConnectionPool.borrow(name)

  override def getConnection(username: String, password: String): Connection = getConnection

  override def unwrap[T](iface: Class[T]): T = throw new SQLException("BasicDataSource is not a wrapper.")

  override def isWrapperFor(iface: Class[_]): Boolean = false

  override def setLoginTimeout(seconds: Int):Unit = throw new SQLFeatureNotSupportedException

  override def setLogWriter(out: PrintWriter):Unit = throw new SQLFeatureNotSupportedException

  override def getParentLogger: Logger = throw new SQLFeatureNotSupportedException

  override def getLoginTimeout: Int = throw new SQLFeatureNotSupportedException

  override def getLogWriter: PrintWriter = throw new SQLFeatureNotSupportedException
}
