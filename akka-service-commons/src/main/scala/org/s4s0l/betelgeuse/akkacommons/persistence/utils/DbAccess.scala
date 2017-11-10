/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-09-15 03:07
 *
 */

package org.s4s0l.betelgeuse.akkacommons.persistence.utils

import scalikejdbc.{DBSession, NamedDB, SettingsProvider}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Marcin Wielgus
  */
trait DbAccess {

  def scalikeDb(): NamedDB

  def query[A](execution: DBSession => A): A

  def locksSupport(): DbLocksSupport

  def update[A](execution: DBSession => A): A

  def dbDispatcher: ExecutionContext

  def queryAsync[A](execution: DBSession => A, ec: ExecutionContext = dbDispatcher): Future[A]

  def updateAsync[A](execution: DBSession => A, ec: ExecutionContext = dbDispatcher): Future[A]

}
