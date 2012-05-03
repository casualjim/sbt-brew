package io.backchat.sbtbrew

import sbt._
import java.nio.charset.Charset
import sbt.Keys._

object HamlPlugin extends sbt.Plugin with ScriptEnginePlugin {

  import BrewPlugin.BrewKeys._

  private def compileSources(charset: Charset, log: Logger)(pair: (File, File)) = {
    val compiler = new HamlScriptEngine(log)
    try {
      val (haml, js) = pair
      log.debug("Compiling %s" format haml)
      compiler.compile(IO.read(haml, charset)).fold(
        err => sys.error(err),
        compiled => {
          IO.write(js, compiled)
          log.debug("Wrote to file %s" format js)
          js
        })

    } catch {
      case e: Exception =>
        throw new RuntimeException(
          "error occured while compiling %s with %s: %s" format(
            pair._1, compiler, e.getMessage), e
        )
    }
  }

  
  private def compileChangedHaml(context: ScriptEngineContext, log: Logger) =
    compileChanged(context, log) {
      case Nil =>
        log.debug("No haml views to compile")
        compiled(context.targetDir, context.targetExtension)
      case xs =>
        log.info("Compiling %d haml views to %s" format(xs.size, context.targetDir))
        xs map compileSources(context.charset, log)
        log.debug("Compiled %s hamlScripts" format xs.size)
        compiled(context.targetDir, context.targetExtension)    
    }

  private def hamlCompilerTask =
    (streams, engineContext in haml) map {
      (out, context) =>
        compileChangedHaml(context, out.log)
    }

  def hamlSettingsIn(c: Configuration): Seq[Setting[_]] =
    inConfig(c)(hamlSettings0 ++ Seq(
      sourceDirectory in haml <<= (sourceDirectory in (c, coffee))(_ / "views"),
      resourceManaged in haml <<= (resourceManaged in (c, coffee))(_ / "views"),
      engineContext in haml <<= buildEngineContext(haml),
      cleanFiles in haml <<= (resourceManaged in haml)(_ :: Nil),
      watchSources in haml <<= (unmanagedSources in haml)
    )) ++ Seq(
      cleanFiles <+= (resourceManaged in haml in c),
      watchSources <++= (unmanagedSources in haml in c),
      resourceGenerators in c <+= haml in c,
      compile in c <<= (compile in c).dependsOn(haml in c)
    )

  def hamlSettings: Seq[Setting[_]] =
    hamlSettingsIn(Compile) ++ hamlSettingsIn(Test)

  def hamlSettings0: Seq[Setting[_]] =
    Seq(sourceExtensions in haml := Seq("haml")) ++ taskSettings(haml) ++ Seq(haml <<= hamlCompilerTask)
}

