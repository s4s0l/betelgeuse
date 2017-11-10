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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.sql
import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.util.Date

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.Internals.{Ref, _}
import org.s4s0l.betelgeuse.utils.AllUtils
import scalikejdbc._

import scala.collection.JavaConverters._
import scala.reflect.ClassTag


object CrateScalikeJdbcImports {

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
    override def toSqlType(a: Date): Wrapper = if (a == null) CrateSqlishValueHolder(None) else CrateSqlishValueHolder(Some(new Timestamp(a.getTime)))

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


  ///TRAITS

  trait CrateDbObject {
    private val mapper = AllUtils.findCompanionForClass[CrateDbObjectMapper[AnyRef]](getClass)

    def crateDbObjectMapper(): CrateDbObjectMapper[AnyRef] = mapper
  }

  trait CrateDbObjectMapper[T >: Null] {
    def ctag: ClassTag[T]

    def toSql(no: T): Map[String, Wrapper]

    def fromSql(resolver: ObjectAttributeResolver): T


    implicit val sqlConverter: SqlConverter[T] = new SqlConverter[T] {
      override def toSqlType(a: T) = CrateSqlishValueHolder(Option(toSql(a)))

      override def fromSqlType(w: Ref) = Option(fromSql(w))

      override def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit = {
        stmt.setObject(idx, aValue.orNull)
      }
    }
    implicit val sqlConverterArray: SqlConverter[Array[T]] = new ArraySqlConverter[T]()(ctag, sqlConverter)
    implicit val sqlConverterList: SqlConverter[List[T]] = new ListSqlConverter[T]()(ctag, sqlConverter)
    implicit val sqlConverterMap: SqlConverter[Map[String, T]] = new MapSqlConverter[T]()(sqlConverter)
    implicit val sqlConverterOption: SqlConverter[Option[T]] = new OptionSqlConverter[T]()(ctag, sqlConverter)

    implicit val typeBinder: TypeBinder[T] = new TypeBinder[T] {
      def apply(rs: ResultSet, label: String): T = convertFromSql(Option(rs.getObject(label)))(sqlConverter).orNull

      def apply(rs: ResultSet, index: Int): T = convertFromSql(Option(rs.getObject(index)))(sqlConverter).orNull
    }

    implicit val typeBinderArray: TypeBinder[Array[T]] = new TypeBinder[Array[T]] {

      def apply(rs: ResultSet, label: String): Array[T] = convertSqlArrayToArray(rs.getArray(label))


      def apply(rs: ResultSet, index: Int): Array[T] = convertSqlArrayToArray(rs.getArray(index))
    }

    implicit val typeBinderList: TypeBinder[List[T]] = new TypeBinder[List[T]] {

      def apply(rs: ResultSet, label: String): List[T] = apply(rs.getArray(label))

      private def apply(sqlArray: sql.Array): List[T] = {
        if (sqlArray == null || sqlArray.getArray == null) {
          return null
        }
        convertSqlArrayToArray(sqlArray).toList
      }

      def apply(rs: ResultSet, index: Int): List[T] = apply(rs.getArray(index))
    }

    implicit val typeBinderMapString: TypeBinder[Map[String, T]] = new TypeBinder[Map[String, T]] {

      def apply(rs: ResultSet, label: String): Map[String, T] = {
        Option(rs.getObject(label))
          .map(x => x
            .asInstanceOf[java.util.Map[String, AnyRef]]
            .asScala
            .toMap
            .map(x => (x._1, fromSql(x._2))))
          .orNull
      }


      def apply(rs: ResultSet, index: Int): Map[String, T] = {
        rs.getObject(index).asInstanceOf[Map[String, AnyRef]]
          .map(x => (x._1, fromSql(x._2)))
      }
    }

    private def fromSql(m: Ref): T = {
      fromSqlMap(m) { x =>
        fromSql(x)
      }
    }

    private def convertSqlArrayToArray(sqlArray: sql.Array): Array[T] = {
      if (sqlArray == null || sqlArray.getArray == null) {
        return null
      }
      val v = sqlArray.getArray.asInstanceOf[Array[AnyRef]]
      val oArr = ctag.newArray(v.length)
      v
        .map(x => fromSql(x))
        .zipWithIndex
        .foreach {
          a => oArr(a._2) = a._1
        }
      oArr
    }

    private def fromSqlMap(value: Ref)(f: ObjectAttributeResolver => T): T = {
      import scala.collection.JavaConverters._
      val opt: Option[Map[String, AnyRef]] = value match {
        case scalaMap: Map[_, _] => Some(scalaMap.asInstanceOf[Map[String, AnyRef]])
        case Some(scalaMap: Map[_, _]) => Some(scalaMap.asInstanceOf[Map[String, AnyRef]])
        case javaMap: java.util.Map[_, _] => Some(javaMap.asScala.toMap.asInstanceOf[Map[String, AnyRef]])
        case Some(javaMap: java.util.Map[_, _]) => Some(javaMap.asScala.toMap.asInstanceOf[Map[String, AnyRef]])
        case None => None
        case null => None
        case _ => throw new RuntimeException()
      }
      opt.map(it => f.apply(new ObjectAttributeResolver(it))).orNull
    }

  }


}

