/*
 * Copyright© 2017 the original author or authors.
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

import scalikejdbc._

/**
  * Can be used only with [[BetelgeuseDb.readOnly]] and [[BetelgeuseDb.localTx]]
  * or queries involving this entity need to be wrapped with [[BetelgeuseEntityObject.runWithSchemaAndPool]]
  *
  * @author Marcin Wielgus
  */
trait BetelgeuseEntityObject[T] extends SQLSyntaxSupport[T] {

  def apply(m: ResultName[T])(rs: WrappedResultSet): T

  override def connectionPoolName: Any = Symbol(BetelgeuseEntityObject.threadPoolName)

  override def schemaName: Option[String] = Some(BetelgeuseEntityObject.threadSchemaName)

}

object BetelgeuseEntityObject {
  /**
    * thread local holder for current scalike pool name and schema, set by
    * [[BetelgeuseDb.readOnly]] and [[BetelgeuseDb.localTx]] via [[BetelgeuseEntityObject.runWithSchemaAndPool]]
    * used by [[BetelgeuseEntityObject]] as
    *
    */
  private[utils] val threadLocalPoolNameAndSchema: ThreadLocal[(String, String)] = new ThreadLocal[(String, String)]

  /**
    * Used to wrap context for [[BetelgeuseEntityObject]], queries involving it will only work
    * when wrapped by this decorator
    */
  def runWithSchemaAndPool[T](poolName: String, schemaName: String)(body: => T): T = {
    try {
      threadLocalPoolNameAndSchema.set((poolName, schemaName))
      body
    } finally {
      threadLocalPoolNameAndSchema.remove()
    }
  }

  private[utils] def threadPoolName: String = threadLocalPoolNameAndSchema.get()._1

  private[utils] def threadSchemaName: String = threadLocalPoolNameAndSchema.get()._2

}