package io.github.mojri.hesabyar.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtFile

class RawColorOutsideTheme(config: Config) :
  Rule(config, "Raw Color hex outside the theme.") {
  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)
    if (!isColorCall(expression)) return
    for (arg in expression.valueArguments) {
      val constant = findHexConstant(arg.getArgumentExpression()) ?: continue
      val msg = "Raw color ${constant.text} outside the theme; use a theme color."
      report(Finding(Entity.from(expression), msg))
      return
    }
  }

  private fun isColorCall(expression: KtCallExpression): Boolean {
    val text = expression.calleeExpression?.text ?: return false
    if (text == "androidx.compose.ui.graphics.Color") return true
    if (text.endsWith(".Color")) {
      return text.substringBeforeLast(".") == "androidx.compose.ui.graphics"
    }
    val file = expression.containingKtFile
    if (colorAliases(file).contains(text)) return true
    if (text != "Color") return false
    return hasComposeColorImport(file)
  }

  private fun hasComposeColorImport(file: KtFile): Boolean {
    return file.importList?.imports.orEmpty().any { directive ->
      val fq = directive.importedFqName?.asString() ?: return@any false
      if (directive.isAllUnder) fq == "androidx.compose.ui.graphics" else fq == "androidx.compose.ui.graphics.Color"
    }
  }

  private fun colorAliases(file: KtFile): Set<String> {
    val aliases = mutableSetOf<String>()
    for (directive in file.importList?.imports.orEmpty()) {
      val alias = directive.alias?.name ?: continue
      if (directive.importedFqName?.asString() == "androidx.compose.ui.graphics.Color") aliases.add(alias)
    }
    return aliases
  }

  private fun findHexConstant(expression: KtExpression?): KtConstantExpression? {
    var current = expression
    while (true) {
      current = unwrap(current)
      when (current) {
        is KtConstantExpression -> {
          return if (current.text.startsWith("0x", ignoreCase = true)) current else null
        }
        is KtPrefixExpression -> {
          val sign = current.operationReference.text
          if (sign != "-" && sign != "+") return null
          current = current.baseExpression
          continue
        }
        else -> return null
      }
    }
  }

  private fun unwrap(expression: KtExpression?): KtExpression? {
    var current = expression
    while (current is KtParenthesizedExpression) current = current.expression
    return current
  }
}
