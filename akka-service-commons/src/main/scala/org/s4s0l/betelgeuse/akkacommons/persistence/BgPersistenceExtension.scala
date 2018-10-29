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


package org.s4s0l.betelgeuse.akkacommons.persistence

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.{BetelgeuseDb, DbAccess, DbAccessImpl}

/**
  * @author Marcin Wielgus
  */
class BgPersistenceExtension(private val system: ExtendedActorSystem) extends Extension {

  private val betelgeuseDb: BetelgeuseDb = {
    val db = new BetelgeuseDb(system.settings.config)(system.dispatcher, system.scheduler)
    db.setupAll()
    db
  }

  lazy val dbAccess: DbAccess = new DbAccessImpl(betelgeuseDb, defaultPoolName)(system)

  def defaultSchemaName: String = {
    betelgeuseDb.getDefaultSchemaNameFromPoolName(defaultPoolName).get
  }

  def defaultPoolName: String = {
    betelgeuseDb.getDefaultPoolName.get
  }

  private[persistence] def closeDb(): Unit = {
    betelgeuseDb.closeAll()
  }

}


object BgPersistenceExtension extends ExtensionId[BgPersistenceExtension] with ExtensionIdProvider {

  override def apply(system: ActorSystem): BgPersistenceExtension = system.extension(this)

  override def get(system: ActorSystem): BgPersistenceExtension = system.extension(this)

  override def lookup(): BgPersistenceExtension.type = BgPersistenceExtension

  override def createExtension(system: ExtendedActorSystem): BgPersistenceExtension =
    new BgPersistenceExtension(system)

}

