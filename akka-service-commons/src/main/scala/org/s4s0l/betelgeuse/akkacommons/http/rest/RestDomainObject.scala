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

/*
 * Copyright© 2017 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.http.rest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshallable}
import akka.http.scaladsl.model.{HttpEntity, HttpMethod, HttpMethods, headers}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher1, _}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.message.MessageHeaders.Headers
import org.s4s0l.betelgeuse.akkacommons.serialization.HttpMarshalling
import org.s4s0l.betelgeuse.akkacommons.utils.QA
import org.s4s0l.betelgeuse.akkacommons.utils.QA._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Utility protocol for automatic generation of api following
  * https://github.com/restfulobjects/restfulobjects-spec/blob/2180261f47b7e9279bdb18180ffbee1430b1e342/restfulobjects-spec.pdf?raw=true
  * Introduces some common message types and conventions.
  *
  * @author Marcin Wielgus
  */
object RestDomainObject {

  sealed trait RestCommand extends UuidQuestion {

    override lazy val messageId: Uuid = headers.get("messageId").getOrElse(QA.uuid)

    def headers: Headers

  }

  sealed trait ActionDesc {
    def name: String

    def params: Set[String]

    def method: HttpMethod
  }

  //todo: should contain headers so that actions could send back something more than payload
  sealed trait RestCommandResult[T] extends Result[Uuid, T]

  trait ProtocolWithUpdates[ID, T <: AnyRef, V] extends ProtocolRoute[ID, T, V]
    with Gets[ID, T, V]
    with Deletes[ID, T, V]
    with Updates[ID, T, V]
    with Creates[ID, T, V]

  trait ProtocolWithImmutable[ID, T <: AnyRef, V] extends ProtocolRoute[ID, T, V]
    with Gets[ID, T, V]
    with Creates[ID, T, V]


  trait RestProtocol {

    def and(other: RestProtocol): RestProtocol = new RestProtocol {
      override def createRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
        RestProtocol.this.createRoute ~ other.createRoute
      }
    }

