package org.s4s0l.betelgeuse.akkacommons.streaming

import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerMessage, ProducerMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.s4s0l.betelgeuse.akkacommons.serialization.{JacksonJsonSerializable, JacksonJsonSerializer, SimpleSerializer}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.ScalaFutures
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class BgStreamingTest extends BgTestService with ScalaFutures with GivenWhenThen {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  private val aService = testWith(new BgStreaming {}
  )
  aService.to = 5 seconds

  aService.timeout = 5 seconds


  def getUniqueTopic = s"topic${System.currentTimeMillis()}"

  feature("BgStreaming service provides access to streaming") {
    scenario("someone forgets to pass type parameters") {
      new WithService(aService) {
        assertThrows[IllegalArgumentException] {
          aService.service.getKafkaAccess("kafka1").asInstanceOf[KafkaAccess[String, String]]
        }
      }
    }

    scenario("we get kafka instance not at the specified path") {
      new WithService(aService) {
        assertThrows[ConfigException] {
          aService.service.getKafkaAccess[String, String]("kafka-top-level").asInstanceOf[KafkaAccess[String, String]]
        }
      }
    }

    scenario("we have multiple kafka instances") {
      new WithService(aService) {
        val access1: KafkaAccess[String, String] = aService.service.getKafkaAccess[String, String]("kafka1").asInstanceOf[KafkaAccess[String, String]]
        val access2: KafkaAccess[String, String] = aService.service.getKafkaAccess[String, String]("kafka2").asInstanceOf[KafkaAccess[String, String]]
        val kafkaCustom: KafkaAccess[String, String] = aService.service.getKafkaAccess[String, String]("kafkaCustom").asInstanceOf[KafkaAccess[String, String]]


        assert(producersAreTheSame(access1, access2))
        assert(consumersAreTheSame(access1, access2))

        assert(!producersAreTheSame(access1, kafkaCustom))
        assert(!consumersAreTheSame(access1, kafkaCustom))
      }
    }

    scenario("we can easily change serializers") {
      new WithService(aService) {
        Given("Jackson serializer")
        val jacksonSerializer: JacksonJsonSerializer = new JacksonJsonSerializer()
        And("Simple serialized implicitly created from jackson")
        val kafkaValueSerializer: SimpleSerializer = jacksonSerializer // implicit conversions
        And("Default serializer created from Akka's serialization extension")
        val default: SimpleSerializer = aService.service.streamingExtension.defaultKeyValueSerializer


        When("We create kafka serialization context for kafkaAccess")
        implicit val kafkaSerializers: KafkaSerializers = KafkaSerializers(default, kafkaValueSerializer)
        val access1: KafkaAccess[String, Apple] = aService.service.getKafkaAccess[String, Apple]("kafka1").asInstanceOf[KafkaAccess[String, Apple]]


        val apple = Apple("delicious")

        And("We serialize simple object using jackson")
        val bytes: Array[Byte] = kafkaValueSerializer.toBinary(apple)


        Then("We can obtain serializer and deserializers from kafka's properties")
        assert(access1.producerSettings.valueSerializerOpt.get.isInstanceOf[org.apache.kafka.common.serialization.Serializer[Apple]])

        When("We serialize and deserialize using kafka's internal serializers")
        val serializedViaKafka: Array[Byte] = access1.producerSettings.valueSerializerOpt.get.serialize("a", apple)
        val deserializeViaKafka: Apple = access1.consumerSettings.valueDeserializerOpt.get.deserialize("a", bytes)


        Then("Kafka uses internally jackson serializer and deserializers for values")
        assert(bytes sameElements serializedViaKafka)
        assert(deserializeViaKafka == apple)


        When("We try the same with default serializers")
        val bytes_default: Array[Byte] = default.toBinary(apple)

        val apple_default: Apple = default.fromBinary[Apple](bytes_default)

        Then("Results are different")
        assert(!(bytes sameElements bytes_default))
        assert(apple_default == apple)
      }
    }


    scenario("provides default kafka access") {
      new WithService(aService) {
        type KEY = Array[Byte]
        type MSG = String
        implicit val patienceConfig: PatienceConfig = PatienceConfig(2 second, 300 millis)
        val topic: String = getUniqueTopic

        val kafka: StreamingAccess[KEY, MSG] = aService.service.defaultKafkaAccess[KEY, MSG]
        val msg = "hello world from test!"

        val push: Future[Done] = kafka.producer.single(topic, List(msg))


        whenReady(push) { res =>
          assert(res == Done)
        }

        val source: Source[MSG, Consumer.Control] = kafka.consumer.source(Set(topic)).mapAsync(1) { msg =>
          msg.committableOffset.commitScaladsl() map { _ =>
            msg.record.value()
          }
        }

        val head: Future[MSG] = source.runWith(Sink.head)

        whenReady(head) { res =>
          assert(res == msg)
        }

        val all: Future[ConsumerMessage.CommittableMessage[KEY, MSG]] = kafka.consumer.source(Set(topic)).runWith(Sink.head)

        assertThrows[java.util.concurrent.TimeoutException] {
          Await.result(all, 1 second)
        }
      }
    }


    scenario("provides streaming capabilities source, flows and sink") {
      new WithService(aService) {
        implicit val patienceConfig: PatienceConfig = PatienceConfig(1 second, 300 millis)

        val topic: String = getUniqueTopic

        val kafka: StreamingAccess[Array[Byte], String] = aService.service.getKafkaAccess[Array[Byte], String]("kafka1")

        val mappingFlow: Flow[String, ProducerRecord[Array[Byte], String], NotUsed] = Flow[String].map(new ProducerRecord(topic, _))

        val kafkaSink: Sink[ProducerRecord[Array[Byte], String], Future[Done]] = kafka.producer.sink()

        val kafkaFlow: Flow[ProducerMessage.Message[Array[Byte], String, String], ProducerMessage.Result[Array[Byte], String, String], NotUsed] = kafka.producer.flow()

        val kafkaSource: Source[ConsumerRecord[Array[Byte], String], Consumer.Control] = kafka.consumer.source(Set(topic)).mapAsync(1) { msg =>
          msg.committableOffset.commitScaladsl() map { _ =>
            msg.record
          }
        }

        val msgSource = Source(List("a", "b", "c"))


        // wire it together:

        val publish_flow: Future[Done] = msgSource.via(mappingFlow)
          .map(msg => ProducerMessage.Message[Array[Byte], String, String](msg, msg.value().toUpperCase()))
          .via(kafkaFlow)
          .map {
            case ProducerMessage.Result(_, message) =>
              LOGGER.info("cool feature you can pass some context: {}", message.passThrough)
              message.passThrough
          }
          .via(mappingFlow)
          .runWith(kafkaSink)

        whenReady(publish_flow) { res =>
          assert(res == akka.Done)
        }

        val pull_job: Future[immutable.Seq[String]] = kafka.consumer.source(Set(topic)).mapAsync(1) { msg =>
          msg.committableOffset.commitScaladsl() map { _ =>
            msg.record.value()
          }
        }.take(6).runWith(Sink.seq)


        whenReady(pull_job) { res =>
          assert(res.size == 6)
          assert(res.toSet == Set("a", "b", "c", "A", "B", "C"))
        }
      }
    }

    def producersAreTheSame[K, V](access1: KafkaAccess[K, V], access2: KafkaAccess[K, V]): Boolean = {
      val (a, b) = (access1.producerSettings, access2.producerSettings)
      b.properties == a.properties &&
        b.closeTimeout == a.closeTimeout &&
        b.dispatcher == a.dispatcher &&
        b.parallelism == a.parallelism
    }

    def consumersAreTheSame[K, V](access1: KafkaAccess[K, V], access2: KafkaAccess[K, V]): Boolean = {
      val (a, b) = (access1.consumerSettings, access2.consumerSettings)
      a.closeTimeout == b.closeTimeout &&
        a.commitTimeout == b.commitTimeout &&
        a.commitTimeWarning == b.commitTimeWarning &&
        a.dispatcher == b.dispatcher &&
        a.maxWakeups == b.maxWakeups &&
        a.pollInterval == b.pollInterval &&
        a.properties == b.properties &&
        a.stopTimeout == b.stopTimeout &&
        a.wakeupTimeout == b.wakeupTimeout
    }
  }

}

case class Apple(a: String) extends JacksonJsonSerializable

