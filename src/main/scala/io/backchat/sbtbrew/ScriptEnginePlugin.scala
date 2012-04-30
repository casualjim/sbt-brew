package io.backchat.sbtbrew

import sbt._
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

  protected def translatePath(context: ScriptEngineContext, coffee: File) = {
    IO.relativize(context.sourceDir, coffee) map { relative =>
      new File(context.targetDir, context.sourceExtensions.foldLeft(relative){ (p, ext) => p.replace(ext, context.targetExtension) })
    }
  }
}
