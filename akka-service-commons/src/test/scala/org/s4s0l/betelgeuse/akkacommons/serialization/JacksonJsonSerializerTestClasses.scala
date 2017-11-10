/*
 *  Copyright© 2017 by Marcin Wielgus - All Rights Reserved
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *  Written by Marcin Wielgus <mwielgus@outlook.com>, 2017-08-23 12:26
 *
 */

package org.s4s0l.betelgeuse.akkacommons.serialization

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
  * @author Marcin Wielgus
  */

case class Animal(name:String, age:Int, t:Cat) extends JacksonJsonSerializable

case class Cat(color:String, tail:Boolean)

case class OldType(s:String) extends DepricatedTypeWithMigrationInfo {
  override def convertToMigratedType(): AnyRef = NewType(s.toInt)
}
case class NewType(i:Int)


case class ObjectWrapperWithTypeInfo(@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@any_class") any:AnyRef)

case class ObjectWrapperWithoutTypeInfo(any:AnyRef)

case class ObjectWrapperWithoutTypeInfoOverrided(any:AnyRef) extends JacksonJsonSerializableButNotDeserializable