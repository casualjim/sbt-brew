package io.backchat.sbtbrew

import java.io.InputStreamReader
import sbt._

object CoffeeJadeScriptEngine {
  val CoffeeJadeScriptName = "coffeejade.js"
  val CoffeeJadeScriptResource = "/coffeejade/"+CoffeeJadeScriptName
}
class CoffeeJadeScriptEngine(options: String, bare: Boolean, log: Logger) extends ScriptEngine {

  import ScriptEngine._
  import CoffeeJadeScriptEngine._

  private[this] val coffeeCompiler = Vanilla(bare, log)

  def compile(scriptToCompile: String) = {
    withContext { ctx =>
      val compileScope = ctx.newObject(scope)
      compileScope.setParentScope(scope)

      catchRhinoExceptions {
        val coffee = evalString(
          "window.jade.compile(jadeSource, opts).code;",
          "JCoffeeJadeCompiler",
          "jadeSource" -> scriptToCompile,
          "opts" -> options)
        coffeeCompiler.compile(coffee)
      }
    }
  }

  lazy val scope = withContext { ctx =>
    val ns = createScope(ctx)
    ctx.evaluateReader(
      ns,
      new InputStreamReader(getClass.getResourceAsStream("/coffeescript/vanilla/coffee-script.js"), utf8), "coffee-script", 1, null)
    ctx.evaluateString(ns, "var window = {};", "JCoffeeJadeCompiler", 0, null)
    ctx.evaluateReader(
      ns,
      new InputStreamReader(getClass.getResourceAsStream(CoffeeJadeScriptResource), utf8),
      CoffeeJadeScriptName, 1, null)
    ns
  }
}
