package io.backchat.sbtbrew

import sbt._
import java.nio.charset.Charset
import sbt.Keys._

object CoffeePlugin extends sbt.Plugin with ScriptEnginePlugin {

  import BrewPlugin.BrewKeys._

  private def compileSources(bare: Boolean, charset: Charset, iced: Boolean, log: Logger)(pair: (File, File)) = {
    val compiler = if (iced) Iced(bare, log) else Vanilla(bare, log)
    try {
      val (coffee, js) = pair
      log.debug("Compiling %s" format coffee)
      compiler.compile(IO.read(coffee, charset)).fold(
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

  
  private def compileChangedCoffee(context: ScriptEngineContext, bare: Boolean, iced: Boolean, log: Logger) = 
    compileChanged(context, log) {
      case Nil =>
        log.debug("No CoffeeScripts to compile")
        compiled(context.targetDir, context.targetExtension)
      case xs =>
        log.info("Compiling %d CoffeeScripts to %s" format(xs.size, context.targetDir))
        xs map compileSources(bare, context.charset, iced, log)
        log.debug("Compiled %s CoffeeScripts" format xs.size)
        compiled(context.targetDir, context.targetExtension)    
    }

  private def coffeeCompilerTask =
    (streams, engineContext in coffee, bare in coffee, iced in coffee) map {
      (out, context, bare, iced) =>
        compileChangedCoffee(context, bare, iced, out.log)
    }

  def coffeeSettingsIn(c: Configuration): Seq[Setting[_]] =
    inConfig(c)(coffeeSettings0 ++ Seq(
      sourceDirectory in coffee <<= (sourceDirectory in c)(_ / "coffee"),
      resourceManaged in coffee <<= (resourceManaged in c)(_ / "js"),
      engineContext in coffee <<= buildEngineContext(coffee),
      cleanFiles in coffee <<= (resourceManaged in coffee)(_ :: Nil),
      watchSources in coffee <<= (unmanagedSources in coffee)
    )) ++ Seq(
      cleanFiles <+= (resourceManaged in coffee in c),
      watchSources <++= (unmanagedSources in coffee in c),
      resourceGenerators in c <+= coffee in c,
      compile in c <<= (compile in c).dependsOn(coffee in c)
    )

  def coffeeSettings: Seq[Setting[_]] =
    coffeeSettingsIn(Compile) ++ coffeeSettingsIn(Test)

  def coffeeSettings0: Seq[Setting[_]] = Seq(
    bare in coffee := false,
    iced in coffee := false,
    sourceExtensions in coffee <<= (iced in coffee)(ice => if (ice) Seq("coffee", "iced") else Seq("coffee"))) ++
    taskSettings(coffee) ++ Seq(
    coffee <<= coffeeCompilerTask
  )
}
