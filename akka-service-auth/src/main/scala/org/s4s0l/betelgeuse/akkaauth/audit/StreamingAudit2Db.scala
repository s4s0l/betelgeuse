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

package org.s4s0l.betelgeuse.akkaauth.audit

import akka.actor.ActorSystem
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbAccess
import org.s4s0l.betelgeuse.akkacommons.serialization.JacksonJsonSerializer
import org.s4s0l.betelgeuse.akkacommons.streaming.KafkaConsumer
import scalikejdbc.DBSession

import scala.concurrent.ExecutionContext

/**
  * @author Marcin Wielgus
  */
object StreamingAudit2Db {

  private[audit] def dbCall(implicit jacksonJsonSerializer: JacksonJsonSerializer)
  : (DBSession, List[StreamingAuditDto]) => Unit = { (session, records) =>
    import scalikejdbc._
    val valuesSql = records.tail.foldLeft(sqls" (?, ?) ") {
      case (sqlSoFar, _) => sqlSoFar.append(sqls", (?, ?)")
    }
    val values = records.flatMap(evt => Seq(evt.id, jacksonJsonSerializer.simpleToString(evt)))
    sql"insert into auth_audit_log (uuid, event) values $valuesSql ON CONFLICT (uuid)  DO NOTHING"
      .tags("reporter.batch")
      .bind(values: _*)
      .update().
      apply()(session)
  }

  def apply(kafkaAccess: KafkaConsumer[String, StreamingAuditDto])
           (implicit actorSystem: ActorSystem,
            jacksonJsonSerializer: JacksonJsonSerializer,
            executionContext: ExecutionContext,
            dbAccess: DbAccess): Streams2Db = {
    val config = actorSystem.settings.config.getConfig("bg.auth.streaming-2-db")
      .withFallback(actorSystem.settings.config.getConfig("bg.auth.streaming"))

    Streams2Db(kafkaAccess, "auth-audit-2-db-collector", config)(dbCall)
  }
}
