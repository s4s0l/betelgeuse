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

package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import org.flywaydb.core.internal.database.postgresql.PostgreSQLSqlStatementBuilder
import org.flywaydb.core.internal.database.{Delimiter, ExecutableSqlScript, SqlStatementBuilder}
import org.flywaydb.core.internal.util.PlaceholderReplacer
import org.flywaydb.core.internal.util.scanner.{LoadableResource, Resource}

/**
  * @author Marcin Wielgus
  */
class CrateSqlScript(resource: LoadableResource, placeholderReplacer: PlaceholderReplacer, mixed: Boolean)
  extends ExecutableSqlScript(resource, placeholderReplacer, mixed) {

  override protected def createSqlStatementBuilder: SqlStatementBuilder = {
    new PostgreSQLSqlStatementBuilder(Delimiter.SEMICOLON)
  }
}