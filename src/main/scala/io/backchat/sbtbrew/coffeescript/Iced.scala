package io.backchat.sbtbrew
package coffeescript

import sbt._

class Iced(log: Logger, bare: Boolean)
  extends CoffeeScriptCompiler(log, "coffeescript/iced/coffee-script.js", bare, Seq("coffee", "iced")) {

  override val args = Map("runtime" -> "inline")

  //  def createSource(graph: ScriptGraph, file: File): ScriptSource = new CoffeeSource(graph, file, this)
}
