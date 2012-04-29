package io.backchat.sbtbrew

import sbt._
import scala.collection._

object ScriptSource {
  /**
   * Transforms a java multi-line string into javascript multi-line string.
   * This technique was found at {@link http://stackoverflow.com/questions/805107/multiline-strings-in-javascript/}
   * @param data a string containing new lines.
   * @return a string which being evaluated on the client-side will be treated as a correct multi-line string.
   */
  def toJSMultiLineString(data: String): String = {
    val lines = data.split("\n")
    val result = new StringBuilder("[")
    if (lines.isEmpty) result.append("\"\"")
    lines.zipWithIndex foreach { case (line, idx) =>
      result.append("\"")
      result.append(line.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("\\r|\\n", ""))
      if (lines.length == 1) result.append("\n")
      result.append("\"")
      if (idx < lines.length - 1) result.append(",")
    }
    result.append("].join(\"\\n\")")
    result.toString
  }
}
//trait ScriptSource extends UntypedSource {
//
//  type S = ScriptSource
//  type G = ScriptGraph
//}
