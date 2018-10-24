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

package org.s4s0l.betelgeuse.akkaauth.manager.impl

import java.security.MessageDigest

import akka.Done
import akka.actor.Status.Failure
import akka.actor.{ActorRef, Props, Scheduler}
import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.{Config, ConfigFactory}
import org.s4s0l.betelgeuse.akkaauth.common.{PasswordCredentials, UserId}
import org.s4s0l.betelgeuse.akkaauth.manager.HashProvider.HashedValue
import org.s4s0l.betelgeuse.akkaauth.manager.ProviderExceptions.{PasswordAlreadyEnabled, PasswordNotEnabled, PasswordNotFound, PasswordValidationError}
import org.s4s0l.betelgeuse.akkaauth.manager.impl.PasswordManagerImpl.PasswordManagerCommand._
import org.s4s0l.betelgeuse.akkaauth.manager.impl.PasswordManagerImpl.Settings
import org.s4s0l.betelgeuse.akkaauth.manager.{HashProvider, PasswordManager}
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.{BgClusteringSharding, BgClusteringShardingExtension}
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.{BgPersistenceJournalRoach, BgPersistenceSnapStoreRoach}
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class PasswordManagerImplTest extends BgTestRoach with ScalaFutures {

  private val aService = testWith(
    new BgPersistenceJournalRoach
      with BgPersistenceSnapStoreRoach
      with BgClusteringSharding {
      override def customizeConfiguration: Config = {
        ConfigFactory.parseResources("auth-client.conf")
          .withFallback(ConfigFactory.parseResources("auth-provider.conf"))
          .withFallback(super.customizeConfiguration)
      }
    }
  )

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val scheduler: Scheduler = (new VirtualTime).scheduler

  object NoopHasher extends HashProvider {
    override def hashPassword(password: String): HashedValue = HashedValue(password.getBytes("UTF-8"), Array.empty)

    override def checkPassword(hash: HashedValue, password: String): Boolean = MessageDigest.isEqual(password.getBytes("UTF-8"), hash.hash)
  }

  feature("PersistentPasswordManager enables password holding") {
    scenario("standard password path") {
      new WithService(aService) {

        val manager: ActorRef = system.actorOf(Props(new PasswordManagerImpl(NoopHasher)), "manager")

        manager ! CreatePassword(UserId("id"), PasswordCredentials("login", "password"))
        testKit.expectMsg(10 seconds, Done)

        manager ! CreatePassword(UserId("id"), PasswordCredentials("login", "password"))
        testKit.expectMsgType[Failure](500 millis) // creating

        manager ! VerifyPassword(PasswordCredentials("login", "password"))
        testKit.expectMsgPF(500 millis, "not enabled") {
          case Failure(reason) => assert(reason.isInstanceOf[PasswordNotEnabled])
        }

        manager ! EnablePassword("login")
        testKit.expectMsg(500 millis, UserId("id"))

        manager ! VerifyPassword(PasswordCredentials("login", "password"))
        testKit.expectMsg(500 millis, UserId("id"))

        manager ! VerifyPassword(PasswordCredentials("login", "otherPassword"))
        testKit.expectMsgPF(500 millis, "not yet changed") {
          case Failure(reason) => assert(reason.isInstanceOf[PasswordValidationError])
        }

        manager ! UpdatePassword(PasswordCredentials("login", "otherPassword"))
        testKit.expectMsg(500 millis, Done)

        manager ! VerifyPassword(PasswordCredentials("login", "otherPassword"))
        testKit.expectMsg(500 millis, UserId("id"))

        manager ! RemovePassword("login")
        testKit.expectMsg(500 millis, Done)

        manager ! VerifyPassword(PasswordCredentials("login", "otherPassword"))
        testKit.expectMsgPF(500 millis, "removed") {
          case Failure(reason) => assert(reason.isInstanceOf[PasswordNotFound])
        }
      }
    }

    scenario("we want to use Protocol") {
      new WithService(aService) {
        implicit val sharding: BgClusteringShardingExtension = BgClusteringShardingExtension(system)
        val manager: PasswordManager = PasswordManagerImpl.startSharded(Settings(NoopHasher))

        implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 500.millis)

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "password")).failed) { ex =>
          assert(ex.isInstanceOf[PasswordNotFound])
        }

        whenReady(manager.createPassword(UserId("id"), PasswordCredentials("admin", "password"))) { rsp =>
          assert(rsp == Done)
        }

        whenReady(manager.updatePassword(PasswordCredentials("admin", "password2")).failed) { ex =>
          assert(ex.isInstanceOf[PasswordNotEnabled]) // we cannot update password when it was not enabled
        }

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "password")).failed) { ex =>
          assert(ex.isInstanceOf[PasswordNotEnabled])
        }

        whenReady(manager.enablePassword("admin")) { rsp =>
          assert(rsp == UserId("id"))
        }

        whenReady(manager.enablePassword("admin").failed) { ex =>
          assert(ex.isInstanceOf[PasswordAlreadyEnabled])
        }

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "password"))) { rsp =>
          assert(rsp == UserId("id"))
        }

        whenReady(manager.verifyPassword(PasswordCredentials("admin", "wrong")).failed) { ex =>
          assert(ex.isInstanceOf[PasswordValidationError])
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
