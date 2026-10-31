package io.github.mojri.hesabyar.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression

class RawColorOutsideTheme(config: Config) :
  Rule(config, "Raw Color hex outside the theme.") {
  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)
    val callee = expression.calleeExpression?.text?.substringAfterLast(".")
    if (callee != "Color") return
    for (arg in expression.valueArguments) {
      val argument = unwrap(arg.getArgumentExpression()) as? KtConstantExpression ?: continue
      if (!argument.text.startsWith("0x", ignoreCase = true)) continue
      val msg = "Raw color ${argument.text} outside the theme; use a theme color."
      report(Finding(Entity.from(expression), msg))
      return
    }
  }

  private fun unwrap(expression: KtExpression?): KtExpression? {
    var current = expression
    while (current is KtParenthesizedExpression) current = current.expression
    return current
  }
}
