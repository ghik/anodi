package com.github.ghik.anodi
package util

trait SourceInfoCompat { this: SourceInfo.type =>
  implicit def here: SourceInfo = macro macros.ComponentMacros.sourceInfo
}
