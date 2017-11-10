package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import akka.actor.ActorRef
import akka.persistence.{PersistentActor, SnapshotOffer}
import org.s4s0l.betelgeuse.akkacommons.persistence.utils
import org.s4s0l.betelgeuse.akkacommons.utils.TimeoutShardedActor

import scala.language.postfixOps

/**
  * @author Marcin Wielgus
  */

case class Cmd(data: String)

case class Evt(data: String)

case class ExampleState(events: List[String] = Nil) {

  def updated(evt: Evt): ExampleState = copy(evt.data :: events)

  def size: Int = events.length

  override def toString: String = events.reverse.toString
}

class ExamplePersistentActor(num: Int) extends PersistentActor {

  override def preStart(): Unit = {
    LOGGER.info(s"Pre starting $num")
  }


  override def postStop(): Unit = {
    LOGGER.info(s"Post stopping $num")
    super.postStop()
  }

  override def persistenceId = s"example/$num"

  var state = ExampleState()

  def updateState(event: Evt): Unit =
    state = state.updated(event)

  def numEvents: Int =
    state.size

  val receiveRecover: Receive = {
    case evt: Evt => updateState(evt)
    case SnapshotOffer(_, snapshot: ExampleState) => state = snapshot
  }

  val snapShotInterval = 1000
  private lazy val LOGGER: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(getClass)
  val receiveCommand: Receive = {
    case Cmd(data) =>
      val sndr = sender()
      LOGGER.info("received")
      persist(Evt(s"$data-$numEvents")) { event =>
        LOGGER.info("persisted")
        updateState(event)
        context.system.eventStream.publish(event)
        sndr ! (lastSequenceNr, state.events.reverse)
        if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
          saveSnapshot(state)
      }
  }

}

case class CmdSharded(num: Int, data: String)

case class EvtSharded(num: Int, data: String)


class ExamplePersistentShardedActor extends utils.PersistentShardedActor with TimeoutShardedActor {

  import scala.concurrent.duration._

  override val timeoutTime: FiniteDuration = 10 seconds
  private var dataReceived = List[String]()


  override def onPassivationCallback(): Unit = {
    lastSender ! "down"
  }

  override def receiveRecover:Receive = {
    case EvtSharded(_, s) =>
      dataReceived = s :: dataReceived
  }

  private var lastSender: ActorRef = _

  override def receiveCommand:Receive = {
    case CmdSharded(i, data) =>
      lastSender = sender()
      persist(EvtSharded(i, data)) { _ =>
        dataReceived = data :: dataReceived
        lastSender ! dataReceived.reverse
      }
  }



}
