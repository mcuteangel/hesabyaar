package io.github.mojri.hesabyar.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression

class RawColorOutsideTheme(config: Config) :
  Rule(config, "Raw Color hex outside the theme.") {
  override fun visitCallExpression(expression: KtCallExpression) {
    super.visitCallExpression(expression)
    val callee = expression.calleeExpression?.text?.substringAfterLast(".")
    if (callee != "Color") return
    val firstArg = expression.valueArguments.firstOrNull() ?: return
    val argument = firstArg.getArgumentExpression() as? KtConstantExpression
    if (argument == null) return
    if (!argument.text.startsWith("0x", ignoreCase = true)) return
    val msg = "Raw color ${argument.text} outside the theme; use a theme color."
    report(Finding(Entity.from(expression), msg))
  }
}
