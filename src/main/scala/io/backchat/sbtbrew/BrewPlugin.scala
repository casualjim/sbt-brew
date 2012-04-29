package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset

object BrewPlugin extends sbt.Plugin {

  object BrewKeys {
    val sourceExtensions = SettingKey[Seq[String]]("source-extensions", "The extensions the engine has to look.")
    val targetExtension = SettingKey[String]("target-extension", "The extension this plugin compiles to.")
    val charset = SettingKey[Charset]("charset", "Sets the character encoding used in file IO. Defaults to utf-8")
    val brew = TaskKey[Seq[File]]("brew", "Compile and optimize script source files")
    val optimizer = SettingKey[Optimizer]("optimizer", "The optimizer to use")
  }
}
