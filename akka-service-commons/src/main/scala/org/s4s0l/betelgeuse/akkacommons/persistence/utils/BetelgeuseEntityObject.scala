package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import scalikejdbc._

/**
  * @author Marcin Wielgus
  */
trait BetelgeuseEntityObject[T] extends SQLSyntaxSupport[T] {

  def apply(m: ResultName[T])(rs: WrappedResultSet): T

  override def connectionPoolName: Any = BetelgeuseDb.getDefaultPoolName.map(Symbol(_)) getOrElse super.connectionPoolName

  override def schemaName: Option[String] = BetelgeuseDb.getDefaultSchemaName

}
