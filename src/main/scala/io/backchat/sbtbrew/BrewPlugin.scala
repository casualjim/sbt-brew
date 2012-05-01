package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset

object BrewPlugin extends sbt.Plugin {

  object BrewKeys {
    val sourceExtensions = SettingKey[Seq[String]]("source-extensions", "The extensions the engine has to look.")
    val targetExtension = SettingKey[String]("target-extension", "The extension this plugin compiles to.")
    
    val charset = SettingKey[Charset]("charset", "Sets the character encoding used in file IO. Defaults to utf-8.")
    
    val brew = TaskKey[Seq[File]]("brew", "Compile and optimize script source files.")
    val coffee = TaskKey[Seq[File]]("coffee", "Compile coffee sources.")
    val coffeeJade = TaskKey[Seq[File]]("coffee-jade", "Compile the jade views with coffee jade")
    val optimize = TaskKey[Seq[File]]("optimize", "Optimize the web resources.")
    val optimizer = SettingKey[Optimizer]("optimizer", "The optimizer to use.")
    val engineContext = SettingKey[ScriptEngineContext]("engine-context", "The configuration for this script engine to compile files.")
    
    val bare = SettingKey[Boolean]("bare", "Compile coffee sources without top-level function wrapper.")
    val iced = SettingKey[Boolean]("iced", """When true, The coffee task will compile vanilla CoffeeScript and "Iced" CoffeeScript sources.""")
    
    val viewsFile = SettingKey[Option[File]]("views-file", "The single JS file containing all the Jade templates in a map")
    val jadeOptions = SettingKey[String]("jade-options", "Compiler options for the jade compiler.")
  }
}
