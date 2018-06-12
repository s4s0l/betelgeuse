/*
 * Copyright© 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.s4s0l.betelgeuse.akkacommons.tools

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import javax.script.{ScriptEngine, ScriptEngineManager}
import org.s4s0l.betelgeuse.akkacommons.tools.ScalaScriptEngineActor.Protocol.{Eval, EvalNotOk, EvalOk, EvalResult}
import org.s4s0l.betelgeuse.akkacommons.tools.ScalaScriptEngineActor.Settings
import org.s4s0l.betelgeuse.akkacommons.utils.QA.{Uuid, UuidQuestion}
import org.s4s0l.betelgeuse.akkacommons.utils.{ActorTarget, QA}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Because scala script engine is resource hungry, and has problems with being concurent
  * an utility actor that holds reference to script engine and performs
  * evals on scripts. With some small additions.
  *
  * @author Marcin Wielgus
  */
class ScalaScriptEngineActor(settings: Settings) extends Actor with ActorLogging {

  private var engine: ScriptEngine = _

  //todo this actor probably should use some separate dispatcher as scripts may block
  override def preStart(): Unit = {
    super.preStart()
    val start = System.currentTimeMillis()
    try {
      engine = ScalaScriptEngineActor.manager.getEngineByName("scala")
    } finally {
      log.info("scala engine creation utook {} ms", System.currentTimeMillis() - start)
    }
  }

  override def receive: Actor.Receive = {
    case Eval(script, messageId, _) =>
      val start = System.currentTimeMillis()
      try {
        val unit = engine.eval(script)
        sender() ! EvalOk(unit, messageId)
      } catch {
        case ex: Exception =>
          sender() ! EvalNotOk(ex, messageId)
      } finally {
        log.info("scala evaluation took {} ms", System.currentTimeMillis() - start)
      }

  }


}


object ScalaScriptEngineActor {

  private lazy val manager: ScriptEngineManager = new ScriptEngineManager(classOf[ScalaScriptEngineActor].getClassLoader)

  /**
    * creates props for actor
    */
  def start(settings: Settings = Settings("scala-scripting-actor"), propsMapper: Props => Props = identity)
           (implicit actorSystem: ActorSystem): Protocol = {
    val ref = actorSystem.actorOf(Props(new ScalaScriptEngineActor(settings)), settings.name)
    new Protocol(ref)
  }

  trait AsyncScriptEvaluator {
    def eval[T](script: String)(implicit executionContext: ExecutionContext, timeout: Timeout): Future[T]
  }

  final case class Settings(name: String)

  /**
    * An protocol for [[ScalaScriptEngineActor]]
    */
  final class Protocol(actorRef: ActorTarget) extends AsyncScriptEvaluator {

    override def eval[T](script: String)(implicit executionContext: ExecutionContext, timeout: Timeout): Future[T] = {
      evaluate[T](Eval[T](script, timeout = timeout))(executionContext, ActorRef.noSender)
        .map {
          case EvalOk(value, _) => value.asInstanceOf[T]
          case EvalNotOk(ex, _) => throw ex
        }
    }

    def evaluate[T](msg: Eval[T])
                   (implicit executionContext: ExecutionContext, sender: ActorRef): Future[EvalResult[T]] = {
      actorRef.?(msg)(msg.timeout, sender).mapTo[EvalResult[T]]
    }
  }

  object Protocol {

    trait EvalResult[T] extends QA.Result[Uuid, T]

    case class Eval[T](script: String, messageId: Uuid = QA.uuid, timeout: Timeout = 5 seconds) extends UuidQuestion

    case class EvalOk[T](value: T, correlationId: Uuid) extends EvalResult[T] with QA.OkResult[Uuid, T]

    case class EvalNotOk[T](ex: Exception, correlationId: Uuid) extends EvalResult[T] with QA.NotOkResult[Uuid, T]


  }


}    