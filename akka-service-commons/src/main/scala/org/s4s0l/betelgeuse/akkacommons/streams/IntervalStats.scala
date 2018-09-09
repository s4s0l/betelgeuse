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

package org.s4s0l.betelgeuse.akkacommons.streams

import akka.stream._
import akka.stream.scaladsl.{Keep, Source}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.concurrent.Promise

/**
  * @author Marcin Wielgus
  */

object IntervalStats {

  implicit class TimingStatsSource[T, Mat](wrapped: Source[T, Mat]) {
    def viaIntervalStatsKeepMat(): Source[T, (Mat, Promise[StreamIntervalStats])] = {
      wrapped.viaMat(new TimingStatsFlow[T]())(Keep.both)
    }

    def viaIntervalStatsMat(): Source[T, Promise[StreamIntervalStats]] = {
      wrapped.viaMat(new TimingStatsFlow[T]())(Keep.right)
    }
  }


  private val deNano = 1000000


  case class StreamTimeStats(
                              maxTime: Long = Long.MinValue,
                              minTime: Long = Long.MaxValue,
                              avgTime: Long = 0,
                              messagesPassed: Long = 0,
                              dSquared: Long = 0
                            ) {
    lazy val populationStdDev: Double = Math.sqrt(populationVariance) / deNano
    lazy val stdDev: Double = Math.sqrt(this.variance) / deNano
    private lazy val count: Long = messagesPassed - 1
    private lazy val populationVariance: Double = dSquared / count
    private lazy val variance = if (count > 1) dSquared.toDouble / (count - 1) else 0d

    //see https://dev.to/nestedsoftware/calculating-standard-deviation-on-streaming-data-253l
    def tick(time: Long): StreamTimeStats = {
      val diff = time
      val minValue = Math.min(minTime, diff)
      val maxValue = Math.max(maxTime, diff)
      val oldAvg = avgTime
      val newAvg = (oldAvg * messagesPassed + diff) / (messagesPassed + 1)
      val dSquaredIncrement =
        (diff - newAvg) * (diff - oldAvg)
      this.copy(
        dSquared = dSquared + dSquaredIncrement,
        avgTime = newAvg,
        messagesPassed = messagesPassed + 1,
        maxTime = maxValue,
        minTime = minValue
      )
    }
  }

  case class StreamIntervalStats(
                                  totalTime: Long,
                                  timeToFirstMessage: Long,
                                  intervalStats: StreamTimeStats
                                ) {
    override def toString: String =
      s"${intervalStats.messagesPassed} messages in ${totalTime / deNano}, " +
        s"avgInterval=${intervalStats.avgTime / deNano}, " +
        f"stdDev=${intervalStats.stdDev}%.2f, " +
        s"min=${intervalStats.minTime / deNano}, " +
        s"max=${intervalStats.maxTime / deNano}, " +
        s"delay=${timeToFirstMessage / deNano}"
  }


  private[IntervalStats] case class TimingStatsFlow[A] private[IntervalStats]()
    extends GraphStageWithMaterializedValue[FlowShape[A, A], Promise[StreamIntervalStats]] {

    private val in = Inlet[A]("Map.in")
    private val out = Outlet[A]("Map.out")
    override val shape: FlowShape[A, A] = FlowShape.of(in, out)

    override def createLogicAndMaterializedValue(attr: Attributes)
    : (GraphStageLogic, Promise[StreamIntervalStats]) = {
      val materialized = Promise[StreamIntervalStats]()
      var firstMessageTime = -1L
      var lastMessageTime = -1L
      var startTime = System.nanoTime()
      var timeStats: StreamTimeStats = StreamTimeStats()
      val logic = new GraphStageLogic(shape) {


        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val time = System.nanoTime()
            if (firstMessageTime == -1) {
              firstMessageTime = time
            } else {
              val diff = time - lastMessageTime
              timeStats = timeStats.tick(diff)
            }
            lastMessageTime = time
            push(out, grab(in))
          }


          private def materialize = {
            StreamIntervalStats(
              lastMessageTime - startTime,
              firstMessageTime - startTime,
              timeStats
            )
          }


          override def onUpstreamFinish(): Unit = {
            materialized.success(materialize)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            materialized.success(materialize)
            super.onUpstreamFailure(ex)
          }
        })
        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        override def preStart(): Unit = {
          startTime = System.nanoTime()
        }
      }

      (logic, materialized)
    }
  }


}

