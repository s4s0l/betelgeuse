
package org.s4s0l.betelgeuse.akkacommons.kamon

import akka.actor.{Actor, Props}
import akka.pattern.ask
import kamon.Kamon
import kamon.metric.MeasurementUnit
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

/**
  * @author Marcin Wielgus
  */
class BgKamonServiceTest extends BgTestService {

  private val aService = testWith(new BgKamonService with BgClusteringSharding {

    lazy val props = Props(new Actor {
      override def receive: Receive = {
        case _ =>
          Thread.sleep(1000)
          Kamon.counter("my.funny.counter", MeasurementUnit.none)
            .refine("spmcustom" -> "mytag")
            .increment()
          sender() ! "pong"
      }
    })

    lazy val shardedActor = clusteringShardingExtension.start("cycki", props, {
      case msg: String => (msg, msg)
    })

    lazy val dupaActor = system.actorOf(props, "dupa")

  })
  aService.to = 20.seconds
  aService.timeout = 20.seconds

  feature("just a kamon test") {
    scenario("alala") {
      new WithService(aService) {
        for (_ <- 1 to 3) {
          val res = Await.result(service.dupaActor ? "ping", 20.seconds)
          assert(res == "pong")
          val res2 = Await.result(service.shardedActor ? s"ping${Random.nextInt(100)}", 20.seconds)
          assert(res2 == "pong")
        }

      }
    }
  }

}
