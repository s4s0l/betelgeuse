package org.s4s0l.betelgeuse.akkacommons.persistence.journal

/**
  * @author Marcin Wielgus
  */
case class PersistenceId(tag: String, uniqueId: String) {
  override def toString: String = {
    tag match {
      case "SOME" => uniqueId
      case _ => tag + "/" + uniqueId
    }
  }
}

object PersistenceId {
  private val includeSlash: Boolean = false

  def apply(tag: String, uniqueId: String): PersistenceId = new PersistenceId(tag, uniqueId)

  implicit def fromString(persistenceId: String): PersistenceId = {
    val i: Int = persistenceId.lastIndexOf('/')
    if (i < 0) {
      new PersistenceId("SOME", persistenceId)
    } else {
      val tag = if (includeSlash) {
        persistenceId.substring(0, i + 1)
      } else {
        persistenceId.substring(0, i)
      }
      val uniqueId = persistenceId.substring(i + 1, persistenceId.length)
      PersistenceId(tag, uniqueId)
    }
  }
}