package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset
import org.mozilla.javascript._
import java.io.{IOException, InputStreamReader}
import tools.ToolErrorReporter
import util.control.Exception._

object ScriptEngine {
  val utf8 = Charset.forName("utf-8")
  val CommonsScriptName = "commons.js"
  val CommonsScriptResource = "/rhino/"+CommonsScriptName
  val ClientEnvScriptName = "env.rhino.js"
  val ClientEnvScriptResource = "/rhino/"+ClientEnvScriptName
  val JsonScriptName = "json2.min.js"
  val JsonScriptResource = "/rhino/"+JsonScriptName
}

trait ScriptEngine {
   import ScriptEngine._


  def compile(scriptToCompile: String): Either[String, String]


  protected def catchRhinoExceptions(fn: => Either[String, String]): Either[String, String] =
    catching(classOf[RhinoException]).withApply({
      case e: RhinoException => Left(RhinoUtils.createExceptionMessage(e))
      case e => Left(e.getMessage)
    })(fn)


  protected def createScope(ctx: Context, emulateShell: Boolean = false) = {
    val ns = if (emulateShell) ShellEmulation.emulate(ctx.initStandardObjects()) else ctx.initStandardObjects()
    ctx.evaluateReader(
      ns,
      new InputStreamReader(getClass.getResourceAsStream(CommonsScriptResource), utf8), CommonsScriptName, 1, null)
    ns
  }

  protected def addJsonScript(localScope: ScriptableObject) =
    loadScript(JsonScriptResource, JsonScriptName, localScope)

  protected def addClientEnvironment(localScope: ScriptableObject) =
    loadScript(ClientEnvScriptResource, ClientEnvScriptName, localScope)

  protected def loadScript(path: String, name: String, localScope: ScriptableObject = scope) = withContext { ctx =>
    ctx.evaluateReader(
      localScope,
          new InputStreamReader(getClass.getResourceAsStream(path), utf8), name, 1, null)
  }

  protected def evalString(script: String, params: (String, Any)*): String = evalString(script, "anonymous script", params:_*)

  protected def evalString(script: String, sourceName: String, params: (String, Any)*): String = withContext { ctx =>
    val localScope = ctx.newObject(scope)
    localScope.setParentScope(scope)
    params foreach {
      case (k, v) => localScope.put(k, localScope, v)
    }
    ctx.evaluateString(localScope, script, sourceName, 0, null).asInstanceOf[String]
  }

  def scope: ScriptableObject

  protected def withContext[T](f: Context => T): T =
    try {
      val ctx = Context.enter()
      // Do not compile to byte code (max 64kb methods)
      ctx.setOptimizationLevel(-1)
      ctx.setErrorReporter(new ToolErrorReporter(false))
      ctx.setLanguageVersion(Context.VERSION_1_8)

      f(ctx)
    } finally {
      Context.exit()
    }

  protected def addModuleDefinition(script: String) =
    "(function(define){\n"+
        "define(function(){return function(vars){\n" +
      "with(vars||{}) {\n" +
            "return " + script + "; \n"+
          "}};\n"+
        "})" +
      ";})(typeof define==\"function\"?\n"+
          "define:\n"+
          "function(factory){module.exports=factory.apply(this, deps.map(require));});\n"

}








