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

package org.s4s0l.betelgeuse.akkacommons.streaming

import akka.{Done, NotUsed}
import akka.kafka.{ProducerMessage, ProducerSettings}
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Maciej Flak
  */
trait KafkaProducer[K,V]{
  def sink(shared: Boolean=false): Sink[ProducerRecord[K, V], Future[Done]]
  def flow[P](shared: Boolean=false): Flow[ProducerMessage.Message[K, V, P], ProducerMessage.Result[K, V, P], NotUsed]
  def single(topic: String, elems: List[V]): Future[Done]
}


class KafkaProducerImpl[K, V] private[streaming](producerSettings: ProducerSettings[K, V])(implicit ec: ExecutionContext, mat: ActorMaterializer) extends KafkaProducer[K,V]{

  private val shared_producer = producerSettings.createKafkaProducer()

  def sink(shared: Boolean=false): Sink[ProducerRecord[K, V], Future[Done]] = {
    if (shared) {
      Producer.plainSink(producerSettings, shared_producer)
    }
    else {
      Producer.plainSink(producerSettings)
    }
  }

  def flow[P](shared: Boolean=false): Flow[ProducerMessage.Message[K, V, P], ProducerMessage.Result[K, V, P], NotUsed] = {
    if (shared){
      Producer.flow(producerSettings,shared_producer)
    } else {
      Producer.flow(producerSettings)
    }
  }

  def single(topic: String, elems: List[V]): Future[Done] = Source(elems).map(new ProducerRecord[K, V](topic, _))
    .runWith(Producer.plainSink(producerSettings, shared_producer))


}
