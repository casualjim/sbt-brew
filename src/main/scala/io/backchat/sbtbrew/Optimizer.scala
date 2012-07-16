package io.backchat.sbtbrew

import sbt._
import org.mozilla.javascript.ErrorReporter
import java.io.{InputStream, FileOutputStream, File}


object Optimizer {
  private val RequirePlugins = Seq("i18n.js", "cs.js", "text.js", "domReady.js")
  private val BuildScriptClasspathFilename = "/r.js"

}

class Optimizer {

  import Optimizer._
  private val buildScript = File.createTempFile("build", "js")
  copyFromClassPathToFilesystem(BuildScriptClasspathFilename, buildScript)
  buildScript.deleteOnExit()



  private def copyFromClassPathToFilesystem(classpathFilename: String, outputFile: File): File = {
    Using.fileOutputStream()(outputFile) { output =>
      IO.transferAndClose(getClass.getResourceAsStream(classpathFilename), output)
    }
    outputFile
  }

  private def putRequirePluginsIn(buildDir: File): Seq[File] = {
    buildDir.mkdirs()
    RequirePlugins map { plugin =>
      val out = new File(buildDir, plugin)
      copyFromClassPathToFilesystem("/requirejs/"+plugin, out)
      out
    }
  }
}
