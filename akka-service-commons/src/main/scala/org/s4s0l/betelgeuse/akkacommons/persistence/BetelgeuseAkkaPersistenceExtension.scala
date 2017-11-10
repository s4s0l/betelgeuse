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



package org.s4s0l.betelgeuse.akkacommons.persistence

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.dispatch.MessageDispatcher
import org.s4s0l.betelgeuse.akkacommons.BetelgeuseAkkaServiceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.{BetelgeuseDb, DbAccess, DbLocksSupport}
import scalikejdbc.{DBSession, NamedDB, SettingsProvider}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class BetelgeuseAkkaPersistenceExtension(private val system: ExtendedActorSystem) extends Extension {

  private val serviceName = BetelgeuseAkkaServiceExtension.get(system).serviceInfo.serviceName

  private val betelgeuseDb: BetelgeuseDb = {
    val db = new BetelgeuseDb(system.settings.config)
    db.setupAll()
    db
  }

  def defaultPoolName: String = serviceName

  def defaultSchemaName: String = BetelgeuseDb.getDefaultSchemaName.get

  def scalikeDb(name: String = defaultPoolName, settingsProvider: SettingsProvider = SettingsProvider.default): NamedDB = {
    betelgeuseDb.underlyingPureScalikeJdbcDb(name, settingsProvider)
  }

  def query[A](execution: DBSession => A): A = {
    betelgeuseDb.readOnly(execution)
  }

  def locksSupport(name: String = defaultPoolName): DbLocksSupport = betelgeuseDb.getLocks(name)

  def update[A](execution: DBSession => A): A = {
    betelgeuseDb.localTx(execution)
  }


  val dbAccess: DbAccess = new DbAccess {
    override def query[A](execution: (DBSession) => A): A = BetelgeuseAkkaPersistenceExtension.this.query(execution)

    override def locksSupport(): DbLocksSupport = BetelgeuseAkkaPersistenceExtension.this.locksSupport()

    override def update[A](execution: (DBSession) => A): A = BetelgeuseAkkaPersistenceExtension.this.update(execution)

    override def scalikeDb(): NamedDB = BetelgeuseAkkaPersistenceExtension.this.scalikeDb()

    lazy val ec: MessageDispatcher = system.dispatchers.lookup("db-dispatcher")

    override def dbDispatcher: ExecutionContext = ec

    override def queryAsync[A](execution: (DBSession) => A, ec: ExecutionContext): Future[A] = {
      Future {
        query(execution)
      }(ec)
    }

    override def updateAsync[A](execution: (DBSession) => A, ec: ExecutionContext): Future[A] = {
      Future {
        update(execution)
      }(ec)
    }
  }

  private[persistence] def closeDb(): Unit = {
    betelgeuseDb.closeAll()
  }

}


object BetelgeuseAkkaPersistenceExtension extends ExtensionId[BetelgeuseAkkaPersistenceExtension] with ExtensionIdProvider {

  override def apply(system: ActorSystem): BetelgeuseAkkaPersistenceExtension = system.extension(this)

  override def get(system: ActorSystem): BetelgeuseAkkaPersistenceExtension = system.extension(this)

  override def lookup(): BetelgeuseAkkaPersistenceExtension.type = BetelgeuseAkkaPersistenceExtension

  override def createExtension(system: ExtendedActorSystem): BetelgeuseAkkaPersistenceExtension =
    new BetelgeuseAkkaPersistenceExtension(system)

}

