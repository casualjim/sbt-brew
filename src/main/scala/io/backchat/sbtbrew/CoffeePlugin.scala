package io.backchat.sbtbrew

//import coffeescript.{Vanilla, Iced}
import sbt._
import sbt.Project.Initialize
import scala.collection.JavaConversions._
import java.nio.charset.Charset
import java.io.File
import sbt.Keys._

object CoffeePlugin extends sbt.Plugin with ScriptEnginePlugin {

  import BrewPlugin.BrewKeys._
  import ScriptEngineKeys.engineContext
  import CoffeeKeys._

  object CoffeeKeys {
    val coffee = TaskKey[Seq[File]]("coffee", "Compile coffee sources.")
    val bare = SettingKey[Boolean]("bare", "Compile coffee sources without top-level function wrapper.")
    val iced = SettingKey[Boolean]("iced", """When true, The coffee task will compile vanilla CoffeeScript and "Iced" CoffeeScript sources""")

  }

  private def javascript(sources: File, coffee: File, targetDir: File) =
    Some(new File(targetDir, IO.relativize(sources, coffee).get.replace(".coffee", ".js").replace(".iced", ".js")))

  private def compileSources(bare: Boolean, charset: Charset, iced: Boolean, log: Logger)(pair: (File, File)) = {
    val compiler = if (iced) Iced(bare, log) else Vanilla(bare, log)
    try {
      val (coffee, js) = pair
      log.debug("Compiling %s" format coffee)

      val code = scala.io.Source.fromFile(coffee)(scala.io.Codec(charset)).mkString
       compiler.compile(code).fold(
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

  private def compiled(under: File) = (under ** "*.js").get

  private def compileChanged(context: ScriptEngineContext, bare: Boolean, iced: Boolean, log: Logger) =
    (for (coffee <- context.sourceDir.descendentsExcept(context.includeFilter, context.excludeFilter).get;
          js <- translatePath(context, coffee)
          if (coffee newerThan js)) yield (coffee, js)) match {
      case Nil =>
        log.debug("No CoffeeScripts to compile")
        compiled(context.targetDir)
      case xs =>
        log.info("Compiling %d CoffeeScripts to %s" format(xs.size, context.targetDir))
        xs map compileSources(bare, context.charset, iced, log)
        log.debug("Compiled %s CoffeeScripts" format xs.size)
        compiled(context.targetDir)
    }

  private def coffeeCleanTask =
    (streams, resourceManaged in coffee) map {
      (out, target) =>
        out.log.info("Cleaning generated JavaScript under " + target)
        IO.delete(target)
    }

  private def coffeeCompilerTask =
    (streams, engineContext in coffee, bare in coffee, iced in coffee) map {
      (out, context, bare, iced) =>
        compileChanged(context, bare, iced, out.log)
    }

  // move defaultExcludes to excludeFilter in unmanagedSources later
  private def coffeeSourcesTask =
    (sourceDirectory in coffee, includeFilter in coffee, excludeFilter in coffee) map {
      (sourceDir, filt, excl) =>
        sourceDir.descendentsExcept(filt, excl).get
    }

  private def buildEngineContext = {
    (sourceDirectory in coffee, resourceManaged in coffee, includeFilter in coffee, excludeFilter in coffee,
      charset in coffee, sourceExtensions in coffee, targetExtension in coffee) map {
      (sourceDir, targetDir, incl, excl, charset, sourceExts, targetExt) =>
        ScriptEngineContext(sourceExts, targetExt, sourceDir, targetDir, charset, incl, excl)
    }
  }

  def coffeeSettingsIn(c: Configuration): Seq[Setting[_]] =
    inConfig(c)(coffeeSettings0 ++ Seq(
      sourceDirectory in coffee <<= (sourceDirectory in c)(_ / "coffee"),
      resourceManaged in coffee <<= (resourceManaged in c)(_ / "js"),
      engineContext in coffee <<= buildEngineContext,
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
    sourceExtensions in coffee <<= (iced in coffee)(ice => if (ice) Seq("coffee", "jade") else Seq("coffee")),
    targetExtension in coffee := "js",
    charset in coffee := Charset.forName("utf-8"),
    includeFilter in coffee <<= (sourceExtensions in coffee)(_.map(f => ("*."+f): FileFilter).reduce(_ || _)),
    excludeFilter in coffee := (".*" - ".") || "_*" || HiddenFileFilter,
    unmanagedSources in coffee <<= coffeeSourcesTask,
    clean in coffee <<= coffeeCleanTask,
    coffee <<= coffeeCompilerTask
  )
}
