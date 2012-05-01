package io.backchat.sbtbrew

import java.io.InputStreamReader
import sbt._

object HamlScriptEngine {
  val HamlScriptName = "haml.js"
  val HamlScriptResource = "/haml/"+HamlScriptName
}
class HamlScriptEngine(log: Logger) extends ScriptEngine {

  import ScriptEngine._
  import HamlScriptEngine._


  def compile(scriptToCompile: String) = {
    withContext { ctx =>
      catchRhinoExceptions {
        Right(addModuleDefinition(
          evalString("Haml.optimize(Haml.compile(hamlSource));","HamlCompiler", "hamlSource" -> scriptToCompile)))
      }
    }
  }

  lazy val scope = withContext { ctx =>
    val ns = createScope(ctx, true)
    addJsonScript(ns)
    ctx.evaluateReader(
      ns,
      new InputStreamReader(getClass.getResourceAsStream(HamlScriptResource), utf8),
      HamlScriptName, 1, null)
    ns
  }
}
