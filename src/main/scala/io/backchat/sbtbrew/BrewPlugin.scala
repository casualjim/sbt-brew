package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset
import reflect.BeanProperty
import net.liftweb.json._
import JsonDSL._

/**
 *  The config for RequireJS
 */
class RequireJsContext(val baseUrl: String, val dir: File) {
  var mainConfigFile: String = null
  var locale: String = "en-us"
  var optimize: String = "uglify"
  var optimizeCss: String = "standard" 
  var requireUrl: String = "require.js"
  var inlineText: Boolean = true
  var useStrict: Boolean = false
  var pragmas: Map[String, Boolean] = Map.empty
  var skipPragmas: Boolean = false
  var ccsImportIgnore: Seq[String] = Nil
  var isSkipModuleInsertion: Boolean = false
  var stubModules: Seq[String] = Nil
  var modules: Seq[RequireJsModule] = Nil
  var paths: Map[String, String] = Map.empty
  var packagePaths: Map[String, Seq[String]] = Map.empty
  var packages: Seq[String] = Nil
  var isMinifyOnlyAggregated: Boolean = false
  var uglify: Option[Uglify] = None
  var closure: Option[Closure] = None
  var wrap: Option[Wrap] = None
  var pragmasOnSave: Map[String, Boolean] = Map.empty
  var has: Map[String, Boolean] = Map.empty
  var hasOnSave: Map[String, Boolean] = Map.empty
  var namespace: String = null
  var isOptimizeAllPluginResources: Boolean = false
  var appDir: String = "./application"
  var findNestedDependencies: Boolean = false
  var removeCombined: Boolean = false
  var name: String = null
  var include: Seq[String] = Nil
  var includeRequire: Boolean = false
  var exclude: List[String] = Nil
  var excludeShallow: List[String] = Nil
  var fileExclusionRegexp: String = "^\\."
  var preserveLicenseComments: Boolean = true
  var logLevel: Int = 0
  var onBuildRead: String = null
  var onBuildWrite: String = null

  implicit val formats: Formats = DefaultFormats

  def toJValue: JObject = {   
    ("baseUrl"                      -> baseUrl) ~
    ("dir"                          -> dir.getAbsolutePath) ~
    ("mainConfigFile"               -> mainConfigFile ) ~
    ("locale"                       -> locale) ~
    ("optimize"                     -> optimize) ~
    ("optimizeCss"                  -> optimizeCss) ~
    ("requireUrl"                   -> requireUrl) ~
    ("inlineText"                   -> inlineText) ~
    ("useStrict"                    -> useStrict) ~
    ("pragmas"                      -> pragmas) ~
    ("skipPragmas"                  -> skipPragmas) ~
    ("ccsImportIgnore"              -> ccsImportIgnore) ~    
    ("skipModuleInsertion"          -> isSkipModuleInsertion) ~
    ("stubModules"                  -> stubModules) ~
    ("modules"                      -> JArray(modules.toList map (Extraction.decompose(_)))) ~
    ("paths"                        -> paths) ~
    ("packagePaths"                 -> packagePaths) ~
    ("packages"                     -> packages) ~
    ("isMinifyOnlyAggregated"       -> isMinifyOnlyAggregated) ~
    ("uglify"                       -> uglify.map(Extraction.decompose(_)).orNull) ~
    ("closure"                      -> closure.map(Extraction.decompose(_)).orNull) ~
    ("wrap"                         -> wrap.map(Extraction.decompose(_)).orNull) ~
    ("pragmasOnSave"                -> pragmasOnSave) ~
    ("has"                          -> has) ~
    ("hasOnSave"                    -> hasOnSave) ~
    ("namespace"                    -> namespace) ~
    ("optimizeAllPluginResources"   -> isOptimizeAllPluginResources) ~
    ("findNestedDependencies"       -> findNestedDependencies) ~
    ("removeCombined"               -> removeCombined) ~
    ("name"                         -> name) ~
    ("include"                      -> include) ~
    ("includeRequire"               -> includeRequire) ~
    ("exclude"                      -> exclude) ~
    ("excludeShallow"               -> excludeShallow) 
  }

}
case class RequireJsModule(name: String, includeRequire: Boolean = false, include: List[String] = Nil, exclude: List[String] = Nil, excludeShallow: List[String] = Nil, `override`: JObject = JObject(Nil))
case class Uglify(
             strict_semicolons: Boolean = false,
             toplevel: Boolean = false, 
             except: Array[String] = Array.empty, 
             defines: Map[String, Any] = Map.empty,
             make_seqs: Boolean = true,
             dead_code: Boolean = true,
             beautify: Boolean = false,
             indent_start: Int = 0,
             indent_level: Int = 2,
             quote_keys: Boolean = false,
             space_colon: Boolean = false,
             ascii_only: Boolean = false,
             inline_script: Boolean = false)
case class Closure(compilationLevel: String, loggingLevel: String, compilerOptions: Map[String, String] = Map.empty)
case class Wrap(start: String, end: String, startFile: String, endFile: String)
object BrewPlugin extends sbt.Plugin {

  object BrewKeys {
    val sourceExtensions = SettingKey[Seq[String]]("source-extensions", "The extensions the engine has to look for.")
    val targetExtension = SettingKey[String]("target-extension", "The extension this plugin compiles to.")
    
    val charset = SettingKey[Charset]("charset", "Sets the character encoding used in file IO. Defaults to utf-8.")
    
    val brew = TaskKey[Seq[File]]("brew", "Compile and optimize script source files.")
    val coffee = TaskKey[Seq[File]]("coffee", "Compile coffee sources.")
    val haml = TaskKey[Seq[File]]("haml", "Compile haml sources.")
    val coffeeJade = TaskKey[Seq[File]]("coffee-jade", "Compile the jade views with coffee jade.")
    val buildProfile = TaskKey[JObject]("build-profile", "Create the build profile for the require.js optimizer.")

    val engineContext = SettingKey[ScriptEngineContext]("engine-context", "The configuration for this script engine to compile files.")

    val bare = SettingKey[Boolean]("bare", "Compile coffee sources without top-level function wrapper. Defaults to true.")
    val iced = SettingKey[Boolean]("iced", """When true, The coffee task will compile vanilla CoffeeScript and "Iced" CoffeeScript sources.""")

    val viewsFile = SettingKey[Option[File]]("views-file", "The single JS file containing all the Jade templates in a map.")
    val jadeOptions = SettingKey[String]("jade-options", "Compiler options for the jade compiler.")

    val jsOptimizer = SettingKey[String]("js-optimizer", "The javascript optimizer for require.js to use. Defaults to uglify.")
  }
}
