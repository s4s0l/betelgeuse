/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
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
