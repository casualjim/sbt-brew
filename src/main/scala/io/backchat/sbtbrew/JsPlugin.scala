//package io.backchat.sbtbrew
//
//import sbt._
//import Keys._
//import java.nio.charset.Charset
//import java.util.Properties
//import Project.Initialize
//
//class JsPlugin extends Plugin {
//
////  object CoffeeScriptFlavor {
////    sealed trait Value
////    object Vanilla extends Value
////    object Iced extends Value
////  }
//
//  object JsKeys {
//    val charset = SettingKey[Charset]("charset", "Sets the character encoding used in Javascript files (default utf-8)")
//    val brew = TaskKey[Seq[File]]("brew", "Compile and optimize script source files")
////    val bare = SettingKey[Boolean]("compile-option", "Compile coffee sources without top-level function wrapper.")
////    val engine = SettingKey[ScriptEngine]("compilers", "The registered compilers (default icedcoffee, coffeejade, haml, js)")
//    val optimizer = SettingKey[Optimizer]("optimizer", "The optimizer to use")
//    val coffee = TaskKey[Seq[File]]("coffee", "Compile coffee sources.")
//  }
//
//  import JsKeys._
//
//  def jsSettingsIn(conf: Configuration): Seq[Setting[_]] =
//    inConfig(conf)(Seq(
//      charset in brew            :=   Charset.forName("utf-8"),
//      includeFilter in brew      <<=  (compilers in brew)(_ flatMap (_.extensions map ("*."+_)) reduce (_ || _)),
//      excludeFilter in brew      :=   (".*" - ".") || "_*" || HiddenFileFilter,
//      sourceDirectory in brew    <<=  (sourceDirectory in conf),
//      sourceDirectories in brew  <<=  (sourceDirectory in (conf, brew)) { Seq(_) },
//      unmanagedSources in brew   <<=  unmanagedSourcesTask,
//      clean in brew              <<=  cleanTask
//    )) ++ Seq(
//      cleanFiles                 <+=  (resourceManaged in brew in conf),
//      watchSources               <++= (watchSources in brew in conf),
//      resourceGenerators in conf <+= coffee in c,
//      compile in c               <<= (compile in c).dependsOn(coffee in c)
//    )
//
//  def jsSettings: Seq[Setting[_]] = jsSettingsIn(Compile) ++ jsSettingsIn(Test)
//
//  def unmanagedSourcesTask: Initialize[Task[Seq[File]]] =
//    (streams, sourceDirectories in brew, includeFilter in brew, excludeFilter in brew) map {
//      (out, sourceDirs, includeFilter, excludeFilter) =>
//        out.log.debug("sourceDirectories: " + sourceDirs)
//        out.log.debug("includeFilter: " + includeFilter)
//        out.log.debug("excludeFilter: " + excludeFilter)
//
//        sourceDirs.foldLeft(Seq[File]()) {
//          (accum, sourceDir) =>
//            accum ++ sourceDir.descendentsExcept(includeFilter, excludeFilter).get
//        }
//    }
//
//  def cleanTask =
//    (streams, resourceManaged in brew) map {
//      (out, target) =>
//        out.log.info("Cleaning generated JavaScript under " + target)
//        IO.delete(target)
//    }
//
////  def sourceGraphTask: Initialize[Task[ScriptGraph]] =
////    (streams, sourceDirectories in brew, resourceManaged in brew, unmanagedSources in brew, templateProperties) map {
////      (out, sourceDirs, targetDir, sourceFiles, templateProperties) =>
////        out.log.debug("sbt-brew-js template properties " + templateProperties)
////
////        val graph = ScriptGraph(
////          log                = out.log,
////          sourceDirs         = sourceDirs,
////          targetDir          = targetDir,
////          templateProperties = templateProperties
////        )
////
////        sourceFiles.foreach(graph += _)
////
////        graph
////    }
//
////  def watchSourcesTask: Initialize[Task[Seq[File]]] =
////    (streams, sourceGraph in brew) map {
////      (out, graph) =>
////        graph.sources.map(_.src)
////    }
//
//
//}
