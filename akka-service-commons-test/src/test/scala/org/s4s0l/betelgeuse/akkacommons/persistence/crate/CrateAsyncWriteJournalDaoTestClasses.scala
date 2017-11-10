package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports._
import org.s4s0l.betelgeuse.akkacommons.persistence.crate.Internals.Wrapper
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializable

import scala.reflect.ClassTag

/**
  * @author Marcin Wielgus
  */


case class RegularEvent(s: String, i: Int, seq: Seq[String])

case class JsonEvent(s: String, i: Int, seq: Seq[String]) extends JacksonJsonSerializable

case class CrateEvent(s: String, i: Int, seq: Seq[String]) extends CrateDbObject

object CrateEvent extends CrateDbObjectMapper[CrateEvent] {
  override def ctag: ClassTag[CrateEvent] = classTag[CrateEvent]

  override def toSql(no: CrateEvent): Map[String, Wrapper] = {
    Map[String, Wrapper](
      "s" -> no.s,
      "i" -> no.i,
      "seq" -> no.seq.toList,
    )
  }

  override def fromSql(resolver: Internals.ObjectAttributeResolver): CrateEvent = {
    new CrateEvent(
      resolver.string("s").get,
      resolver.int("i").get,
      resolver.get[List[String]]("seq").get,
    )
  }

}