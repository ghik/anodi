package com.github.ghik.anodi
package util

import scala.quoted.*

trait SourceInfoCompat:
  this: SourceInfo.type =>
  inline implicit def here: SourceInfo = ${SourceInfoCompat.hereImpl}

object SourceInfoCompat:
  def hereImpl(using quotes: Quotes): Expr[SourceInfo] =
    import quotes.reflect.*
    val herePos = Position.ofMacroExpansion
    '{SourceInfo(
      ${Expr(herePos.sourceFile.path)},
      ${Expr(herePos.sourceFile.name)},
      ${Expr(herePos.startLine)},
      ${Expr(Symbol.spliceOwner.owner.name)}
    )}
