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

package org.s4s0l.betelgeuse.akkacommons.persistence.roach

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.util.Date

import org.s4s0l.betelgeuse.akkacommons.persistence.roach.Internals.{Ref, _}
import scalikejdbc._

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.reflect.ClassTag


object PostgresScalikeJdbcImports {

  def classTag[T](implicit ctag: ClassTag[T]): ClassTag[T] = ctag


  val typeMap: Map[Class[_], String] = Map(
    classOf[String] -> "string"
  )

  implicit def convertToSql[A: SqlConverter](v: A): Wrapper = {
    implicitly[SqlConverter[A]].toSqlType(v)
  }


  def convertFromSql[A: SqlConverter](v: Option[Ref]): Option[A] = {
    implicitly[SqlConverter[A]].fromSqlType(v.orNull)
  }


  def param[T >: Null : ClassTag](array: T)(implicit conv: SqlConverter[T]): ParameterBinder = (stmt: PreparedStatement, idx: Int) => {
    val toSave = unSqlish(convertToSql[T](array)(conv))
    conv.setInStatement(stmt, idx, toSave)
  }


  implicit val sqlConverterInt: SqlConverter[Int] = new IdentitySqlConverter[Int]() {
    override def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit = {
      stmt.setInt(idx, aValue.orNull.asInstanceOf[Int])
    }
  }

  implicit val sqlConverterString: SqlConverter[String] = new IdentitySqlConverter[String]() {
    override def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit = {
      stmt.setString(idx, aValue.orNull.asInstanceOf[String])
    }
  }

  implicit val sqlConverterDate: SqlConverter[Date] = new SqlConverter[Date] {
    override def toSqlType(a: Date): Wrapper = if (a == null) RoachSqlishValueHolder(None) else RoachSqlishValueHolder(Some(new Timestamp(a.getTime)))

    override def fromSqlType(w: Ref): Option[Date] = w match {
      case null | None => None
      case ts: Timestamp => Some(new Date(ts.getTime))
      case ts: Date => Some(ts)
      case ts: Long => Some(new Date(ts))
      case a => throw new RuntimeException(s"Dunno what to do with ${a.getClass}")
    }

    override def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit = {
      stmt.setTimestamp(idx, aValue.orNull.asInstanceOf[Timestamp])
    }
  }

  implicit val sqlConverterArrayOfString: SqlConverter[Array[String]] = new ArraySqlConverter[String]()
  implicit val sqlConverterListOfString: SqlConverter[List[String]] = new ListSqlConverter[String]()
  implicit val sqlConverterMapOfString: SqlConverter[Map[String, String]] = new MapSqlConverter[String]()

  implicit val typeBinderArrayString: TypeBinder[Array[String]] = new TypeBinder[Array[String]] {
    def apply(rs: ResultSet, label: String): Array[String] = convertFromSql(Option(rs.getArray(label)))(sqlConverterArrayOfString).orNull

    def apply(rs: ResultSet, index: Int): Array[String] = convertFromSql(Option(rs.getArray(index)))(sqlConverterArrayOfString).orNull
  }

  implicit val typeBinderListString: TypeBinder[List[String]] = new TypeBinder[List[String]] {
    def apply(rs: ResultSet, label: String): List[String] = convertFromSql(Option(rs.getArray(label)))(sqlConverterListOfString).orNull

    def apply(rs: ResultSet, index: Int): List[String] = convertFromSql(Option(rs.getArray(index)))(sqlConverterListOfString).orNull
  }

  implicit val typeBinderMapStringString: TypeBinder[Map[String, String]] = new TypeBinder[Map[String, String]] {
    def apply(rs: ResultSet, label: String): Map[String, String] = {
      rs.getObject(label) match {
        case null => null
        case x => x.asInstanceOf[java.util.Map[String, String]].asScala.toMap
      }
    }

    def apply(rs: ResultSet, index: Int): Map[String, String] = rs.getObject(index).asInstanceOf[java.util.Map[String, String]].asScala.toMap
  }
}


