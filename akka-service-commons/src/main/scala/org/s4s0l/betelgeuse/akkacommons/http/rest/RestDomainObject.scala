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

/*
 * Copyright© 2017 by Ravenetics Sp. z o.o. - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * This file is proprietary and confidential.
 */

package org.s4s0l.betelgeuse.akkacommons.http.rest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshallable}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher1, _}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.util.Timeout
import org.s4s0l.betelgeuse.akkacommons.patterns.message.MessageHeaders.Headers
import org.s4s0l.betelgeuse.akkacommons.serialization.HttpMarshalling
import org.s4s0l.betelgeuse.akkacommons.utils.QA
import org.s4s0l.betelgeuse.akkacommons.utils.QA._
import org.slf4j.{Logger, LoggerFactory}

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
  def log: Logger = LoggerFactory.getLogger(RestDomainObject.getClass)
  sealed trait RestCommand extends UuidQuestion {

    override lazy val messageId: Uuid = headers.get("messageId").getOrElse(QA.uuid)

    def headers: Headers

  }

  //todo: should contain headers so that actions could send back something more than payload
  sealed trait RestCommandResult[T] extends Result[Uuid, T] {
    val httpStatusCode: StatusCode
  }

  sealed trait ActionDesc {
    def name: String

    def params: Set[String]

    def method: HttpMethod
  }


  trait ProtocolWithUpdates[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V]
    with Gets[ID, T, V]
    with Deletes[ID, T, V]
    with Updates[ID, T, V]
    with Creates[ID, T, V]

  trait ProtocolWithImmutable[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V]
    with Gets[ID, T, V]
    with Creates[ID, T, V]


  trait RestProtocol {

    private[rest] val defaultPassedHeaders: Set[String] = Set("messageId")

    def createRoute(implicit executionContext: ExecutionContext, sender: ActorRef, httpMarshaller: HttpMarshalling, timeout: Timeout = 5 seconds): Route = reject

    private[rest] def withRecovery[X](correlationId: Uuid): PartialFunction[Throwable, RestCommandResult[X]] = {
      case ex: Throwable => RestCommandNotOk[X](ex, correlationId)
    }

    private[rest] def toResponseMarshallAble[X <: AnyRef](value: X)(implicit context: RestProtocolContext): ToResponseMarshallable = ToResponseMarshallable(value)(toEntityMarshaller[X])

    private[rest] def completeWithPayload[X <: AnyRef](implicit context: RestProtocolContext): PartialFunction[Try[RestCommandResult[X]], Route] = {
      case Success(RestCommandOk(value, correlationId, httpStatusCode)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          if (value == NoPayload)
            complete(httpStatusCode -> HttpEntity.Empty)
          else {
            implicit val toEntity: ToEntityMarshaller[X] = toEntityMarshaller[X]
            complete(httpStatusCode -> value)
          }
        }
      case Success(notOk@RestCommandNotOk(_, correlationId, _)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          failureRoute(notOk)
        }
      case Failure(ex) => failWith(ex)
    }

    private[rest] def completeWithId(implicit context: RestProtocolContext): PartialFunction[Try[RestCommandResult[_]], Route] = {
      case Success(RestCommandOk(value, correlationId, httpStatusCode)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          implicit val toEntity: ToEntityMarshaller[Id] = toEntityMarshaller[Id]
          complete(httpStatusCode -> Id(value.toString))
        }
      case Success(notOk@RestCommandNotOk(_, correlationId, _)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          failureRoute[Id](notOk.asInstanceOf[RestCommandNotOk[Id]])
        }
      case Failure(ex) => failWith(ex)
    }

    private[rest] def failureRoute[X <: AnyRef](notOk: RestCommandNotOk[X])
                                               (implicit context: RestProtocolContext): StandardRoute = {
      failureRouteException(notOk.httpStatusCode, notOk.ex)
    }

    private[rest] def failureRouteException(statusCode: StatusCode, ex: Throwable)
                                           (implicit context: RestProtocolContext): StandardRoute = {
      implicit val toEntity: ToEntityMarshaller[FailureDto] = toEntityMarshaller[FailureDto]
      log.error("Rest handler got exception ", ex)
      complete(statusCode -> FailureDto(ex.getMessage))
    }

    private[rest] def toEntityMarshaller[X <: AnyRef](implicit context: RestProtocolContext): ToEntityMarshaller[X] = context.httpMarshaller.marshaller[X]

    private[rest] def withHeaders(acceptedHeaders: Set[String] = Set.empty)(implicit context: RestProtocolContext): Directive1[Headers] = Directives
      .extract(_.request.headers
        .filter(it => defaultPassedHeaders.contains(it.name()) || acceptedHeaders.contains(it.name()))
        .map(it => it.name() -> it.value())
        .toMap
      )

    private[rest] def completeWithNoPayload[X <: AnyRef](implicit context: RestProtocolContext): PartialFunction[Try[RestCommandResult[_]], Route] = {
      case Success(RestCommandOk(_, correlationId, httpStatusCode)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          complete(httpStatusCode, HttpEntity.Empty)
        }
      case Success(notOk@RestCommandNotOk(_, correlationId, _)) =>
        respondWithHeader(headers.RawHeader("correlationId", correlationId)) {
          failureRoute(notOk.asInstanceOf[RestCommandNotOk[X]])
        }
      case Failure(ex) => failWith(ex)
    }
  }

  trait VersionedRestProtocol[V] extends RestProtocol {

    def version: V

    override def createRoute(implicit executionContext: ExecutionContext, sender: ActorRef, httpMarshaller: HttpMarshalling, timeout: Timeout = 5 seconds): Route = {
      super.createRoute ~ pathPrefix(version.toString) {
        createVersionedRoute(new RestProtocolContext()(executionContext, sender, httpMarshaller, timeout))
      }
    }

    private[rest] def createVersionedRoute(implicit context: RestProtocolContext): Route = reject

  }

  /**
    *
    * @tparam ID typed ID of an object
    * @tparam T  type of an object
    * @tparam V  typed version, using some customizable type so it can be safely case matched
    */
  trait DomainObjectProtocol[ID, T <: AnyRef, V] extends VersionedRestProtocol[V] {

    protected def domainObjectSettings: DomainObjectSettings[ID, T, V]

    protected def domainObjectType: String

    private[rest] implicit def fromEntityMarshaller(implicit context: RestProtocolContext): FromEntityUnmarshaller[T] = context.httpMarshaller.unmarshaller[T](domainObjectSettings.classTag)

    private[rest] def withIdHeaders(idMatcher: PathMatcher1[String] = Segment, acceptedHeaders: Set[String] = Set.empty)(implicit context: RestProtocolContext): Directive[(ID, Headers)] = {
      path(idMatcher).map(domainObjectSettings.stringToId) & withHeaders(acceptedHeaders)
    }

    private[rest] override def createVersionedRoute(implicit context: RestProtocolContext): Route = {
      super.createVersionedRoute ~
        pathPrefix("objects" / domainObjectType) {
          handleExceptions(exceptionHandler) {
            createObjectRoute
          }
        }
    }

    private[rest] def exceptionHandler(implicit context: RestProtocolContext): PartialFunction[Throwable, Route] = {
      case ex: Throwable =>
        failureRouteException(StatusCodes.InternalServerError, ex)
    }

    private[rest] def createObjectRoute(implicit context: RestProtocolContext): Route = reject

  }

  trait Gets[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {


    def get(msg: Get[ID, V])(implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[T]]

    def list(msg: GetList[V])(implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[List[ID]]]

    private[rest] override def createObjectRoute(implicit context: RestProtocolContext): Route = {
      super.createObjectRoute ~
        listDirective ~
        getDirective
    }

    protected def getDirective(implicit context: RestProtocolContext): Route = {
      implicit val ec: ExecutionContext = context.executionContext
      implicit val ac: ActorRef = context.sender
      implicit val to: Timeout = context.timeout
      Directives.get {
        withIdHeaders()(context) { (id: ID, headers: Headers) =>
          val msg = Get(version, id, headers)
          onComplete(get(msg).recover(withRecovery(msg.messageId)))(completeWithPayload)
        }
      }
    }

    protected def listDirective(implicit context: RestProtocolContext): Route = {
      implicit val ec: ExecutionContext = context.executionContext
      implicit val ac: ActorRef = context.sender
      implicit val to: Timeout = context.timeout
      Directives.get {
        pathEnd {
          withHeaders()(context) { headers =>
            val msg = GetList(version, headers)
            onComplete(list(msg).recover(withRecovery(msg.messageId)))(completeWithPayload)
          }
        }
      }
    }
  }

  trait Deletes[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {

    def delete(msg: Delete[ID, V])(implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[ID]]

    private[rest] override def createObjectRoute(implicit context: RestProtocolContext): Route = {
      super.createObjectRoute ~
        deleteDirective
    }

    protected def deleteDirective(implicit context: RestProtocolContext): Route = {
      implicit val ec: ExecutionContext = context.executionContext
      implicit val ac: ActorRef = context.sender
      implicit val to: Timeout = context.timeout
      Directives.delete {
        withIdHeaders()(context) { (id, headers) =>
          val msg = Delete(version, id, headers)
          onComplete(delete(msg).recover(withRecovery(msg.messageId)))(completeWithNoPayload)
        }
      }
    }

  }

  trait Updates[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {

    def update(msg: Update[ID, T, V])(implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[ID]]

    private[rest] override def createObjectRoute(implicit context: RestProtocolContext): Route = {
      super.createObjectRoute ~
        updateDirective
    }

    protected def updateDirective(implicit context: RestProtocolContext): Route = {
      implicit val ec: ExecutionContext = context.executionContext
      implicit val ac: ActorRef = context.sender
      implicit val to: Timeout = context.timeout

      Directives.put {
        withIdHeaders()(context) { (id, headers) =>
          entity(as[T]) { e =>
            val msg = Update(version, id, e, headers)
            onComplete(update(msg).recover(withRecovery(msg.messageId)))(completeWithNoPayload)
          }
        }

      }
    }

  }

  trait Creates[ID, T <: AnyRef, V] extends DomainObjectProtocol[ID, T, V] {

    def generateId: ID

    def create(msg: Create[ID, T, V])(implicit executionContext: ExecutionContext, sender: ActorRef, timeout: Timeout): Future[RestCommandResult[ID]]

    private[rest] override def createObjectRoute(implicit context: RestProtocolContext): Route = {
      super.createObjectRoute ~
        createDirective
    }

    protected def createDirective(implicit context: RestProtocolContext): Route = {
      implicit val ec: ExecutionContext = context.executionContext
      implicit val ac: ActorRef = context.sender
      implicit val to: Timeout = context.timeout

      Directives.post {
        pathEnd {
          withHeaders()(context) { headers =>
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

    type ActionType = (Action[ID, V], RestProtocolContext) => Future[RestCommandResult[_]]

    private[rest] override def createObjectRoute(implicit context: RestProtocolContext): Route = {
      super.createObjectRoute ~
        actionsDirective
    }

    protected def actionsDirective(implicit context: RestProtocolContext): Route = {
      implicit val ec: ExecutionContext = context.executionContext
      implicit val ac: ActorRef = context.sender
      implicit val to: Timeout = context.timeout

      val actionList = path(Segment / "actions") { _ =>
        complete(ToResponseMarshallable(ActionList(actions.keys.map(e => ActionPrompt(e.name, e.method.value, e.params.toList)).toList))(toEntityMarshaller[ActionList]))
      }

      val prompts = actions.foldLeft(actionList: Route) { (c: Route, e) =>
        c ~
          path(Segment / "actions" / e._1.name) { _ =>
            complete(ToResponseMarshallable(ActionPrompt(e._1.name, e._1.method.value, e._1.params.toList))(toEntityMarshaller[ActionPrompt]))
          }
      }

      val actionDirectives = actions.map { e =>
        e._1 ->
          withIdHeaders(Segment / "actions" / e._1.name / "invoke", e._1.params)(context) { (id: ID, headers: Headers) =>
            //todo make it accept params in request body also, as simple map
            parameterMap { paramMap =>
              val params = paramMap ++ headers.filter(it => e._1.params.contains(it._1))
              val missing = e._1.params.filter(it => !params.contains(it) || params(it).isEmpty)
              val msg = Action(version, id, e._1.name, params, headers)
              if (missing.isEmpty) {
                onComplete(e._2(msg, context).recover(withRecovery(msg.messageId)).asInstanceOf[Future[RestCommandResult[AnyRef]]])(completeWithPayload)
              } else {
                failureRoute(RestCommandNotOk(new Exception(s"Missing action parameters: ${missing.mkString(", ")}"), msg.messageId, StatusCodes.BadRequest))
              }
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

    //TODO use maybe directive api here to parse params?
    def actions: Map[ActionDesc, ActionType] = Map()
  }


  case class Get[ID, V](version: V, id: ID, headers: Headers) extends RestCommand

  case class GetList[V](version: V, headers: Headers) extends RestCommand

  case class Delete[ID, V](version: V, id: ID, headers: Headers) extends RestCommand

  case class Create[ID, T, V](version: V, id: ID, value: T, headers: Headers) extends RestCommand

  case class Update[ID, T, V](version: V, id: ID, value: T, headers: Headers) extends RestCommand

  case class Action[ID, V](version: V, id: ID, actionName: String, params: Map[String, String], headers: Headers) extends RestCommand

  case class RestCommandOk[T](value: T, correlationId: Uuid, httpStatusCode: StatusCode = StatusCodes.OK) extends RestCommandResult[T] with OkResult[Uuid, T]

  case class RestCommandNotOk[T](ex: Throwable, correlationId: Uuid, httpStatusCode: StatusCode = StatusCodes.InternalServerError) extends RestCommandResult[T] with NotOkResult[Uuid, T]

  case class Idempotent(name: String, params: Set[String] = Set()) extends ActionDesc {
    override def method: HttpMethod = HttpMethods.PUT
  }

  case class NonIdempotent(name: String, params: Set[String] = Set()) extends ActionDesc {
    override def method: HttpMethod = HttpMethods.POST
  }

  case class Query(name: String, params: Set[String] = Set()) extends ActionDesc {
    override def method: HttpMethod = HttpMethods.GET
  }


  class RestProtocolContext private[rest](implicit val executionContext: ExecutionContext,
                                          val sender: ActorRef,
                                          val httpMarshaller: HttpMarshalling,
                                          val timeout: Timeout = 5 seconds)

  class DomainObjectSettings[ID, T <: AnyRef, V](implicit val classTag: ClassTag[T],
                                                 val stringToId: String => ID)

  case class Id(id: String)

  case class FailureDto(error: String)

  case class ActionPrompt(name: String, method: String, params: List[String])

  case class ActionList(actions: List[ActionPrompt])

  object NoPayload

}
