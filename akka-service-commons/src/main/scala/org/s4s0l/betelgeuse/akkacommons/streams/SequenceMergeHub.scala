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

import akka.NotUsed
import akka.stream.scaladsl.{Keep, MergeHub, Sink, Source}
import akka.stream.{KillSwitches, Materializer, UniqueKillSwitch}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * @author Marcin Wielgus
  */
class SequenceMergeHub[T, M](mergeSink: Sink[T, NotUsed], killSwitch: UniqueKillSwitch)
                            (implicit ec: ExecutionContext,
                             materializer: Materializer) {

  private val sourceQueue = mutable.Queue[(Source[T, M], Promise[M])]()
  @volatile private var running: Option[(Source[T, M], M, UniqueKillSwitch)] = None
  @volatile private var down: Boolean = false

  def killSource(): Unit = {
    synchronized {
      down = true
      sourceQueue.clear()
      killSwitch.shutdown()
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
        throw new IllegalStateException("Source is down!")
      }
      val promise = Promise[M]()
      sourceQueue.enqueue((src, promise))
      if (running.isEmpty)
        runNext()
      promise.future
    }
  }

  /**
    * Runs the stream forcibly, stopping any running stream
    * and purging the stream queue.
    */
  def runSource(src: Source[T, M]): Future[M] = {
    synchronized {
      sourceQueue.clear()
      running.foreach(_._3.shutdown())
      addSource(src)
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
    val tmp = dequeue()
      .map { it =>
        val ((x, done), ksw) = it._1
          .watchTermination()(Keep.both)
          .viaMat(KillSwitches.single)(Keep.both)
          .to(mergeSink)
          .run()
        val ret = (it._1, it._2, x, ksw)
        done.onComplete(_ => {
          runNext()
        })
        ret
      }
    running = tmp.map(it => (it._1, it._3, it._4))
    tmp.foreach { it => it._2.success(it._3) }
  }

}

object SequenceMergeHub {
  def defineSource[T, M](perProducerBufferSize: Int = 16)
                        (implicit ec: ExecutionContext,
                         materializer: Materializer)
  : Source[T, SequenceMergeHub[T, M]] =
    MergeHub.source[T](perProducerBufferSize)
      .viaMat(KillSwitches.single)(Keep.both)
      .mapMaterializedValue(it => new SequenceMergeHub[T, M](it._1, it._2))
}