    def createRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = reject

  }

  trait VersionedRestProtocol[V] extends RestProtocol {

    def version: V

    override def createRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      super.createRoute ~ pathPrefix(version.toString) {
        createVersionedRoute
      }
    }

    protected def createVersionedRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = reject

  }

  /**
    *
    * @tparam ID typed ID of an object
    * @tparam T  type of an object
    * @tparam V  typed version, using some customizable type so it can be safely case matched
    */
  trait DomainObjectProtocol[ID, T <: AnyRef, V] extends VersionedRestProtocol[V] {


    def id(id: String): ID

    def classTag: ClassTag[T]

    def domainObjectType: String

    override protected def createVersionedRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      super.createVersionedRoute ~
        pathPrefix("objects" / domainObjectType) {
          createObjectRoute
        }
    }

    protected def createObjectRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = reject

  }

  trait Gets[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {
    i: ProtocolRoute[ID, T, V] =>

    def get(msg: Get[ID, V])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[T]]

    def list(msg: GetList[V])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[List[ID]]]

    protected override def createObjectRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      super.createObjectRoute ~
        listDirective ~
        getDirective
    }

    protected def getDirective(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      Directives.get {
        withIdHeaders() { (id, headers) =>
          val msg = Get(version, id, headers)
          onComplete(get(msg).recover(withRecovery(msg.messageId)))(completeWithPayload)
        }
      }
    }

    protected def listDirective(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      Directives.get {
        pathEnd {
          withHeaders() { headers =>
            val msg = GetList(version, headers)
            onComplete(list(msg).recover(withRecovery(msg.messageId)))(completeWithPayload)
          }
        }
      }
    }
  }

  trait Deletes[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {
    i: ProtocolRoute[ID, T, V] =>
    def delete(msg: Delete[ID, V])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[ID]]

    protected override def createObjectRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      super.createObjectRoute ~
        deleteDirective
    }

    protected def deleteDirective(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      Directives.delete {
        withIdHeaders() { (id, headers) =>
          val msg = Delete(version, id, headers)
          onComplete(delete(msg).recover(withRecovery(msg.messageId)))(completeWithNoPayload)
        }
      }
    }

  }

  trait Updates[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {
    i: ProtocolRoute[ID, T, V] =>
    def update(msg: Update[ID, T, V])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[ID]]

    protected override def createObjectRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      super.createObjectRoute ~
        updateDirective
    }

    protected def updateDirective(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      Directives.put {
        withIdHeaders() { (id, headers) =>
          entity(as[T]) { e =>
            val msg = Update(version, id, e, headers)
            onComplete(update(msg).recover(withRecovery(msg.messageId)))(completeWithNoPayload)
          }
        }

      }
    }

  }

  trait Creates[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {
    i: ProtocolRoute[ID, T, V] =>
    def generateId: ID

    def create(msg: Create[ID, T, V])(implicit executionContext: ExecutionContext, sender: ActorRef): Future[RestCommandResult[ID]]

    protected override def createObjectRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      super.createObjectRoute ~
        createDirective
    }

    protected def createDirective(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      Directives.post {
        pathEnd {
          withHeaders() { headers =>
            entity(as[T]) { e =>

              val msg = Create(version, generateId, e, headers)
              onComplete(create(msg).recover(withRecovery[ID](msg.messageId)))(completeWithId)
            }
          }
        }
      }
    }

  }

  trait Actions[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {
    i: ProtocolRoute[ID, T, V] =>

    //TODO use maybe directive api here to parse params?
    def actions: Map[ActionDesc, (Action[ID, V], ExecutionContext, ActorRef) => Future[RestCommandResult[_]]] = Map()

    protected override def createObjectRoute(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      super.createObjectRoute ~
        actionsDirective
    }

    protected def actionsDirective(implicit executionContext: ExecutionContext, sender: ActorRef): Route = {
      val actionList = path(Segment / "actions") { _ =>
        complete(ToResponseMarshallable(ActionList(actions.keys.map(e => ActionPrompt(e.name, e.method.value, e.params.toList)).toList))(toEntityMarshaller))
      }

      val prompts = actions.foldLeft(actionList: Route) { (c: Route, e) =>
        c ~
          path(Segment / "actions" / e._1.name) { _ =>
            complete(ToResponseMarshallable(ActionPrompt(e._1.name, e._1.method.value, e._1.params.toList))(toEntityMarshaller))
          }
      }

      val actionDirectives = actions.map { e =>
        e._1 ->
          withIdHeaders(Segment / "actions" / e._1.name / "invoke", e._1.params) { (id, headers) =>
            //todo make it accept params in request body also, as simple map
            parameterMap { paramMap =>
              val params = paramMap ++ headers.filter(it => e._1.params.contains(it._1))
              val msg = Action(version, id, e._1.name, params, headers)
              onComplete(e._2(msg, executionContext, sender).recover(withRecovery(msg.messageId)).asInstanceOf[Future[RestCommandResult[AnyRef]]])(completeWithPayload)
            }
          }
      }

      get {
        actionDirectives
          .filter(_._1.method == HttpMethods.GET)
          .foldLeft(prompts: Route) { (c: Route, e) =>
            c ~ e._2
          }
      } ~
        put {
          actionDirectives
            .filter(_._1.method == HttpMethods.PUT)
            .foldLeft(reject: Route) { (c: Route, e) =>
              c ~ e._2
            }
        } ~
        post {
          actionDirectives
            .filter(_._1.method == HttpMethods.POST)
            .foldLeft(reject: Route) { (c: Route, e) =>
              c ~ e._2
            }
        }


    }
  }

  trait ProtocolRoute[ID, T <: AnyRef, V] {
    protocol: DomainObjectProtocol[ID, T, V] =>

    val defaultPassedHeaders: Set[String] = Set("messageId")

    protected implicit def fromEntityMarshaller: FromEntityUnmarshaller[T]

    def withRecovery[X](correlationId: Uuid): PartialFunction[Throwable, RestCommandResult[X]] = {
      case ex: Throwable => RestCommandNotOk[X](ex, correlationId)
    }

    def completeWithPayload[X <: AnyRef]: PartialFunction[Try[RestCommandResult[X]], Route] = {
      case Success(RestCommandOk(value, correlationId)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          complete(if (value == NoPayload) HttpEntity.Empty else toResponseMarshallAble(value))
        }
      case Success(RestCommandNotOk(ex, correlationId)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          failWith(ex)
        }
      case Failure(ex) => failWith(ex)
    }

    def completeWithNoPayload[X <: AnyRef]: PartialFunction[Try[RestCommandResult[_]], Route] = {
      case Success(RestCommandOk(_, correlationId)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          complete(HttpEntity.Empty)
        }
      case Success(RestCommandNotOk(ex, correlationId)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          failWith(ex)
        }
      case Failure(ex) => failWith(ex)
    }

    def completeWithId: PartialFunction[Try[RestCommandResult[ID]], Route] = {
      case Success(RestCommandOk(value, correlationId)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          complete(toResponseMarshallAble(Id(value.toString)))
        }
      case Success(RestCommandNotOk(ex, correlationId)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          failWith(ex)
        }
      case Failure(ex) => failWith(ex)
    }

    def toResponseMarshallAble[X <: AnyRef](value: X): ToResponseMarshallable = ToResponseMarshallable(value)(toEntityMarshaller)

    def withIdHeaders(idMatcher: PathMatcher1[String] = Segment, acceptedHeaders: Set[String] = Set.empty): Directive[(ID, Headers)] = {
      path(idMatcher).map(protocol.id) & withHeaders(acceptedHeaders)
    }

    def withHeaders(acceptedHeaders: Set[String] = Set.empty): Directive1[Headers] = Directives
      .extract(_.request.headers
        .filter(it => defaultPassedHeaders.contains(it.name()) || acceptedHeaders.contains(it.name()))
        .map(it => it.name() -> it.value())
        .toMap

      )

    protected def toEntityMarshaller: ToEntityMarshaller[AnyRef]

  }

  case class Get[ID, V](version: V, id: ID, headers: Headers) extends RestCommand

  case class GetList[V](version: V, headers: Headers) extends RestCommand

  case class Delete[ID, V](version: V, id: ID, headers: Headers) extends RestCommand

  case class Create[ID, T, V](version: V, id: ID, value: T, headers: Headers) extends RestCommand

  case class Update[ID, T, V](version: V, id: ID, value: T, headers: Headers) extends RestCommand

  case class Action[ID, V](version: V, id: ID, actionName: String, params: Map[String, String], headers: Headers) extends RestCommand

  case class RestCommandOk[T](value: T, correlationId: Uuid) extends RestCommandResult[T] with OkResult[Uuid, T]

  case class RestCommandNotOk[T](ex: Throwable, correlationId: Uuid) extends RestCommandResult[T] with NotOkResult[Uuid, T]

  case class Idempotent(name: String, params: Set[String] = Set()) extends ActionDesc {
    override def method: HttpMethod = HttpMethods.PUT
  }

  case class NonIdempotent(name: String, params: Set[String] = Set()) extends ActionDesc {
    override def method: HttpMethod = HttpMethods.POST
  }

  case class Query(name: String, params: Set[String] = Set()) extends ActionDesc {
    override def method: HttpMethod = HttpMethods.GET
  }

  trait BaseProtocol[ID, T <: AnyRef, V]
    extends DomainObjectProtocol[ID, T, V]
      with ProtocolRoute[ID, T, V] {

    val baseProtocolSettings: BaseProtocolSettings[ID, T, V]

    protected implicit def toEntityMarshaller: ToEntityMarshaller[AnyRef] = baseProtocolSettings.httpMarshaller.marshaller[AnyRef]

    protected implicit def fromEntityMarshaller: FromEntityUnmarshaller[T] = baseProtocolSettings.httpMarshaller.unmarshaller[T](baseProtocolSettings.classTag)

    protected implicit def timeout: Timeout = baseProtocolSettings.timeout

    override def version: V = baseProtocolSettings.version

    override def id(id: String): ID = baseProtocolSettings.stringToId(id)

    override def classTag: ClassTag[T] = baseProtocolSettings.classTag

    override def domainObjectType: String = baseProtocolSettings.domainObjectType
  }

  class BaseRestProtocol[ID, T <: AnyRef, V](val baseProtocolSettings: BaseProtocolSettings[ID, T, V])
    extends BaseProtocol[ID, T, V]

  class BaseProtocolSettings[ID, T <: AnyRef, V](val version: V, val domainObjectType: String)
                                                (implicit val classTag: ClassTag[T],
                                                 val stringToId: String => ID,
                                                 val httpMarshaller: HttpMarshalling,
                                                 val timeout: Timeout = 5 seconds)

  case class Id(id: String)

  case class ActionPrompt(name: String, method: String, params: List[String])

  case class ActionList(actions: List[ActionPrompt])

  object NoPayload

}
