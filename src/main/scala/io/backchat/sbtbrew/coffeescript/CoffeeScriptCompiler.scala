package io.backchat.sbtbrew
package coffeescript

import sbt._
import java.nio.charset.Charset
import Keys._
import java.nio.charset.Charset
import org.mozilla.javascript._
import io.backchat.brewsbt.RhinoUtils
import java.io.{IOException, InputStreamReader}
import util.control.Exception._

object CoffeeScriptCompiler {
  val ScriptName = "coffee-script.min.js"
  val utf8 = Charset.forName("utf-8")
}
/**
 * A Scala / Rhino Coffeescript compiler.
 *
 * Adapted from https://github.com/softprops/coffeescripted-sbt/blob/master/src/main/scala/compiler.scala
 * Many thanks to softprops (doug)
 *
 * @author daggerrz
 * @author doug (to a lesser degree)
 * @author casualjim (to an even lesser degree)
 */
abstract class CoffeeScriptCompiler(log: Logger, libSrc: String, bare: Boolean, val extensions: Seq[String]) extends RhinoRunner(log, CoffeeScriptCompiler.utf8) with Compiler {


  /**compiler arguments in addition to `bare`*/
  def args: Map[String, Any] = Map.empty[String, Any]

  override def toString = "%s(%s)" format(getClass.getSimpleName, libSrc)

  /**
   * Compiles a string of Coffeescript code to Javascript.
   *
   * @param code the Coffeescript source code
   * @param bare whether the Coffeescript compiler should run in "bare" mode
   * @return Either a compilation error description or
   *         the compiled Javascript code
   */
  def compile(code: String): Either[String, String] =
    (catching(classOf[RhinoException])
      withApply ({
      case e: RhinoException => Left(RhinoUtils.createExceptionMessage(e))
    })) {
      Right(evaluate(
        "CoffeeScript.compile(%s, %s)".format(ScriptSource.toJSMultiLineString(code), jsArgs),
        "CoffeeScript.compile").toString)
    }

  override lazy val scope = {
    val sc = super.scope
    evaluateChain(resourceStream, CoffeeScriptCompiler.ScriptName)
    sc
  }

  private[this] def resourceStream = getClass.getResourceAsStream(libSrc)

  private[this] def jsArgs =
    ((List.empty[String] /: (Map("bare" -> bare) ++ args)) {
      (a, e) => e match {
        case (k, v) =>
          "%s:%s".format(k, v match {
            case s: String => "'%s'" format s
            case lit => lit
          }) :: a
      }
    }).mkString("({", ",", "});")

}
