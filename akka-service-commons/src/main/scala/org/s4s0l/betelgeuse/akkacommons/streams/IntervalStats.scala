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
    def viaIntervalStatsKeepMat(): Source[T, (Mat, Promise[TimingStats])] = {
      wrapped.viaMat(new TimingStatsFlow[T]())(Keep.both)
    }

    def viaIntervalStatsMat(): Source[T, Promise[TimingStats]] = {
      wrapped.viaMat(new TimingStatsFlow[T]())(Keep.right)
    }
  }


  private val deNano = 1000000


  case class TimingStats(
                          totalTime: Long,
                          maxInterval: Long,
                          minInterval: Long,
                          avgInterval: Long,
                          messagesPassed: Long,
                          dSquared: Long,
                          timeToFirstMessage: Long) {

    lazy val populationStdDev: Double = Math.sqrt(populationVariance) / deNano
    lazy val stdDev: Double = Math.sqrt(this.variance) / deNano
    private lazy val count: Long = messagesPassed - 1
    private lazy val populationVariance: Double = dSquared / count
    private lazy val variance = if (count > 1) dSquared.toDouble / (count - 1) else 0d

    override def toString: String =
      s"$messagesPassed messages in $totalTime, " +
        s"avgInterval=$avgInterval, " +
        f"stdDev=$stdDev%.2f, " +
        s"min=$minInterval, " +
        s"max $maxInterval, " +
        s"delay=$timeToFirstMessage"
  }

  private[IntervalStats] case class TimingStatsFlow[A] private[IntervalStats]() extends GraphStageWithMaterializedValue[FlowShape[A, A], Promise[TimingStats]] {

    private val in = Inlet[A]("Map.in")
    private val out = Outlet[A]("Map.out")
    override val shape: FlowShape[A, A] = FlowShape.of(in, out)

    override def createLogicAndMaterializedValue(attr: Attributes)
    : (GraphStageLogic, Promise[TimingStats]) = {
      val materialized = Promise[TimingStats]()
      var lastMessageTime: Long = -1
      var minValue = Long.MaxValue
      var maxValue = Long.MinValue
      var avgValue: Long = -1
      var counter: Long = 0
      var startTime = System.nanoTime()
      var firstMessageTime: Long = -1
      //see https://dev.to/nestedsoftware/calculating-standard-deviation-on-streaming-data-253l
      var dSquared: Long = 0

      val logic = new GraphStageLogic(shape) {


        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            val time = System.nanoTime()
            if (firstMessageTime == -1) {
              firstMessageTime = time
            } else {
              val diff = time - lastMessageTime
              minValue = Math.min(minValue, diff)
              maxValue = Math.max(maxValue, diff)
              if (avgValue == -1) {
                avgValue = 0
              }
              val newAvg = (avgValue * counter + diff) / (counter + 1)
              val dSquaredIncrement =
                (diff - newAvg) * (diff - avgValue)
              dSquared = dSquared + dSquaredIncrement
              avgValue = newAvg
            }
            counter = counter + 1
            lastMessageTime = time
            push(out, grab(in))
          }


          private def materialize = {

            TimingStats(
              (lastMessageTime - startTime) / deNano,
              maxValue / deNano,
              minValue / deNano,
              avgValue / deNano,
              counter,
              dSquared,
              (firstMessageTime - startTime) / deNano
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

