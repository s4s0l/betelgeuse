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
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.{BetelgeuseDb, DbAccess, DbLocksSupport}
import scalikejdbc.DBSession

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
class BgPersistenceExtension(private val system: ExtendedActorSystem) extends Extension {

  val dbAccess: DbAccess = new DbAccess {
    override def query[A](execution: (DBSession) => A): A = BgPersistenceExtension.this.query(execution)

    override def locksSupport(): DbLocksSupport = BgPersistenceExtension.this.locksSupport()

    override def update[A](execution: (DBSession) => A): A = BgPersistenceExtension.this.update(execution)

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

  private val betelgeuseDb: BetelgeuseDb = {
    val db = new BetelgeuseDb(system.settings.config)
    db.setupAll()
    db
  }

  def defaultSchemaName: String = betelgeuseDb.getDefaultSchemaNameFromPoolName(defaultPoolName).get

  def defaultPoolName: String = betelgeuseDb.getDefaultPoolName.get

  private def query[A](execution: DBSession => A, name: String = defaultPoolName): A = {
    betelgeuseDb.readOnly(execution, name = name)
  }

  private def locksSupport(name: String = defaultPoolName): DbLocksSupport = betelgeuseDb.getLocks(name)

  private def update[A](execution: DBSession => A, name: String = defaultPoolName): A = {
    betelgeuseDb.localTx(execution, name = name)
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

