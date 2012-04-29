package io.backchat.sbtbrew

import sbt.Logger
import java.io.InputStreamReader
import net.liftweb.json.{DefaultFormats, Serialization}
import org.mozilla.javascript._

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
abstract class CoffeeScriptEngine(src: String, bare: Boolean, log: Logger) extends ScriptEngine {
  import ScriptEngine._
  /** compiler arguments in addition to `bare` */
  def args: Map[String, Any] = Map.empty[String, Any]

  override def toString = "%s(%s)" format(getClass.getSimpleName, src)

  /**
   * Compiles a string of Coffeescript code to Javascript.
   *
   * @param code the Coffeescript source code
   * @param bare whether the Coffeescript compiler should run in "bare" mode
   * @return Either a compilation error description or
   *   the compiled Javascript code
   */
  def compile(code: String): Either[String, String] = {
    withContext { ctx =>
      catchRhinoExceptions {
        val coffee = scope.get("CoffeeScript", scope).asInstanceOf[NativeObject]
        val compileFunc = coffee.get("compile", scope).asInstanceOf[Function]
        val opts = ctx.evaluateString(scope, jsArgs(bare), null, 1, null)
        Right(compileFunc.call(
          ctx, scope, coffee, Array(code, opts)).asInstanceOf[String])
      }
    }

  }

  lazy val scope = withContext { ctx =>
    val scope = createScope(ctx)
    ctx.evaluateReader(
      scope,
      new InputStreamReader(getClass.getResourceAsStream("/%s" format src), utf8), src, 1, null)
    scope
  }

  private def jsArgs(bare: Boolean) = "("+Serialization.write(Map("bare" -> bare) ++ args)(DefaultFormats)+")"
}

object Vanilla {
  def apply(bare: Boolean, log: Logger): Vanilla = new Vanilla(bare, log)
}
class Vanilla(bare: Boolean, log: Logger) extends CoffeeScriptEngine("coffeescript/vanilla/coffee-script.js", bare, log)

object Iced {
  def apply(bare: Boolean, log: Logger): Iced = new Iced(bare, log)
}

class Iced(bare: Boolean, log: Logger) extends CoffeeScriptEngine("coffeescript/iced/coffee-script.js", bare, log) {
  override def args = Map("runtime" -> "inline")
}

