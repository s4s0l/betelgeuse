/*
 * Copyright© 2018 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

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

import akka.stream.scaladsl.{Keep, MergeHub, Sink, Source}
import akka.stream.{KillSwitches, Materializer, UniqueKillSwitch}
import akka.{Done, NotUsed}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * @param noStreamFactory - when defined will be used to create a stream every time
  *                        no source stream is available in the hub
  * @param killSwitch      - kill switch to source that was returned
  * @author Marcin Wielgus
  */
class SequenceMergeHub[T, M](mergeSink: Sink[T, NotUsed],
                             killSwitch: UniqueKillSwitch,
                             hubComplete: Future[Done],
                             noStreamFactory: Option[() => Source[T, M]])
                            (implicit ec: ExecutionContext,
                             materializer: Materializer) {

  private val sourceQueue = mutable.Queue[(Source[T, M], Promise[M])]()
  @volatile private var running: Option[(Source[T, M], M, UniqueKillSwitch)] = None
  @volatile private var down: Boolean = false
  private var noSourceStreamKillSwitch: Option[UniqueKillSwitch] = None

  hubComplete.onComplete(_ => killSource())

  def killSource(): Unit = {
    synchronized {
      down = true
      sourceQueue.foreach { src =>
        val value: M = src._1.to(Sink.cancelled).run()
        src._2.success(value)
      }
      sourceQueue.clear()
      killSwitch.shutdown()
    }
  }

  /**
    * plays default stream only id nothing is running
    *
    * @return true if default was started
    */
  def playDefault(): Boolean = synchronized {
    if (!down && running.isEmpty && noSourceStreamKillSwitch.isEmpty) {
      runNoSourceStream()
      true
    } else {
      false
    }
  }

  def currentlyRunning(): Option[(Source[T, M], M)] = {
    running.map(it => (it._1, it._2))
  }


  /**
    * Enqueues the source for streaming. Will be run immediately
    * if no stream is currently running otherwise it will wait for all
    * previous streams to complete.
    */
  def addSource(src: Source[T, M]): Future[M] = {
    synchronized {
      if (down) {
        val m = src.to(Sink.cancelled).run()
        Future.successful(m)
      } else {
        val noSourceWasRunning = noSourceStreamKillSwitch.isDefined
        noSourceStreamKillSwitch.foreach(_.shutdown())
        val promise = Promise[M]()
        sourceQueue.enqueue((src, promise))
        if (running.isEmpty && !noSourceWasRunning)
          runNext()
        promise.future
      }
    }
  }

  /**
    * Runs the stream forcibly, stopping any running stream
    * and purging the stream queue.
    */
  def runSource(src: Source[T, M]): Future[M] = {
    synchronized {
      if (down) {
        val m = src.to(Sink.cancelled).run()
        Future.successful(m)
      } else {
        noSourceStreamKillSwitch.foreach(_.shutdown())
        sourceQueue.clear()
        running.foreach(_._3.shutdown())
        addSource(src)
      }
    }
  }

  private def dequeue(): Option[(Source[T, M], Promise[M])] = {
    if (sourceQueue.isEmpty) {
      None
    } else {
      Some(sourceQueue.dequeue())
    }
  }

  private def runNext(): Unit = synchronized {
    if (!down) {
      val tmp: Option[(Source[T, M], Promise[M], M, UniqueKillSwitch)] = dequeue()
        .map { it =>
          val ((x, done), ksw) = it._1
            .watchTermination()(Keep.both)
            .viaMat(KillSwitches.single)(Keep.both)
            .to(mergeSink)
            .run()
          done.onComplete(_ => {
            onCompleteNormal()
          })
          (it._1, it._2, x, ksw)
        }
      running = tmp.map(it => (it._1, it._3, it._4))
      tmp.foreach { it => it._2.success(it._3) }
      if (running.isEmpty) {
        runNoSourceStream()
      }
    }
  }

  private def runNoSourceStream(): Unit = {
    noStreamFactory.foreach { f =>
      val noSourceStream = f.apply()
      val ((_, done), ksw) = noSourceStream
        .watchTermination()(Keep.both)
        .viaMat(KillSwitches.single)(Keep.both)
        .to(mergeSink)
        .run()
      noSourceStreamKillSwitch = Some(ksw)
      done.onComplete(_ => {
        onCompleteNoSourceStream()
      })
    }
  }

  private def onCompleteNormal(): Unit = synchronized {
    running = None
    runNext()
  }

  private def onCompleteNoSourceStream(): Unit = synchronized {
    noSourceStreamKillSwitch = None
    runNext()
  }

}

object SequenceMergeHub {
  /**
    *
    * @param perProducerBufferSize - see [[MergeHub]] docs
    * @param noStreamFactory       - when defined will be used to create a stream every time
    *                              no source stream is available in the hub, DO NOT USE FINITE
    *                              STREAMS OF FINITE SIZE <= perProducerBufferSize, otherwise
    *                              they will flood MERGE hub and it WILL (NOT MAY IT WILL!!)
    *                              starve real streams. Generally it is best to use infinite streams.
    *                              It's because of how [[MergeHub]] works, to remove
    *                              this behaviour we would need to reimplement whole
    *                              [[MergeHub]] or provide some look ahead tooling to see
    *                              what was pulled from the hub really.
    * @return
    */
  def defineSource[T, M](perProducerBufferSize: Int = 16,
                         noStreamFactory: Option[() => Source[T, M]] = None)
                        (implicit ec: ExecutionContext,
                         materializer: Materializer)
  : Source[T, SequenceMergeHub[T, M]] =
    MergeHub.source[T](perProducerBufferSize)
      .viaMat(KillSwitches.single)(Keep.both)
      .watchTermination()(Keep.both)
      .mapMaterializedValue(it => new SequenceMergeHub[T, M](it._1._1, it._1._2, it._2, noStreamFactory))
}
