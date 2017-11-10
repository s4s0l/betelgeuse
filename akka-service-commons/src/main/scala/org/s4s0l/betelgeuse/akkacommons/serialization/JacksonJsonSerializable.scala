/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */
//THEaboe is a lie!!!! taken from https://github.com/NextGenTel/akka-tools
package org.s4s0l.betelgeuse.akkacommons.serialization

trait JacksonJsonSerializable extends Serializable {}
trait JacksonJsonSerializableButNotDeserializable extends JacksonJsonSerializable {}
