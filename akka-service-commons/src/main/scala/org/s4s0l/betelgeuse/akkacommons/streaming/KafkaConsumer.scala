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

import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.Source


trait KafkaConsumer[K, V] {
  def source(topics: Set[String]): Source[ConsumerMessage.CommittableMessage[K, V], Consumer.Control]
}


/**
  * @author Maciej Flak
  */
class KafkaConsumerImpl[K, V] private[streaming](consumerSettings: ConsumerSettings[K, V]) extends KafkaConsumer[K,V]{

  /**
    * simple wrapper around [[akka.kafka.scaladsl.Consumer#committableSource(akka.kafka.ConsumerSettings, akka.kafka.Subscription)]]
    *
    * should you wish for
    * at-least once delivery - pass the msg down the stream and commit when processed
    * at-most  once delivery - commit asap
    *
    * @param topics topics for subscribing to
    * @return
    */
  def source(topics: Set[String]): Source[ConsumerMessage.CommittableMessage[K, V], Consumer.Control] = {
    require(topics.nonEmpty)
    Consumer.committableSource(consumerSettings, Subscriptions.topics(topics))
  }


}


