import sbt._
import Keys._

object BrewBuild extends Build {

  import ScriptedPlugin._

  val defaultSettings = Project.defaultSettings ++ scriptedSettings ++ Seq(
    sbtPlugin := true,
    organization := "io.backchat",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.9.1",
    publishMavenStyle := false,
    scriptedBufferLog := false,
    resolvers += Resolver.url("sbt-plugin-releases", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
    scalacOptions ++= Seq("-deprecation", "-unchecked"),
    compileOrder := CompileOrder.JavaThenScala
  )

  val root = Project(
    id = "brew",
    base = file("."),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.mozilla" % "rhino" % "1.7R3",
        "com.samskivert" % "jmustache" % "1.3",
        "com.google.javascript" % "closure-compiler" % "r1592",
        "net.liftweb" %% "lift-json" % "2.4")))


}

