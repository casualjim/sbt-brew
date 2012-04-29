package io.backchat.sbtbrew

import sbt._
import Keys._
import java.nio.charset.Charset
import org.mozilla.javascript._
import io.backchat.brewsbt.RhinoUtils
import java.io.{IOException, InputStreamReader}
import util.control.Exception._

trait Compiler {

  def compile(scriptToCompile: String): Either[String, String]

}








