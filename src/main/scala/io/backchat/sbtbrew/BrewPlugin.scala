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

  implicit val formats: Formats = DefaultFormats

  def toJValue: JObject = {   
    ("baseUrl"                      -> baseUrl) ~
    ("dir"                          -> dir.getAbsolutePath) ~
    ("locale"                       -> locale) ~
    ("optimize"                     -> optimize) ~
    ("optimizeCss"                  -> optimizeCss) ~
    ("requireUrl"                   -> requireUrl) ~
    ("inlineText"                   -> inlineText) ~
    ("useStrict"                    -> useStrict) ~
    ("pragmas"                      -> pragmas) ~
    ("skipPragmas"                  -> skipPragmas) ~
    ("ccsImportIgnore"              -> ccsImportIgnore) ~    
    ("isSkipModuleInsertion"        -> isSkipModuleInsertion) ~
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
    ("isOptimizeAllPluginResources" -> isOptimizeAllPluginResources)
}

}
case class RequireJsModule(name: String, includeRequire: Boolean = false, include: List[String] = Nil, exclude: List[String] = Nil, excludeShallow: List[String] = Nil, `override`: JObject = JObject(Nil))
case class Uglify(
             gen_options: Map[String, String] = Map.empty,
             strict_semicolons: Map[String, String] = Map.empty,
             mangle_options: Map[String, String] = Map.empty,
             squeeze_options: Map[String, String] = Map.empty)
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
    val cssOptimizer = SettingKey[String]("css-optimizer", "The CSS optimizer for require.js to use. Defaults to standard.")
    val locale = SettingKey[String]("locale", "The locale for require.js to use. Defaults to en-us.")
    val requireUrl = SettingKey[String]("require-url", "The url for the require.js library. Defaults to require.js.")
    val inlineText = SettingKey[Boolean]("inline-text", "Inlines the text for any text! dependencies. Defaults to true.")
    val strict = SettingKey[Boolean]("use-strict", "Allow \"use strict\"; be included in the RequireJS files. Defaults to false.")
    val pragmas = SettingKey[Map[String, Boolean]]("pragmas", "Specify build pragmas.")
    val skipPragmas = SettingKey[Boolean]("skip-pragmas", "Skip processing of pragmas. Defaults to false.")
    val cssImportIgnore = SettingKey[Seq[String]]("css-import-ignores", "A list of of files to ignore for the @import inlining. The pats should match whatever is used in the @import statements.")
    val skipModuleInsertion = SettingKey[Boolean]("skip-module-insertion", "When false stubs out a define() placeholder. Defaults to false.")
    val modules = SettingKey[Seq[RequireJsModule]]("modules", "List the modules that will be optimized.")
    val paths = SettingKey[Map[String, String]]("paths", "Set paths for modules. If relative paths, set relative to `sourceDirectory in brew`")
    val packagePaths = SettingKey[Map[String, Seq[String]]]("package-paths", "Configure CommonJS packages. See http://requirejs.org/docs/api.html#packages")
    val packages = SettingKey[Seq[String]]("packages", "Configures loading modules from CommonJS packages")
    val minifyOnlyAggregated = SettingKey[Boolean]("minify-only-aggregated", "Set to true to only minify the aggregated module files. Defaults to false")
    val uglify = SettingKey[Option[Uglify]]("uglify", "Config for UglifyJS. See https://github.com/mishoo/UglifyJS for the possible values.")
    val closure = SettingKey[Option[Closure]]("closure","Config for google closure" )
    val pragmasOnSave = SettingKey[Map[String, Boolean]]("pragmas-on-save", "Same as \"pragmas\", but only applied once during the file save phase")
    val has = SettingKey[Map[String, Boolean]]("has", "Allows trimming of code branches that use has.js-based feature detection: https://github.com/phiggins42/has.js. See http://requirejs.org/docs/optimization.html#hasjs")
    val hasOnSave = SettingKey[Map[String, Boolean]]("has-on-save", "Similar to pragmasOnSave, but for has tests, only applied during the file save phase of optimization")
    val namespace = SettingKey[String]("namespace", "Allows namespacing requirejs, require and define calls to a new name.")
    val optimizeAllPluginResources = SettingKey[Boolean]("optimize-all-plugin-resources", "Scan all .js files for plugin resources and apply optimizations. Defaults to false")
    val wrap = SettingKey[Wrap]("wrap", "Wrap any build layer in a start and end text specified by wrap.")
  }
}
