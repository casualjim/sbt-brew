package io.backchat.sbtbrew

import sbt._

object CoffeeJadePlugin extends sbt.Plugin with ScriptEnginePlugin {

  object CoffeeJadeKeys {
    val viewsMapOutputFile = SettingKey[Option[File]]("view-map-output-file", "The single JS file containing all the Jade templates in a map")
    val coffeeJade = TaskKey[Seq[File]]("coffee-jade", "Compile the jade views with coffee jade")
  }


}
