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

package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import scalikejdbc.DBSession
import scalikejdbc.streams.StreamReadySQL

import scala.concurrent.Future

/**
  * @author Marcin Wielgus
  */
class DbAccessImpl(db: BetelgeuseDb, poolName: String)
                  (implicit actorSystem: ActorSystem) extends DbAccess {

  lazy val ec = PersistenceBlockingDispatcher(actorSystem.dispatchers.lookup("db-dispatcher"))

  override def query[A](execution: DBSession => A): A = {
    db.readOnly(execution, name = poolName)
  }

  override def locksSupport(): DbLocks =
    db.getLocks(name = poolName)

  override def update[A](execution: DBSession => A): A =
    db.localTx(execution, name = poolName)

  override def dbDispatcher: PersistenceBlockingDispatcher =
    ec

  override def queryAsync[A](execution: DBSession => A)
                            (implicit ec: PersistenceBlockingDispatcher = dbDispatcher)
  : Future[A] =
    Future {
      query(execution)
    }(ec.ec)

  override def updateAsync[A](execution: DBSession => A)
                             (implicit ec: PersistenceBlockingDispatcher = dbDispatcher)
  : Future[A] =
    Future {
      update(execution)
    }(ec.ec)

  override def stream[A](execution: StreamReadySQL[A])
                        (implicit ec: PersistenceBlockingDispatcher = dbDispatcher)
  : Source[A, NotUsed] =
    Source.fromPublisher(db.streamRead(execution, ec.ec))

}
