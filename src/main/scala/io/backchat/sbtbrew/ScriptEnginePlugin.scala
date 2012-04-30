package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset

case class ScriptEngineContext(
    sourceExtensions: Seq[String],
    targetExtension: String,
    sourceDir: File,
    targetDir: File,
    charset: Charset,
    includeFilter: FileFilter,
    excludeFilter: FileFilter)
trait ScriptEnginePlugin { self: sbt.Plugin =>
  import BrewPlugin.BrewKeys._

  protected def compiled(under: File, targetExt: String) = (under ** ("*."+targetExt)).get
  protected def translatePath(context: ScriptEngineContext, coffee: File) = {
    IO.relativize(context.sourceDir, coffee) map { relative =>
      new File(context.targetDir, context.sourceExtensions.foldLeft(relative){ (p, ext) => p.replace(ext, context.targetExtension) })
    }
  }

  protected def compileChanged(context: ScriptEngineContext, log: Logger)(handler: PartialFunction[Seq[(File, File)], Seq[File]]): Seq[File] = {
    val r = for {
      coffee <- context.sourceDir.descendentsExcept(context.includeFilter, context.excludeFilter).get
      js <- translatePath(context, coffee) if coffee newerThan js }  yield (coffee, js)
    handler(r)
  }

  protected def buildEngineContext(t: TaskKey[Seq[File]]) = {
    (sourceDirectory in t, resourceManaged in t, includeFilter in t, excludeFilter in t,
      charset in t, sourceExtensions in t, targetExtension in t) {
      (sourceDir, targetDir, incl, excl, charset, sourceExts, targetExt) =>
        ScriptEngineContext(sourceExts, targetExt, sourceDir, targetDir, charset, incl, excl)
    }
  }

  protected def engineCleanTask(t: TaskKey[Seq[File]]) =
    (streams, resourceManaged in t) map {
      (out, target) =>
        out.log.info("Cleaning generated JavaScript under " + target)
        IO.delete(target)
    }

  protected def engineSourceTask(t: TaskKey[Seq[File]]) =
    (sourceDirectory in t, includeFilter in t, excludeFilter in t) map {
      (sourceDir, filt, excl) =>
        sourceDir.descendentsExcept(filt, excl).get
    }

  protected def taskSettings(t: TaskKey[Seq[File]]): Seq[Setting[_]] = Seq(
    targetExtension in t := "js",
    charset in t := Charset.forName("utf-8"),
    includeFilter in t <<= (sourceExtensions in t)(_.map(f => ("*."+f): FileFilter).reduce(_ || _)),
    excludeFilter in t := (".*" - ".") || "_*" || HiddenFileFilter,
    unmanagedSources in t <<= engineSourceTask(t),
    clean in t <<= engineCleanTask(t)
  )
}
