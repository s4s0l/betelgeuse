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



package org.s4s0l.betelgeuse.akkacommons.http.stomp

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}

/**
  * @author Marcin Wielgus
  */
class BgHttpStompExtension(private val system: ExtendedActorSystem) extends Extension {

}


object BgHttpStompExtension extends ExtensionId[BgHttpStompExtension] with ExtensionIdProvider {


  override def get(system: ActorSystem): BgHttpStompExtension = system.extension(this)

  override def apply(system: ActorSystem): BgHttpStompExtension = system.extension(this)

  override def lookup(): BgHttpStompExtension.type = BgHttpStompExtension

  override def createExtension(system: ExtendedActorSystem): BgHttpStompExtension =
    new BgHttpStompExtension(system)

}