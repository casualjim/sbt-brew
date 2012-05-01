package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset

object CoffeeJadePlugin extends sbt.Plugin with ScriptEnginePlugin {
  import BrewPlugin.BrewKeys._

  private def compileSources(charset: Charset, options: String, log: Logger)(pair: (File, File)) = {
    val compiler = new CoffeeJadeScriptEngine(options, log)
    try {
      val (coffeeJade, js) = pair
      log.debug("Compiling %s" format coffeeJade)
      compiler.compile(IO.read(coffeeJade, charset)).fold(
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
  
  private def compileChangedJade(context: ScriptEngineContext, options: String, log: Logger) = 
    compileChanged(context, log) {
      case Nil =>
        log.debug("No jade templates to compile")
        compiled(context.targetDir, context.targetExtension)
      case xs =>
        log.info("Compiling %d jade templates to %s" format(xs.size, context.targetDir))
        xs map compileSources(context.charset, options, log)
        log.debug("Compiled %s jade templates" format xs.size)
        compiled(context.targetDir, context.targetExtension)    
    }

  private def coffeeJadeCompilerTask =
    (engineContext in coffeeJade, jadeOptions in coffeeJade, streams) map { (ctx, jo, out) =>
      compileChangedJade(ctx, jo, out.log)
  }

  def coffeeJadeSettingsIn(c: Configuration): Seq[Setting[_]] =
    inConfig(c)(coffeeJadeSettings0 ++ Seq(
      sourceDirectory in coffeeJade <<= (sourceDirectory in (c, coffee))(_ / "views"),
      resourceManaged in coffeeJade <<= (resourceManaged in (c, coffee))(_ / "views"),
      engineContext in coffeeJade <<= buildEngineContext(coffeeJade),
      cleanFiles in coffeeJade <<= (resourceManaged in coffeeJade)(_ :: Nil),
      watchSources in coffeeJade <<= (unmanagedSources in coffeeJade)
    )) ++ Seq(
      cleanFiles <+= (resourceManaged in coffeeJade in c),
      watchSources <++= (unmanagedSources in coffeeJade in c),
      resourceGenerators in c <+= coffeeJade in c,
      compile in c <<= (compile in c).dependsOn(coffeeJade in c)
    )

  def coffeeJadeSettings: Seq[Setting[_]] =
     CoffeePlugin.coffeeSettings ++ coffeeJadeSettingsIn(Compile) ++ coffeeJadeSettingsIn(Test)

  def coffeeJadeSettings0: Seq[Setting[_]] = Seq(
    jadeOptions in coffeeJade := "{}",
    sourceExtensions in coffeeJade := Seq("jade")) ++
    taskSettings(coffeeJade) ++ Seq(
    coffeeJade <<= coffeeJadeCompilerTask
  ) 
}
