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

import scalikejdbc.DBSession

/**
  * plug for [[BetelgeuseDb]] allowing different
  * database providers to modify some behaviours
  *
  * @author Marcin Wielgus
  */
trait SessionCustomizer {
  /**
    * called on read only query,
    * Function receives database name as input.
    * Shuold return value that will be returned in
    * [[scalikejdbc.SQLSyntaxSupportFeature.SQLSyntaxSupport.schemaName]]
    * for every [[BetelgeuseEntityObject]] used in query
    */
  def onReadOnly(implicit execution: DBSession)
  : Option[String] => String

  /**
    * Shuold return value that will be returned in
    * [[scalikejdbc.SQLSyntaxSupportFeature.SQLSyntaxSupport.schemaName]]
    * for every [[BetelgeuseEntityObject]] used in query
    */
  def onLocalTx(implicit execution: DBSession)
  : Option[String] => String
}
