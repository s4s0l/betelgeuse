package org.s4s0l.betelgeuse.akkacommons.persistence.crate

import java.util.Date

import org.s4s0l.betelgeuse.akkacommons.persistence.crate.CrateScalikeJdbcImports.CrateDbObjectMapper
import org.s4s0l.betelgeuse.utils.AllUtils
import org.scalatest.FeatureSpec

/**
  * @author Marcin Wielgus
  */
class CrateDbObjectIntrospectorTest extends FeatureSpec {

  feature("All-utils finding companion objects is usable for crate db objct mappers") {

    scenario("Lookup companion object implementation from object") {
      val theObject = new O(new Date())
      assert(AllUtils.findCompanionForObject[O, CrateDbObjectMapper[O]](theObject) == O)
    }

    scenario("Lookup companion object implementation from class") {
      assert(AllUtils.findCompanionForClass[CrateDbObjectMapper[O]](classOf[O]) == O)
    }

    scenario("Lookup companion object should not fail when companion does not implement given class") {
      assert(AllUtils.findCompanionForClassOption[CrateDbObjectMapper[MappingTable]](classOf[MappingTable]).isEmpty)
    }

    scenario("Lookup companion object should not fail when companion does not have companion object") {
      assert(AllUtils.findCompanionForClassOption[CrateDbObjectMapper[CrateDbObjectIntrospectorTest]](classOf[CrateDbObjectIntrospectorTest]).isEmpty)
    }

  }


}
