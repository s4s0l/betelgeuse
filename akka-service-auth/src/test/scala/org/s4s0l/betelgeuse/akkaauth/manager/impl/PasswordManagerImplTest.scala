package org.s4s0l.betelgeuse.akkaauth.manager.impl

import akka.Done
import akka.actor.Status.Failure
import akka.actor.{ActorRef, Props, Scheduler}
import com.miguno.akka.testing.VirtualTime
import org.s4s0l.betelgeuse.akkaauth.common.{PasswordCredentials, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.{HashProvider, PasswordManager}
import org.s4s0l.betelgeuse.akkaauth.manager.impl.PasswordManagerImpl.PasswordManagerCommand._
import org.s4s0l.betelgeuse.akkaauth.manager.impl.PasswordManagerImpl.Settings
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.{BgClusteringSharding, BgClusteringShardingExtension}
import org.s4s0l.betelgeuse.akkacommons.persistence.BgPersistenceExtension
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.{BgPersistenceJournalRoach, BgPersistenceSnapStoreRoach}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class PasswordManagerImplTest extends BgTestRoach with  ScalaFutures {

  private val aService = testWith(new
      BgPersistenceJournalRoach
        with BgPersistenceSnapStoreRoach
        with BgClusteringSharding {
  })

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val scheduler: Scheduler = (new VirtualTime).scheduler

  object NoopHasher extends HashProvider {
    override def hashPassword(password: String): String = password

    override def checkPassword(hash: String, password: String): Boolean = hash == password
  }

  feature("PersistentPasswordManager enables password holding") {
    scenario("standard password path") {
      new WithService(aService) {
        private val extension = BgPersistenceExtension.apply(system)

        val manager: ActorRef = system.actorOf(Props(new PasswordManagerImpl(NoopHasher)), "manager")

        manager ! CreatePassword(UserId("id"), PasswordCredentials("login", "password"))
        testKit.expectMsg(2 seconds, Done)

        manager ! CreatePassword(UserId("id"), PasswordCredentials("login", "password"))
        testKit.expectMsgType[Failure](500 millis) // creating

        manager ! VerifyPassword(PasswordCredentials("login", "password"))
        testKit.expectMsgPF(500 millis, "not enabled") {
          case Failure(reason) => assert(reason.getMessage.contains("enabled"))
        }

        manager ! EnablePassword("login")
        testKit.expectMsg(500 millis, UserId("id"))

        manager ! VerifyPassword(PasswordCredentials("login", "password"))
        testKit.expectMsg(500 millis, UserId("id"))

        manager ! VerifyPassword(PasswordCredentials("login", "otherPassword"))
        testKit.expectMsgPF(500 millis, "not yet changed") {
          case Failure(reason) => assert(reason.getMessage.contains("Invalid password"))
        }

        manager ! UpdatePassword(PasswordCredentials("login", "otherPassword"))
        testKit.expectMsg(500 millis, Done)

        manager ! VerifyPassword(PasswordCredentials("login", "otherPassword"))
        testKit.expectMsg(500 millis, UserId("id"))

        manager ! RemovePassword("login")
        testKit.expectMsg(500 millis, Done)

        manager ! VerifyPassword(PasswordCredentials("login", "otherPassword"))
        testKit.expectMsgPF(500 millis, "removed") {
          case Failure(reason) => assert(reason.getMessage.contains("does not exist"))
        }
      }
    }

    scenario("we want to use Protocol") {
      new WithService(aService) {
        implicit val sharding: BgClusteringShardingExtension = BgClusteringShardingExtension(system)
        val manager: PasswordManager = PasswordManagerImpl.startSharded(Settings(NoopHasher, 5 seconds))

        implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 500.millis)

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "password")).failed) { ex =>
          assert(ex.getMessage contains "does not exist")
        }

        whenReady(manager.createPassword(UserId("id"),PasswordCredentials("admin", "password"))) { rsp =>
          assert(rsp == Done)
        }

        whenReady(manager.updatePassword(PasswordCredentials("admin","password2")).failed) { ex =>
          assert(ex.getMessage contains "not enabled") // we cannot update password when it was not enabled
        }

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "password")).failed) { ex =>
          assert(ex.getMessage contains "not enabled")
        }

        whenReady(manager.enablePassword("admin")) { rsp =>
          assert(rsp == UserId("id"))
        }

        whenReady(manager.enablePassword("admin").failed) { ex =>
          assert(ex.getMessage contains "Already enabled")
        }

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "password"))) { rsp =>
          assert(rsp == UserId("id"))
        }

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "wrong")).failed) { ex =>
          assert(ex.getMessage contains "Invalid password")
        }

        whenReady(manager.updatePassword(PasswordCredentials("admin", "new_password"))) { rsp =>
          assert(rsp == Done)
        }

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "new_password"))) { rsp =>
          assert(rsp == UserId("id"))
        }

        whenReady(manager.removePassword("admin")) { res =>
          assert(res == Done)
        }
      }

    }
  }
}
