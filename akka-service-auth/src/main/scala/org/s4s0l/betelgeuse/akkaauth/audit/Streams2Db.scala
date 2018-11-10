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

import akka.Done
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props}
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.scaladsl.Consumer
import akka.pattern.{Backoff, BackoffSupervisor, _}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink}
import akka.util.Timeout
import com.typesafe.config.Config
import org.s4s0l.betelgeuse.akkacommons.BgService
import org.s4s0l.betelgeuse.akkacommons.kamon.KamonStreams._
import org.s4s0l.betelgeuse.akkacommons.persistence.utils.DbAccess
import org.s4s0l.betelgeuse.akkacommons.streaming.KafkaConsumer
import org.s4s0l.betelgeuse.utils.AllUtils._
import scalikejdbc.DBSession

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

/**
  * @author Marcin Wielgus
  */
trait Streams2Db {

  def close()
           (implicit sender: ActorRef = ActorRef.noSender,
            ec: ExecutionContext): Future[Done]

  def registerOnShutdown(bgService: BgService)
                        (implicit sender: ActorRef = ActorRef.noSender,
                         ec: ExecutionContext): Unit
}

object Streams2Db {

  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private case class FailingMessage(ex: Throwable)

  private case class StreamStoppedMessage()

  private case class StopMessage(stopInitiator: ActorRef)

  private case class ProducerRestartingException(msg: String, cause: Throwable) extends RuntimeException(msg, cause)

  def apply[K <: AnyRef, V <: AnyRef](kafkaAccess: KafkaConsumer[K, V],
                                      name: String,
                                      producerConfig: Config)
                                     (execution: (DBSession, List[V]) => Unit)
                                     (implicit actorSystem: ActorSystem,
                                      dbAccess: DbAccess)
  : Streams2Db = {
    val config = producerConfig.withFallback(actorSystem.settings.config.getConfig("bg.streaming.stream-2-db"))
    val topic = config.getString("topic")
    val groupSize = config.getInt("group-size")
    val windowSize: FiniteDuration = config.getDuration("window-size")
    val minBackOff: FiniteDuration = config.getDuration("min-backoff")
    val maxBackOff: FiniteDuration = config.getDuration("max-backoff")
    val maxRetries: Int = config.getInt("max-retries")
    val maxDownTimeFinite: FiniteDuration = config.getDuration("max-retries-time")
    val maxDownTime: Duration = if (maxDownTimeFinite == Duration.Zero) Duration.Inf else maxDownTimeFinite
    implicit val askTimeout: Timeout = config.getDuration("ask-timeout"): FiniteDuration
    val sqlJobs: Int = config.getInt("sql-jobs")


    def createStream(implicit ec: ExecutionContext): RunnableGraph[(Consumer.Control, Future[Done])] = {
      kafkaAccess.source(Set(topic))
        .withBasicMetrics(StreamBasicMetrics(s"$name-input"))
        .withDemandLatencyMetrics(StreamDemandLatencyMetrics(s"$name-input"))
        .groupedWithin(groupSize, windowSize)
        .map { group =>
          group.foldLeft((CommittableOffsetBatch.empty, List[V]())) {
            (batch, elem) =>
              (batch._1.updated(elem.committableOffset), elem.record.value() :: batch._2)
          }
        }
        .mapAsync(sqlJobs) {
          case (offsetBatch, records) =>
            if (records.nonEmpty) {
              dbAccess
                .updateAsync(session => execution(session, records))
                .map(_ => offsetBatch)
            }
            Future.successful(offsetBatch)
        }
        .withBasicMetrics(StreamBasicMetrics(s"$name-output"))
        .withDemandLatencyMetrics(StreamDemandLatencyMetrics(s"$name-output"))
        .mapAsync(1) {
          _.commitScaladsl()
        }
        .toMat(Sink.ignore)(Keep.both)
    }

    def actorFactory: Actor = new Actor with ActorLogging {

      private implicit val materializer: ActorMaterializer = ActorMaterializer()(context)
      private implicit val ec: ExecutionContextExecutor = context.dispatcher
      private lazy val control = {
        val (ret, done) = createStream.run()
        done.onComplete {
          case scala.util.Success(_) =>
            log.info(s"Stream 2 Db actor started $topic, named $name")
          case Failure(reason) =>
            self ! FailingMessage(reason)
        }
        ret
      }

      override def preStart(): Unit = {
        control
      }

      override def receive: Receive = {

        case StreamStoppedMessage() =>
          val trapped = sender()
          control.shutdown()
            .map { _ =>
              StopMessage(trapped)
            }
            .pipeTo(self)

        case StopMessage(trappedSender) =>
          trappedSender ! Done
          context.stop(self)

        case FailingMessage(reason) =>
          throw ProducerRestartingException(s"Stream 2 Db for topic $topic, named $name failed!", reason)
      }
    }


    val supervisor =
      Backoff.onFailure(
        childProps = Props(actorFactory),
        childName = s"stream2db-$name",
        minBackoff = minBackOff,
        maxBackoff = maxBackOff,
        randomFactor = 0.2)
        .withSupervisorStrategy(
          OneForOneStrategy(
            maxNrOfRetries = maxRetries,
            withinTimeRange = maxDownTime) {
            case _: ProducerRestartingException => Restart
            case _ => Escalate
          }
        )

    val ref = actorSystem.actorOf(BackoffSupervisor.props(supervisor))
    new Streams2Db {
      override def close()
                        (implicit sender: ActorRef = ActorRef.noSender,
                         ec: ExecutionContext): Future[Done] = {
        (ref ? StreamStoppedMessage()).mapTo[Done]
      }

      override def registerOnShutdown(bgService: BgService)
                                     (implicit sender: ActorRef = ActorRef.noSender,
                                      ec: ExecutionContext): Unit = {
        bgService.shutdownCoordinated("before-service-unbind", s"kafka-client-$name-unbind")(() =>
          close() andThen {
            case Success(Done) =>
              LOGGER.debug(s"Successfully shut down kafka consumer stream 2 db: $name")
            case Failure(ex) =>
              LOGGER.error(s"Failed to close kafka consumer stream 2 db: $name!", ex)
          })
      }
    }
  }

}
