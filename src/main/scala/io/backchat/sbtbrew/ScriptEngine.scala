package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset
import org.mozilla.javascript._
import io.backchat.brewsbt.RhinoUtils
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
  import util.control.Exception.catching
  import ScriptEngine._


  def compile(scriptToCompile: String): Either[String, String]


  protected def catchRhinoExceptions(fn: => Either[String, String]): Either[String, String] =
    catching(classOf[RhinoException]).withApply({
      case e: RhinoException => Left(RhinoUtils.createExceptionMessage(e))
      case e => Left(e.getMessage)
    })(fn)


  protected def createScope(ctx: Context) = {
    val ns = ctx.initStandardObjects()
    ctx.evaluateReader(
      ns,
      new InputStreamReader(getClass.getResourceAsStream(CommonsScriptResource), utf8), CommonsScriptName, 1, null)
    ns
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

}








