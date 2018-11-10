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
import akka.kafka.ProducerMessage
import akka.pattern.{Backoff, BackoffSupervisor, ask}
import akka.persistence.AtLeastOnceDelivery
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import com.typesafe.config.Config
import org.apache.kafka.clients.producer.ProducerRecord
import org.s4s0l.betelgeuse.akkacommons.streaming.KafkaProducer
import org.s4s0l.betelgeuse.utils.AllUtils._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Failure

/**
  * @author Marcin Wielgus
  */
trait Events2Streams[V] {

  def publish(v: V)
             (implicit sender: ActorRef = ActorRef.noSender): Unit

  def publishAsync(v: V)
                  (implicit sender: ActorRef = ActorRef.noSender)
  : Future[Done]

  /**
    * confirmation will arrive as [[Events2Streams.GlobalProducerConfirmation]]
    */
  def atLeastOnce(alod: AtLeastOnceDelivery, v: V, context: AnyRef): Unit
}

object Events2Streams {

  case class GlobalProducerConfirmation(deliveryId: Long, context: AnyRef)


  private case class InternalMessageWrapper[V](message: V, confirmation: Option[DeliveryConfirmation])

  private case class BoundaryMessageWrapper[V](message: V, respondWith: Option[Any])

  private case class DeliveryConfirmation(to: ActorRef, message: Any)

  private case class StoppingMsg(ex: Throwable)

  private case class ProducerRestartingException(msg: String, cause: Throwable) extends RuntimeException(msg, cause)

  def apply[K <: AnyRef, V <: AnyRef](kafkaAccess: KafkaProducer[K, V],
                                      name: String,
                                      producerConfig: Config,
                                      keyExtractor: V => Option[K] = (_: V) => None,
                                      partitionChooser: V => Option[Int] = (_: V) => None)
                                     (implicit actorSystem: ActorSystem)
  : Events2Streams[V] = {

    val config = producerConfig.withFallback(actorSystem.settings.config.getConfig("bg.streaming.events-2-streams"))
    val topic = config.getString("topic")
    val bufferSize = config.getInt("buffer-size")
    val minBackOff: FiniteDuration = config.getDuration("min-backoff")
    val maxBackOff: FiniteDuration = config.getDuration("max-backoff")
    val maxRetries: Int = config.getInt("max-retries")
    val maxDownTimeFinite: FiniteDuration = config.getDuration("max-retries-time")
    val maxDownTime: Duration = if (maxDownTimeFinite == Duration.Zero) Duration.Inf else maxDownTimeFinite
    implicit val askTimeout: Timeout = config.getDuration("ask-timeout"): FiniteDuration

    def createStream(implicit sender: ActorRef): RunnableGraph[(ActorRef, Future[Done])] = {

      Source.actorRef[InternalMessageWrapper[V]](bufferSize, OverflowStrategy.fail)
        .map { dto =>
          val partition: Integer = partitionChooser(dto.message)
            .map(Integer.valueOf)
            .orNull
          val record = keyExtractor(dto.message) match {
            case Some(key) =>
              new ProducerRecord[K, V](
                topic,
                partition,
                key.asInstanceOf[K],
                dto.message)
            case None =>
              new ProducerRecord[Null, V](
                topic,
                partition,
                null,
                dto.message).asInstanceOf[ProducerRecord[K, V]]
          }
          ProducerMessage.Message(record, dto.confirmation)
        }
        .via(kafkaAccess.flow(true))
        .map { msg =>
          msg.message.passThrough match {
            case Some(DeliveryConfirmation(to, what)) =>
              to ! what
            case None =>
          }
          Done
        }
        .toMat(Sink.ignore)(Keep.both)
    }

    def globalProducerActorFactory: Actor = new Actor with ActorLogging {

      private implicit val materializer: ActorMaterializer = ActorMaterializer()(context)
      private implicit val ec: ExecutionContextExecutor = context.dispatcher
      private lazy val streamSourceRef = {
        val (ret, done) = createStream.run()
        done.onComplete {
          case scala.util.Success(_) =>
            log.info(s"Event 2 Stream actor started $topic, named $name")
          case Failure(reason) =>
            self ! StoppingMsg(reason)
        }
        ret
      }

      override def preStart(): Unit = {
        streamSourceRef
      }

      override def receive: Receive = {
        case incoming: BoundaryMessageWrapper[_] =>
          streamSourceRef ! InternalMessageWrapper(
            incoming.message,
            incoming.respondWith.map(DeliveryConfirmation(sender(), _)))
        case StoppingMsg(reason) =>
          throw ProducerRestartingException(s"Event 2 Stream for topic $topic, named $name failed!", reason)
      }
    }

    val supervisor =
      Backoff.onFailure(
        childProps = Props(globalProducerActorFactory),
        childName = s"event-2-stream-$name",
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

    new Events2Streams[V] {
      override def publish(v: V)
                          (implicit sender: ActorRef = ActorRef.noSender)
      : Unit = {
        ref ! BoundaryMessageWrapper(v, None)
      }

      override def publishAsync(v: V)
                               (implicit sender: ActorRef = ActorRef.noSender)
      : Future[Done] = {
        (ref ? BoundaryMessageWrapper(v, Some(Done)))
          .mapTo[Done]
      }

      override def atLeastOnce(alod: AtLeastOnceDelivery, v: V, context: AnyRef)
      : Unit = {
        alod.deliver(ref.path)(deliveryId =>
          BoundaryMessageWrapper(
            message = v,
            respondWith = Some(
              GlobalProducerConfirmation(
                deliveryId,
                context)
            )
          )
        )
      }
    }
  }

}
