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

import akka.Done
import org.s4s0l.betelgeuse.akkaauth.common.UserAttributes.{City, Male}
import org.s4s0l.betelgeuse.akkaauth.common._
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager
import org.s4s0l.betelgeuse.akkaauth.manager.UserManager.{Role, UserDetailedAttributes, UserDetailedInfo}
import org.s4s0l.betelgeuse.akkacommons.clustering.sharding.BgClusteringSharding
import org.s4s0l.betelgeuse.akkacommons.persistence.roach.BgPersistenceJournalRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestRoach
import org.s4s0l.betelgeuse.akkacommons.test.BgTestService.WithService
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

/**
  * @author Marcin Wielgus
  */
class UserManagerImplTest extends BgTestRoach with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.second, 300.millis)

  private val aService = testWith(new BgPersistenceJournalRoach with BgClusteringSharding {
    lazy val userManager: UserManager = UserManagerImpl.start

    override protected def initialize(): Unit = {
      super.initialize()
      userManager
    }
  })

  def uda() =
    UserAttributes(
      given_name = Some("NAME"),
      city = Some(City("New York")),
      gender = Some(Male())
    )


  feature("User manager can persist user information") {
    scenario("Smoke test") {
      val userId = whenReady(aService.service.userManager
        .generateUserId()(aService.execContext)) { id =>
        assert(id.id.length > 16)
        id
      }
      val udi = UserDetailedInfo(
        userId = userId,
        attributes = UserDetailedAttributes(
          userAttributed = uda(),
          roles = Set(Role("role1")),
          additionalAttributes = Map("some" -> "attribute")
        ),
        login = Some("login"),
        locked = false
      )
      new WithService(aService) {
        whenReady(service.userManager.createUser(udi)) { done =>
          assert(done == Done)
        }
        whenReady(service.userManager.createUser(udi).failed) { ex =>
          assert(ex.getMessage.contains("duplicate"))
        }
        whenReady(service.userManager.getUser(userId)) { user =>
          assert(user == udi)
        }
        whenReady(service.userManager.lockUser(userId)
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi.copy(locked = true))
        }
        whenReady(service.userManager.lockUser(userId)
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi.copy(locked = true))
        }
        whenReady(service.userManager.unLockUser(userId)
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi.copy(locked = false))
        }
        whenReady(service.userManager.unLockUser(userId)
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi.copy(locked = false))
        }
        whenReady(service.userManager.updateRoles(userId, Set())
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi.copy(attributes = udi.attributes.copy(roles = Set())))
        }
        whenReady(service.userManager.updateRoles(userId, Set(Role("new Role"), Role("new Role1")))
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi.copy(attributes = udi.attributes.copy(roles = Set(Role("new Role"), Role("new Role1")))))
        }
        whenReady(service.userManager.updateRoles(userId, udi.attributes.roles)
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi)
        }
        whenReady(service.userManager.updateAdditionalAttributes(userId, Map("some" -> None, "some1" -> Some("present")))
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi.copy(attributes = udi.attributes.copy(additionalAttributes = Map("some1" -> "present"))))
        }
        whenReady(service.userManager.updateAdditionalAttributes(userId, Map("some1" -> None, "some" -> Some("attribute2")))
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi.copy(attributes = udi.attributes.copy(additionalAttributes = Map("some" -> "attribute2"))))
        }
        whenReady(service.userManager.updateAdditionalAttributes(userId, Map("some" -> Some("attribute")))
          .flatMap(_ => service.userManager.getUser(userId))) { user =>
          assert(user == udi)
        }
      }
      aService.restartService()
      new WithService(aService) {
        whenReady(service.userManager.getUser(userId)) { user =>
          assert(user == udi)
        }
      }
    }
  }
}


















