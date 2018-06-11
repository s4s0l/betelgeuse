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
  @volatile var running: Option[(Source[T, M], M)] = None
  @volatile var down: Boolean = false

  def killSource(): Unit = {
    synchronized {
      down = true
      sourceQueue.clear()
      killSwitch.shutdown()
    }
  }

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

  private def dequeue(): Option[(Source[T, M], Promise[M])] = {
    if (sourceQueue.isEmpty) {
      None
    } else {
      Some(sourceQueue.dequeue())
    }
  }

  private def runNext(): Unit = {
    running = dequeue()
      .map { it =>
        val (x, done) = it._1
          .watchTermination()(Keep.both)
          .to(mergeSink)
          .run()
        val ret = (it._1, x)
        done.onComplete(_ => {
          runNext()
        })
        it._2.success(x)
        ret
      }
  }

}

object SequenceMergeHub {
  def defineSource[T, M]()(implicit ec: ExecutionContext,
                           materializer: Materializer): Source[T, SequenceMergeHub[T, M]] =
    MergeHub.source[T]
      .viaMat(KillSwitches.single)(Keep.both)
      .mapMaterializedValue(it => new SequenceMergeHub[T, M](it._1, it._2))
}
