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

package org.s4s0l.betelgeuse.akkacommons.kamon

import akka.stream.scaladsl.{Flow, Source}
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import kamon.metric.{Counter, Gauge, StartedTimer, Timer}
import kamon.{Kamon, Tags}

/**
  * @author Marcin Wielgus
  */
object KamonStreams {

  implicit class KamonStatsFlow[In, T, Mat](wrapped: Flow[In, T, Mat]) {
    def withBasicMetrics(baseCounterMetrics: StreamBasicMetrics): Flow[In, T, Mat] = {
      wrapped.via(new BasicCountersStage[T](baseCounterMetrics))
    }

    def withIntervalMetrics(metrics: StreamIntervalMetrics): Flow[In, T, Mat] = {
      wrapped.via(new IntervalStatsStage[T](metrics))
    }

    def withInitiationMetrics(metrics: StreamInitiationMetrics): Flow[In, T, Mat] = {
      wrapped.via(new InitiationStatsStage[T](metrics))
    }

    def withDemandLatencyMetrics(metrics: StreamDemandLatencyMetrics): Flow[In, T, Mat] = {
      wrapped.via(new DemandLatencyStage[T](metrics))
    }
  }

  implicit class KamonStatsSource[T, Mat](wrapped: Source[T, Mat]) {

    def withBasicMetrics(baseCounterMetrics: StreamBasicMetrics): Source[T, Mat] = {
      wrapped.via(new BasicCountersStage[T](baseCounterMetrics))
    }

    def withIntervalMetrics(metrics: StreamIntervalMetrics): Source[T, Mat] = {
      wrapped.via(new IntervalStatsStage[T](metrics))
    }

    def withInitiationMetrics(metrics: StreamInitiationMetrics): Source[T, Mat] = {
      wrapped.via(new InitiationStatsStage[T](metrics))
    }

    def withDemandLatencyMetrics(metrics: StreamDemandLatencyMetrics): Source[T, Mat] = {
      wrapped.via(new DemandLatencyStage[T](metrics))
    }
  }


  object StreamBasicMetrics {

    def apply(streamName: String, tags: (String, String)*): StreamBasicMetrics = {
      apply(streamName, tags.toMap)
    }

    def apply(streamName: String, tags: Tags): StreamBasicMetrics = {
      val metricName = "bg.stream.basic"
      new StreamBasicMetrics(
        Kamon.counter(metricName).refine(tags ++ Map("statistic" -> "messages", "streamName" -> streamName)),
        Kamon.counter(metricName).refine(tags ++ Map("statistic" -> "start", "streamName" -> streamName)),
        Kamon.counter(metricName).refine(tags ++ Map("statistic" -> "fail", "streamName" -> streamName)),
        Kamon.counter(metricName).refine(tags ++ Map("statistic" -> "finish-down", "streamName" -> streamName)),
        Kamon.counter(metricName).refine(tags ++ Map("statistic" -> "finish-up", "streamName" -> streamName)),
        Kamon.gauge(metricName).refine(tags ++ Map("statistic" -> "working", "streamName" -> streamName))
      )
    }
  }

  case class StreamBasicMetrics private(
                                         messagesCount: Counter,
                                         startCounter: Counter,
                                         failCounter: Counter,
                                         downFinish: Counter,
                                         upFinish: Counter,
                                         inTransit: Gauge
                                       )

