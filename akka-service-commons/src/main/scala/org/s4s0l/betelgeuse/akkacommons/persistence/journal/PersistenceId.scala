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