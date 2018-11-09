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

import akka.kafka.ConsumerMessage
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import org.s4s0l.betelgeuse.akkacommons.kamon.KamonStreams._
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistence
import org.s4s0l.betelgeuse.akkacommons.streaming.BgStreaming
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
class BgAuthAuditCollector
  extends BgStreaming {
  this: BgPersistence =>

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(classOf[BgAuthStreamingAudit[_]])

  override protected def onInitialized(): Unit = {
    super.onInitialized()
    val bgAuthStreamingAuditTopic: String = config.getString("bg.auth.streaming.topic")
    val access = createDefaultKafkaAccess[String, StreamingAuditDto]()
    closeOnShutdown(access)
    val value: Source[ConsumerMessage.CommittableMessage[String, StreamingAuditDto], Consumer.Control] =
      access.consumer.source(Set(bgAuthStreamingAuditTopic))
    val (control, done) = value.withBasicMetrics(StreamBasicMetrics("auth-audit-input"))
      .withDemandLatencyMetrics(StreamDemandLatencyMetrics("auth-audit-input"))
      .groupedWithin(
        config.getInt("bg.auth.streaming.collector.group-size"),
        config.getDuration("bg.auth.streaming.collector.window")
      )
      .map { group =>
        group.foldLeft((CommittableOffsetBatch.empty, List[StreamingAuditDto]())) {
          (batch, elem) =>
            (batch._1.updated(elem.committableOffset), elem.record.value() :: batch._2)
        }
      }
      .via(bgAuthStreamingInsertFlow())
      .withBasicMetrics(StreamBasicMetrics("auth-audit-output"))
      .withDemandLatencyMetrics(StreamDemandLatencyMetrics("auth-audit-output"))
      .mapAsync(1) {
        _.commitScaladsl()
      }
      .toMat(Sink.ignore)(Keep.both).run()
    shutdownCoordinated("before-service-unbind", "kafka-client-unbind")(() =>
      control.shutdown() andThen {
        case Success(Done) =>
          LOGGER.debug("Successfully shut down kafka consumer")
        case Failure(ex) =>
          LOGGER.error("Failed to close kafka consumer!", ex)
      })

    done onComplete {
      case Success(Done) =>
        LOGGER.debug("Kafka consumer stream finished successfully")
      case Failure(ex) =>
        LOGGER.error("Kafka consumer stream finished with error", ex)
    }
  }

  def bgAuthStreamingInsertFlow()
  : Flow[(CommittableOffsetBatch, List[StreamingAuditDto]), CommittableOffsetBatch, NotUsed] =
    Flow[(CommittableOffsetBatch, List[StreamingAuditDto])]
      .mapAsync(config.getInt("bg.auth.streaming.collector.sql-jobs")) {
        case (offsetBatch, records) =>
          if (records.nonEmpty) {
            dbAccess.updateAsync { implicit session =>
              import scalikejdbc._
              val valuesSql = records.tail.foldLeft(sqls" (?, ?) ") {
                case (sqlSoFar, _) => sqlSoFar.append(sqls", (?, ?)")
              }
              val values = records.flatMap(evt => Seq(evt.id, serializationJackson.simpleToString(evt)))
              sql"insert into auth_audit_log (uuid, event) values $valuesSql ON CONFLICT (uuid)  DO NOTHING"
                .tags("reporter.batch")
                .bind(values: _*)
                .update().
                apply()

            }.map(_ => offsetBatch)
          }
          Future.successful(offsetBatch)
      }

}
