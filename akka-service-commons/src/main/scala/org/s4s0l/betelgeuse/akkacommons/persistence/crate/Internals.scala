package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.sql
import java.sql.PreparedStatement
import java.util.Date

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports._

import scala.collection.JavaConverters._
import scala.reflect.{ClassTag, classTag}

/**
  * @author Marcin Wielgus
  */

object Internals {
  type Wrapper = CrateSqlishValueHolder
  type Ref = Any

  case class CrateSqlishValueHolder(value: Option[Ref])

  class ObjectAttributeResolver(val map: Map[String, Ref]) {

    def int(name: String): Option[Int] = convertFromSql[Int](map.get(name))

    def string(name: String): Option[String] = convertFromSql[String](map.get(name))

    def date(name: String): Option[Date] = convertFromSql[Date](map.get(name))

    def get[A: SqlConverter](name: String): Option[A] = convertFromSql[A](map.get(name))
  }

  def unSqlish(x: Ref): Option[Ref] = x match {
    case CrateSqlishValueHolder(None) => None
    case CrateSqlishValueHolder(Some(v: Array[_])) => Some(v.map(it => unSqlish(it)).filter(_.isDefined).map(_.get))
    case CrateSqlishValueHolder(Some(v: List[_])) => Some(v.map(it => unSqlish(it)).filter(_.isDefined).map(_.get).asJava)
    case CrateSqlishValueHolder(Some(v: java.util.List[_])) => Some(v.asScala.map(it => unSqlish(it)).filter(_.isDefined).map(_.get).asJava)
    case CrateSqlishValueHolder(Some(v: Map[_, _])) => Some(v
      .map(it => (it._1, unSqlish(it._2)))
      .filter(_._2.isDefined)
      .map(it => (it._1, it._2.get))
      .asJava
    )
    case CrateSqlishValueHolder(Some(v: java.util.Map[_, _])) => Some(v
      .asScala
      .map(it => (it._1, unSqlish(it._2)))
      .filter(_._2.isDefined)
      .map(it => (it._1, it._2.get))
      .asJava
    )
    case CrateSqlishValueHolder(Some(v)) => unSqlish(v)
    case null => None
    case Some(v) => Some(v)
    case v => Some(v)
  }

  def unSqlish(x: Map[String, CrateSqlishValueHolder]): java.util.Map[String, Ref] = {
    x.map(it => (it._1, unSqlish(it._2)))
      .filter(_._2.isDefined)
      .map(it => (it._1, it._2.get))
      .asJava
  }

  trait SqlConverter[A] {
    def toSqlType(a: A): Wrapper

    def fromSqlType(w: Ref): Option[A]

    def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit

  }

  abstract class IdentitySqlConverter[A]() extends SqlConverter[A] {
    override def toSqlType(a: A): Wrapper = CrateSqlishValueHolder(Option(a))

    override def fromSqlType(w: Ref): Option[A] = Option(w).map(_.asInstanceOf[A])
  }

  class OptionSqlConverter[A: ClassTag](implicit val conv: SqlConverter[A]) extends SqlConverter[Option[A]] {
    override def toSqlType(a: Option[A]): Wrapper = CrateSqlishValueHolder(a)

    override def fromSqlType(w: Ref): Option[Option[A]] = Option(conv.fromSqlType(w))

    override def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit = {
      conv.setInStatement(stmt, idx, aValue)
    }
  }

  class ListSqlConverter[A: ClassTag](implicit val conv: SqlConverter[A]) extends SqlConverter[List[A]] {

    override def toSqlType(a: List[A]): Wrapper = CrateSqlishValueHolder(
      Some(a.map(it => conv.toSqlType(it)))
    )

    override def fromSqlType(w: Ref): Option[List[A]] = w match {
      case null | None => None
      case javaArray: java.util.List[_] => Some(
        javaArray.asInstanceOf[java.util.List[A]].asScala.toList
          .map(it => conv.fromSqlType(it))
          .filter(_.isDefined)
          .map(_.get)
      )
      case sqlArray: sql.Array => handleSqlArray(sqlArray)
      case a => throw new RuntimeException(s"Dunno what to do with ${a.getClass}")
    }