  private[KamonStreams] case class BasicCountersStage[A](metrics: StreamBasicMetrics)
    extends GraphStage[FlowShape[A, A]] {

    private val in = Inlet[A]("Map.in")
    private val out = Outlet[A]("Map.out")
    override val shape: FlowShape[A, A] = FlowShape.of(in, out)

    override def createLogic(attr: Attributes)
    : GraphStageLogic = {
      new GraphStageLogic(shape) {
        var running: Boolean = true

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            metrics.messagesCount.increment()
            push(out, grab(in))
          }

          override def onUpstreamFinish(): Unit = {
            if (running) {
              metrics.upFinish.increment()
              running = false
            }
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            metrics.failCounter.increment()
            super.onUpstreamFailure(ex)
          }
        })
        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }

          override def onDownstreamFinish(): Unit = {
            if (running) {
              metrics.downFinish.increment()
              running = false
            }
            super.onDownstreamFinish()
          }
        })

        override def postStop(): Unit = {
          metrics.inTransit.decrement()
          super.postStop()
        }

        override def preStart(): Unit = {
          metrics.inTransit.increment()
          metrics.startCounter.increment()
          super.preStart()
        }
      }
    }
  }


  object StreamIntervalMetrics {

    def apply(streamName: String, tags: (String, String)*): StreamIntervalMetrics = {
      apply(streamName, tags.toMap)
    }

    def apply(streamName: String, tags: Tags): StreamIntervalMetrics = {
      val metricName = "bg.stream.interval"
      StreamIntervalMetrics(
        Kamon.timer(metricName).refine(tags ++ Map("statistic" -> "int", "streamName" -> streamName)),
      )
    }
  }

  case class StreamIntervalMetrics private(intervalTimer: Timer)

  private[KamonStreams] case class IntervalStatsStage[A](metrics: StreamIntervalMetrics)
    extends GraphStage[FlowShape[A, A]] {

    private val in = Inlet[A]("Map.in")
    private val out = Outlet[A]("Map.out")
    override val shape: FlowShape[A, A] = FlowShape.of(in, out)

    override def createLogic(attr: Attributes)
    : GraphStageLogic = {
      var lastMessageStartedTimer: Option[StartedTimer] = None
      new GraphStageLogic(shape) {


        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            lastMessageStartedTimer.foreach(_.stop())
            lastMessageStartedTimer = Some(metrics.intervalTimer.start())
            push(out, grab(in))
          }

        })
        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

      }
    }
  }


  object StreamInitiationMetrics {
    def apply(streamName: String, tags: (String, String)*): StreamInitiationMetrics = {
      apply(streamName, tags.toMap)
    }

    def apply(streamName: String, tags: Tags): StreamInitiationMetrics = {
      val metricName = "bg.stream.initiation"
      new StreamInitiationMetrics(
        Kamon.timer(metricName).refine(tags ++ Map("statistic" -> "first-message", "streamName" -> streamName)),
      )
    }
  }

  case class StreamInitiationMetrics private(firstMessageTimer: Timer)

  private[KamonStreams] case class InitiationStatsStage[A](
                                                            metrics: StreamInitiationMetrics,
                                                          )
    extends GraphStage[FlowShape[A, A]] {

    private val in = Inlet[A]("Map.in")
    private val out = Outlet[A]("Map.out")
    override val shape: FlowShape[A, A] = FlowShape.of(in, out)

    override def createLogic(attr: Attributes)
    : GraphStageLogic = {
      var firstMessageStartedTimer: Option[StartedTimer] = None
      new GraphStageLogic(shape) {


        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            firstMessageStartedTimer.foreach(_.stop())
            firstMessageStartedTimer = None
            push(out, grab(in))
          }

        })
        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            pull(in)
          }
        })

        override def preStart(): Unit = {
          firstMessageStartedTimer = Some(metrics.firstMessageTimer.start())
        }
      }
    }
  }

  object StreamDemandLatencyMetrics {
    def apply(streamName: String, tags: (String, String)*): StreamDemandLatencyMetrics = {
      apply(streamName, tags.toMap)
    }

    def apply(streamName: String, tags: Tags): StreamDemandLatencyMetrics = {
      val metricName = "bg.stream.demand"
      StreamDemandLatencyMetrics(
        Kamon.timer(metricName).refine(tags ++ Map("statistic" -> "upstream", "streamName" -> streamName)),
        Kamon.timer(metricName).refine(tags ++ Map("statistic" -> "downstream", "streamName" -> streamName)),
      )
    }

  }

  case class StreamDemandLatencyMetrics private(
                                                 upstreamDemandTimer: Timer,
                                                 downstreamDemandTimer: Timer
                                               )

  private case class DemandLatencyStage[A] private[KamonStreams](metrics: StreamDemandLatencyMetrics)
    extends GraphStage[FlowShape[A, A]] {

    private val in = Inlet[A]("Map.in")
    private val out = Outlet[A]("Map.out")
    override val shape: FlowShape[A, A] = FlowShape.of(in, out)

    override def createLogic(attr: Attributes)
    : GraphStageLogic = {
      var firstMessageTime = -1L
      var startedUpstreamTimer: Option[StartedTimer] = None
      var startedDownstreamTimer: Option[StartedTimer] = None
      new GraphStageLogic(shape) {

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            if (firstMessageTime == -1) {
              firstMessageTime = System.nanoTime()
            } else {
              startedDownstreamTimer = Some(metrics.downstreamDemandTimer.start())
              startedUpstreamTimer.foreach(_.stop())
              startedUpstreamTimer = None
            }
            push(out, grab(in))
          }

          override def onUpstreamFinish(): Unit = {
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            super.onUpstreamFailure(ex)
          }
        })
        setHandler(out, new OutHandler {
          override def onPull(): Unit = {
            if (firstMessageTime != -1) {
              startedUpstreamTimer = Some(metrics.upstreamDemandTimer.start())
              startedDownstreamTimer.foreach(_.stop())
              startedDownstreamTimer = None
            }
            pull(in)
          }
        })
      }
    }
  }

}