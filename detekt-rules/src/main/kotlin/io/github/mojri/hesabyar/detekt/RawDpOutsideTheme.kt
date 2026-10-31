package io.github.mojri.hesabyar.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPrefixExpression

class RawDpOutsideTheme(config: Config) :
  Rule(config, "Raw .dp literals outside the theme.") {
  override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
    super.visitDotQualifiedExpression(expression)
    if (!isDpSelector(expression)) return
    if (!isRawNumber(expression.receiverExpression)) return
    val msg = "Raw .dp literal ${expression.text} outside the theme; use a design token."
    report(Finding(Entity.from(expression), msg))
  }

  private fun isDpSelector(expression: KtDotQualifiedExpression): Boolean {
    val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return false
    val name = selector.getReferencedName()
    if (name == "dp") return true
    return dpAliases(expression.containingKtFile).contains(name)
  }

  private fun dpAliases(file: KtFile): Set<String> {
    val aliases = mutableSetOf<String>()
    for (directive in file.importList?.imports.orEmpty()) {
      val alias = directive.alias?.name ?: continue
      if (directive.importedFqName?.asString() == "androidx.compose.ui.unit.dp") aliases.add(alias)
    }
    return aliases
  }

  private fun isRawNumber(expression: KtExpression?): Boolean {
    return when (val unwrapped = unwrap(expression)) {
      is KtConstantExpression -> unwrapped.text.firstOrNull()?.isDigit() == true
      is KtPrefixExpression -> isSignedNumber(unwrapped)
      else -> false
    }
  }

  private fun isSignedNumber(expression: KtPrefixExpression): Boolean {
    val sign = expression.operationReference.text
    if (sign != "-" && sign != "+") return false
    val base = unwrap(expression.baseExpression) as? KtConstantExpression ?: return false
    return base.text.firstOrNull()?.isDigit() == true
  }

  private fun unwrap(expression: KtExpression?): KtExpression? {
    var current = expression
    while (current is KtParenthesizedExpression) current = current.expression
    return current
  }
}
