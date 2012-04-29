package io.backchat.sbtbrew
package coffeescript

import sbt._

class Vanilla(log: Logger, bare: Boolean)
  extends CoffeeScriptCompiler(log, "coffeescript/vanilla/coffee-script.js", bare, Seq("coffee")) {

}
