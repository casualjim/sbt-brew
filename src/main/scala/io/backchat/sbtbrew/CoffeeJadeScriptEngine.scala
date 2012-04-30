package io.backchat.sbtbrew

import java.io.InputStreamReader
import sbt._

object CoffeeJadeScriptEngine {
  val CoffeeJadeScriptName = "coffeejade.js"
  val CoffeeJadeScriptResource = "/coffeejade/"+CoffeeJadeScriptName
}
class CoffeeJadeScriptEngine(options: String, iced: Boolean, log: Logger) extends ScriptEngine {

  import ScriptEngine._
  import CoffeeJadeScriptEngine._

  private val coffeeCompiler = if (iced) Iced(false, log) else Vanilla(false, log)

  def compile(scriptToCompile: String) = {
    withContext { ctx =>
      val compileScope = ctx.newObject(scope)
      compileScope.setParentScope(scope)
      compileScope.put("jadeSource", compileScope, scriptToCompile)
      catchRhinoExceptions {
        val script = "window.jade.compile(jadeSource, %s).code;" format options
        val coffee = ctx.evaluateString(compileScope, script, "JCoffeeJadeCompiler", 0, null).toString
        coffeeCompiler.compile(coffee)
      }
    }
  }

  lazy val scope = withContext { ctx =>
    val ns = createScope(ctx)
    ctx.evaluateString(ns, "var window = {};", "JCoffeeJadeCompiler", 0, null)
    ctx.evaluateReader(
      ns,
      new InputStreamReader(getClass.getResourceAsStream(CoffeeJadeScriptResource), utf8),
      CoffeeJadeScriptName, 1, null)
    ns
  }
}
