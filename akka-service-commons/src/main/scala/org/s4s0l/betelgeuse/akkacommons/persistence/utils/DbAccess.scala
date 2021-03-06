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
import akka.stream.scaladsl.Source
import scalikejdbc.DBSession
import scalikejdbc.streams.StreamReadySQL

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait DbAccess {

  def query[A](execution: DBSession => A): A

  def locksSupport(): DbLocks

  def update[A](execution: DBSession => A): A

  def stream[A](execution: StreamReadySQL[A])
               (implicit ec: PersistenceBlockingDispatcher = dbDispatcher)
  : Source[A, NotUsed]

  def dbDispatcher: PersistenceBlockingDispatcher

  def queryAsync[A](execution: DBSession => A)
                   (implicit ec: PersistenceBlockingDispatcher = dbDispatcher)
  : Future[A]

  def updateAsync[A](execution: DBSession => A)
                    (implicit ec: PersistenceBlockingDispatcher = dbDispatcher)
  : Future[A]


}
