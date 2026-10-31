package io.github.mojri.hesabyar.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class RawDpOutsideThemeTest {
  private val rule = RawDpOutsideTheme(TestConfig())

  @Test
  fun flagsRawDpLiterals() {
    val code = """
      import androidx.compose.ui.unit.dp
      fun layout() = Modifier.padding(16.dp)
    """.trimIndent()
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun flagsFloatDpLiterals() {
    val code = "fun layout() = Modifier.size(1.5.dp)"
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun ignoresTokenReferences() {
    val code = "fun layout() = Modifier.padding(SpacingTokens.lg)"
    assertEquals(0, rule.lint(code).size)
  }

  @Test
  fun flagsParenthesizedDpLiterals() {
    val code = "fun layout() = Modifier.padding((16).dp)"
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun flagsNegativeDpLiterals() {
    val code = "fun layout() = Modifier.offset((-16).dp)"
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun flagsParenthesizedSignedOperand() {
    val code = "fun layout() = Modifier.offset((-(16)).dp)"
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun ignoresComputedDpExpressions() {
    val code = "fun layout() = Modifier.padding((16 + 8).dp)"
    assertEquals(0, rule.lint(code).size)
  }

  @Test
  fun flagsAliasedDpSelector() {
    val code = """
      import androidx.compose.ui.unit.dp as dpUnit
      fun layout() = Modifier.padding(16.dpUnit)
    """.trimIndent()
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun flagsNestedSignedDpLiteral() {
    val code = "fun layout() = Modifier.offset((-(-8)).dp)"
    assertEquals(1, rule.lint(code).size)
  }
}