    private def handleSqlArray(sqlArray: sql.Array): Option[List[A]] = {
      if (sqlArray == null || sqlArray.getArray == null) {
        return None
      }
      val v: Array[Ref] = sqlArray.getArray.asInstanceOf[Array[Ref]]
      val notNulls = v.zipWithIndex.map(it => {
        (it._2, conv.fromSqlType(it._1))
      }).filter(_._2.isDefined).map(it => (it._1, it._2.get))

      val oArr = classTag[A].newArray(notNulls.length)
      notNulls.foreach(it => oArr(it._1) = it._2)
      Some(oArr.toList)
    }

    override def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit = {
      aValue match {
        case None => stmt.setArray(idx, null)
        case Some(x: java.util.List[A]) =>
          val typeOf = typeMap.getOrElse(classTag[A].runtimeClass, "object")
          val newArr = new Array[AnyRef](x.size())
          x.asScala.zipWithIndex.foreach { i =>
            newArr(i._2) = i._1.asInstanceOf[AnyRef]
          }
          stmt.setArray(idx, stmt.getConnection.createArrayOf(typeOf, newArr))
        case a => throw new RuntimeException(s"Duno how to handle ${a.getClass} = ${a.toString}")
      }

    }
  }

  class ArraySqlConverter[A: ClassTag](implicit val conv: SqlConverter[A]) extends SqlConverter[Array[A]] {
    override def toSqlType(a: Array[A]): Wrapper = CrateSqlishValueHolder(Option(a.map(it => conv.toSqlType(it))))

    override def fromSqlType(w: Ref): Option[Array[A]] = w match {
      case null => None
      case None => None
      case sqlArray: sql.Array => handleSqlArray(sqlArray)
      case Some(sqlArray: sql.Array) => handleSqlArray(sqlArray)
      case javaArray: java.util.List[_] => Some(javaArray.asInstanceOf[java.util.List[A]].asScala.toArray(classTag[A]))
      case a => throw new RuntimeException(s"Dunno what to do with ${a.getClass}")
    }

    private def handleSqlArray(sqlArray: sql.Array): Option[Array[A]] = {
      if (sqlArray == null || sqlArray.getArray == null) {
        return None
      }
      val v: Array[Ref] = sqlArray.getArray.asInstanceOf[Array[Ref]]
      val notNulls = v.zipWithIndex.map(it => {
        (it._2, conv.fromSqlType(it._1))
      }).filter(_._2.isDefined).map(it => (it._1, it._2.get))

      val oArr = classTag[A].newArray(notNulls.length)
      notNulls.foreach(it => oArr(it._1) = it._2)
      Some(oArr)
    }


    override def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit = {
      aValue match {
        case None => stmt.setArray(idx, null)
        case Some(x: Array[A]) =>
          val typeOf = typeMap.getOrElse(classTag[A].runtimeClass, "object")
          val myArr = x
          val newArr = new Array[AnyRef](myArr.length)
          Array.copy(myArr, 0, newArr, 0, newArr.length)
          stmt.setArray(idx, stmt.getConnection.createArrayOf(typeOf, newArr))
        case a => throw new RuntimeException(s"Duno how to handle ${a.getClass}")
      }

    }
  }

  class MapSqlConverter[A](implicit val conv: SqlConverter[A]) extends SqlConverter[Map[String, A]] {
    override def toSqlType(a: Map[String, A]): Wrapper = a match {
      case null => CrateSqlishValueHolder(None)
      case notNull => CrateSqlishValueHolder(Option(notNull.map(it => (it._1, conv.toSqlType(it._2))).asJava))
    }

    def handleMap(map: Map[String, Ref]): Map[String, A] = map
      .map(it => (it._1, conv.fromSqlType(it._2)))
      .filter(_._2.isDefined)
      .map(it => (it._1, it._2.get))

    override def fromSqlType(w: Ref): Option[Map[String, A]] = w match {
      case null | None => None
      case map: Map[_, _] => Some(handleMap(map.asInstanceOf[Map[String, Ref]]))
      case map: java.util.Map[_, _] => Some(handleMap(map.asInstanceOf[java.util.Map[String, Ref]].asScala.toMap))
      case a => throw new RuntimeException(s"Dunno what to do with ${a.getClass}")
    }

    override def setInStatement(stmt: PreparedStatement, idx: Int, aValue: Option[Ref]): Unit = {
      stmt.setObject(idx, aValue.orNull)
    }
  }


}
