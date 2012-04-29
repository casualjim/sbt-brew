package io.backchat.sbtbrew

import sbt._
import org.mozilla.javascript.tools.ToolErrorReporter
import java.io._
import java.nio.charset.Charset
import io.backchat.brewsbt.RhinoUtils
import org.mozilla.javascript.{RhinoException, JavaScriptException, Context, ScriptableObject}


object RhinoRunner {
  private val ClientScriptEnv = "/rhino/env.rhino.js"
  private val CommonsScript = "/rhino/commons.js"
  private val JsonScript = "/rhino/json2.min.js"
}

class RhinoRunner(logger: Logger, charset: Charset, initialScope: ScriptableObject = null) {

  import RhinoRunner._
  private[this] var context: Context = null

  lazy val scope = {
    enterContext()
    val localScope = context.initStandardObjects(initialScope).asInstanceOf[ScriptableObject]
    addCommonsScript(localScope)
    localScope
  }

  private[this] def addCommonsScript(localScope: ScriptableObject) {
    Using.streamReader(getClass.getResourceAsStream(CommonsScript), charset) { script =>
      try {
        context.evaluateReader(localScope, script, "common.js", 1, null)
      } catch {
        case e: IOException =>
          throw new RuntimeException("Problem while evaluating commons.js script.", e);
      }
    }
  }

  def addClientSideEnvironment(): RhinoRunner = {
    try {
      evaluateChain(getClass.getResourceAsStream(ClientScriptEnv), "env.rhino.js")
    } catch {
      case e: IOException =>
        throw new RuntimeException("Couldn't initialize env.rhino.js script", e)
    }
  }

  def addJson(): RhinoRunner = {
    try {
      evaluateChain(getClass.getResourceAsStream(JsonScript), "json2.min.js")
    } catch {
      case e: IOException =>
        throw new RuntimeException("Couldn't initialize json2.min.js script", e)
    }
  }

  def enterContext() = {
    if (Context.getCurrentContext == null) {
      context = Context.enter()
      context.setOptimizationLevel(-1);
      // TODO redirect errors from System.err to LOG.error()
      context.setErrorReporter(new ToolErrorReporter(false));
      context.setLanguageVersion(Context.VERSION_1_7);
    }
  }

  /**
    * Evaluates a script and return [[io.backchat.sbtbrew.RhinoRunner]] for a chained script evaluation.
    *
    * @param stream [[java.io.InputStream]] of the script to evaluate.
    * @param sourceName the name of the evaluated script.
    * @return [[io.backchat.sbtbrew.RhinoRunner]] chain with required script evaluated.
    * @throws IOException if the script couldn't be retrieved.
    */
  def evaluateChain(stream: InputStream, sourceName: String): RhinoRunner = {
    require(stream != null)
    enterContext()
    Using.streamReader(stream, charset) { script =>
      try {
        context.evaluateReader(scope, script, sourceName, 1, null)
        this
      } catch {
        case e: RhinoException =>
          logger.error("Exception caught: %s" format RhinoUtils.createExceptionMessage(e))
          throw e
        case e: RuntimeException =>
          logger.error("Exception caught: %s" format e.getMessage)
          throw e
      }
    }
  }

  /**
    * Evaluates a script and return [[io.backchat.sbtbrew.RhinoRunner]] for a chained script evaluation.
    *
    * @param stream String of the script to evaluate.
    * @param sourceName the name of the evaluated script.
    * @return [[io.backchat.sbtbrew.RhinoRunner]] chain with required script evaluated.
    * @throws IOException if the script couldn't be retrieved.
    */
  def evaluateChain(script: String, sourceName: String): RhinoRunner = {
    require(script != null && script.trim.nonEmpty)
    enterContext()
    context.evaluateString(scope, script, sourceName, 1, null)
    this
  }

  /**
   * Evaluates a script.
   *
   * @param script string representation of the script to evaluate.
   * @param sourceName the name of the evaluated script.
   * @return evaluated object.
   * @throws IOException if the script couldn't be retrieved.
   */
  def evaluate(script: String, sourceName: String): Any = {
    require(script != null && script.trim.nonEmpty)
    // make sure we have a context associated with current thread
    enterContext()
    try {
      context.evaluateString(scope, script, sourceName, 1, null);
    } catch  {
      case e: JavaScriptException =>
        logger.error("JavaScriptException occured: " + e.getMessage)
        throw e
    } finally {
      // Rhino throws an exception when trying to exit twice. Make sure we don't get any exception
      if (Context.getCurrentContext() != null) {
        Context.exit();
      }
    }
  }

  def evaluate(reader: Reader, sourceName: String): Any = {
    require(reader !=  null)
    evaluate(IO.readLines(new BufferedReader(reader)).mkString("\n"), sourceName)
  }


}
