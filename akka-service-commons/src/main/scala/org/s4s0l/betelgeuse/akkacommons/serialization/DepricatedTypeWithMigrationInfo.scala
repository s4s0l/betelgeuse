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


//THEaboe is a lie!!!! taken from https://github.com/NextGenTel/akka-tools
package org.s4s0l.betelgeuse.akkacommons.serialization

/**
  * taken from https://github.com/NextGenTel/akka-tools
*
  * When you have an old type that you do not want your code to use any more,
  * you can use this trait to signalize to JacksonJsonSerializer that it can
  * retrieve the new representation of the class / object
  */
trait DepricatedTypeWithMigrationInfo {
  def convertToMigratedType(): AnyRef
}
