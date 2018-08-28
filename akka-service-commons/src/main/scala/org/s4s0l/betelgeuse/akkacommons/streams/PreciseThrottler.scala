
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

import java.util.concurrent.Executors

import akka.actor.LightArrayRevolverScheduler
import akka.event.NoLogging
import akka.stream.scaladsl.Source
import akka.stream.stage.{AsyncCallback, _}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, _}

/**
  * @author Marcin Wielgus
  */
object PreciseThrottler {


  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)


  implicit class PreciseThrottlerSource[T, Mat](wrapped: Source[T, Mat]) {
    def viaPreciseThrottler(delay: FiniteDuration, buffer: Int, warnOnNoData: Boolean): Source[T, Mat] = {
      wrapped.via(throttleFixed(delay, buffer, warnOnNoData))
    }

    def viaPreciseThrottlerAkka(delay: FiniteDuration, buffer: Int, warnOnNoData: Boolean,
                                initialDelay: FiniteDuration = Duration.Zero): Source[T, Mat] = {
      wrapped.via(throttleLightAkka(delay, initialDelay, buffer, warnOnNoData))
    }
  }


  private trait Cancelable {
    def cancel(): Unit
  }

  private type FixedScheduler = AsyncCallback[Long] => Cancelable

  private[PreciseThrottler] val deNano: Long = 1000000


  private[PreciseThrottler] val tickers = mutable.Map[Long, Ticker]()

  private def createTicker(millis: Long) = {
    val t = new Ticker(millis * deNano, Math.max(1L, millis / 10l).toInt)
    t.start()
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        t.stop()
      }
    })
    t
  }

  private[PreciseThrottler] lazy val lightScheduler = {
    val ret = new LightArrayRevolverScheduler(
      ConfigFactory.parseString(
        """
          |akka.scheduler.tick-duration = 1 ms
          |akka.scheduler.ticks-per-wheel = 512
          |akka.scheduler.shutdown-timeout = 1 s
        """.stripMargin), NoLogging,
      Executors.defaultThreadFactory() //replace with some named version
    )
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        ret.close()
      }
    })
    ret
  }


  def throttleFixed[A](delay: FiniteDuration, buffer: Int, warnOnNoData: Boolean): GraphStage[FlowShape[A, A]] = {
    val asMillis = delay.toMillis
    val ticker = tickers.synchronized {
      tickers.getOrElse(asMillis, {
        tickers(asMillis) = createTicker(asMillis)
        tickers(asMillis)
      })

    }
    val shd: FixedScheduler = ticker.register
    new FixedThrottle[A](delay.toMillis, shd, buffer, warnOnNoData)
  }

  def throttleLightAkka[A](delay: FiniteDuration,
                           initialDelay: FiniteDuration,
                           buffer: Int,
                           warnOnNoData: Boolean
                          ): GraphStage[FlowShape[A, A]] = {
    implicit val ec: ExecutionContext = SameThreadExecutionContext
    val shd: FixedScheduler = cb => {
      val ret = lightScheduler.schedule(
        initialDelay,
        delay,
        () => cb.invoke(System.nanoTime())
      )
      () => ret.cancel()
    }
    new FixedThrottle[A](delay.toMillis, shd, buffer, warnOnNoData)
  }


  private object SameThreadExecutionContext extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = {
      try {
        runnable.run()
      } catch {
        case ex: Throwable =>
          ex.printStackTrace()
      }
    }

    override def reportFailure(cause: Throwable): Unit =
      throw new IllegalStateException("exception in sameThreadExecutionContext", cause)
  }


  private[PreciseThrottler] class FiniteQueue[A](limit: Int) extends mutable.Queue[A] {

    override def enqueue(elems: A*): scala.Unit = {
      if (super.size >= limit) {
        throw new Exception("is full!!")
      }
      this ++= elems
    }

    def isFull: Boolean = limit == size
  }

  private[PreciseThrottler] class Ticker private[PreciseThrottler](interval: Long, slotsNum: Int) {

    private val callbacks = Array.fill(slotsNum)(mutable.HashSet[AsyncCallback[Long]]())
    @volatile private var running: Boolean = false

    private[PreciseThrottler] def register(cb: AsyncCallback[Long]): Cancelable = {
      val slot = cb.hashCode() % slotsNum
      val bucket = callbacks(slot)
      bucket.synchronized {
        bucket.add(cb)
      }
      () => unregister(cb)
    }

    private[PreciseThrottler] def unregister(cb: AsyncCallback[Long]): Unit = {
      val slot = cb.hashCode() % slotsNum
      val bucket = callbacks(slot)
      bucket.synchronized {
        bucket.remove(cb)
      }
    }

    private[PreciseThrottler] def stop(): Unit = {
      synchronized {
        if (running) {
          running = false
          wait(interval * 5 / deNano)
          LOGGER.debug(s"Ticker $name stopped.")
        }
      }
    }

    private val name = s"PreciseThrottlerThread-$interval-ms"

    private[PreciseThrottler] def start(): Unit = {
      synchronized {
        if (!running) {
          running = true
          new Thread(null,
            () => threadRun(),
            name
          ).start()
        }
      }
    }

    private def threadRun(): Unit = {
      while (running) {
        for (slot <- 0 until slotsNum) {
          val time = System.nanoTime()
          val bucket = callbacks(slot)
          bucket.synchronized {
            bucket.foreach { cb =>
              cb.invoke(time)
            }
          }
          val timeWasted = System.nanoTime() - time
          val nextWaitInNano = interval / slotsNum.toLong - timeWasted
          if (nextWaitInNano > 0)
            Thread.sleep(nextWaitInNano / deNano, (nextWaitInNano % deNano).toInt)
          else
            LOGGER.warn(s"$name wastes too much time notifying.")
        }
      }
      synchronized {
        notifyAll()
      }
    }
  }


  private[PreciseThrottler] case class FixedThrottle[A](
                                                         intervalMillis: Long,
                                                         scheduler: FixedScheduler,
                                                         bufferSize: Int,
                                                         warnOnNoData: Boolean
                                                       ) extends GraphStage[FlowShape[A, A]] {

    private val in = Inlet[A]("Map.in")
    private val out = Outlet[A]("Map.out")
    override val shape: FlowShape[A, A] = FlowShape.of(in, out)

    def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      val buffer = new FiniteQueue[A](bufferSize)
      new GraphStageLogic(shape) {

        def closed: Boolean = isClosed(in)

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            buffer.enqueue(grab(in))
            if (!closed && !buffer.isFull) {
              pull(in)
            }
          }

          override def onUpstreamFinish(): Unit = {
            if (buffer.isEmpty) {
              completeStage()
            }
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            super.onUpstreamFailure(ex)
          }
        })

        setHandler(out, new OutHandler {

          override def onPull(): Unit = {
            if (!closed && !hasBeenPulled(in) && !buffer.isFull) {
              //this should not happen
              LOGGER.warn("This should not happen, contact developers")
            }

          }
        })

        var lastMessageSend: Long = -1

        private def dequeueAndPush(fireTimeNanos: Long): Unit = {
          push(out, buffer.dequeue())
          lastMessageSend = fireTimeNanos
          if (!closed && !hasBeenPulled(in)) {
            pull(in)
          }
        }


        def tick(fireTimeNanos: Long): Unit = {
          if (closed && buffer.isEmpty) {
            completeStage()
          }
          if (isAvailable(out)) {
            if (buffer.isEmpty) {
              //todo ??? we should leave info for onPull to catch up when  messagesToBeSend > 1
              //we do not warn before first message - this is very likely to happen regardless of upstream speed
              if (warnOnNoData && lastMessageSend != -1) LOGGER.warn("Throttling has no data to pull, too slow producer")
            }
            else {
              dequeueAndPush(fireTimeNanos)
              if (closed && buffer.isEmpty) {
                completeStage()
              }
            }
          }

        }

        var myCallback: Cancelable = _

        override def postStop(): Unit = {
          myCallback.cancel()
        }

        override def preStart(): Unit = {
          myCallback = scheduler(getAsyncCallback[Long](tick))
          pull(in)
        }
      }
    }
  }

}