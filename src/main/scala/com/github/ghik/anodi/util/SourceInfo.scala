package com.github.ghik.anodi
package util

/**
 * Macro-materialized implicit value that provides information about callsite source file position.
 * It can be used in runtime for logging and debugging purposes.
 * Similar to Scalactic's `Position`, but contains more information.
 */
case class SourceInfo(
  filePath: String,
  fileName: String,
  line: Int,
  enclosingSymbolName: String,
)

object SourceInfo {
  def apply()(implicit si: SourceInfo): SourceInfo = si

  implicit def here: SourceInfo = macro macros.ComponentMacros.sourceInfo
}
