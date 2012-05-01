package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset

object CoffeeJadePlugin extends sbt.Plugin with ScriptEnginePlugin {
  import BrewPlugin.BrewKeys._

  private def compileSources(charset: Charset, bare: Boolean, options: String, log: Logger)(pair: (File, File)) = {
    val compiler = new CoffeeJadeScriptEngine(options, bare, log)
    try {
      val (coffeeJade, js) = pair
      log.debug("Compiling %s" format coffeeJade)
      compiler.compile(IO.read(coffeeJade, charset)).fold(
        err => sys.error(err),
        compiled => {
          if (!bare) IO.write(js, compiled)
          log.debug("Wrote to file %s" format js)
          (coffeeJade, js, compiled)
        })

    } catch {
      case e: Exception =>
        throw new RuntimeException(
          "error occured while compiling %s with %s: %s" format(
            pair._1, compiler, e.getMessage), e
        )
    }
  }
  
  private def compileChangedJade(context: ScriptEngineContext, viewsMapFile: Option[File], options: String, log: Logger) = 
    compileChanged(context, log) {
      case Nil =>
        log.debug("No jade templates to compile")
        compiled(context.targetDir, context.targetExtension)
      case xs =>
        log.info("Compiling %d jade templates to %s" format(xs.size, context.targetDir))
        val jadeViews = xs map compileSources(context.charset, viewsMapFile.isDefined, options, log)
        log.debug("Compiled %s jade templates" format xs.size)
        if (viewsMapFile.isDefined) {
          val outputFile = viewsMapFile.get
          outputFile.getParentFile.mkdirs()
          printWriter(context.charset)(outputFile) { out =>
            out.println("define(['frameworks'], function() {")
            out.println("  var templates;")
            out.println("  templates = {};")
            jadeViews foreach { case (sourcePath, viewPath, content) => 
              IO.relativize(context.sourceDir, sourcePath) foreach { vw => 
                out.println("  templates['%s'] = %s".format(vw, content.split("\n").drop(1).mkString("\n")))
              }
            } 
            out.println("  return templates;")
            out.println("});")
          }
          Seq(outputFile)
        } else compiled(context.targetDir, context.targetExtension)
    }

  private def coffeeJadeCompilerTask =
    (engineContext in coffeeJade, viewsFile in coffeeJade, jadeOptions in coffeeJade, streams) map { (ctx, vf, jo, out) =>
      compileChangedJade(ctx, vf, jo, out.log)
  }

  def coffeeJadeSettingsIn(c: Configuration): Seq[Setting[_]] =
    inConfig(c)(coffeeJadeSettings0 ++ Seq(
      sourceDirectory in coffeeJade <<= (sourceDirectory in (c, coffee))(_ / "views"),
      resourceManaged in coffeeJade <<= (resourceManaged in (c, coffee))(_ / "views"),
      viewsFile in coffeeJade <<= (resourceManaged in (c, coffeeJade))(d => Some(d / "jade.js")),
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
