package io.github.mojri.hesabyar.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class RawDpOutsideTheme(config: Config) :
  Rule(config, "Raw .dp literals outside the theme.") {
  override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
    super.visitDotQualifiedExpression(expression)
    val receiver = expression.receiverExpression as? KtConstantExpression
    if (receiver == null || !receiver.text.first().isDigit()) return
    val selector = expression.selectorExpression as? KtNameReferenceExpression
    if (selector?.getReferencedName() != "dp") return
    val msg = "Raw .dp literal ${expression.text} outside the theme; use a design token."
    report(Finding(Entity.from(expression), msg))
  }
}
