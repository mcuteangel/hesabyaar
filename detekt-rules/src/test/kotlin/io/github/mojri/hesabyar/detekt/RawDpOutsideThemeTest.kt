package io.github.mojri.hesabyar.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class RawDpOutsideThemeTest {
  private val rule = RawDpOutsideTheme(TestConfig())

  @Test
  fun `flags raw dp literals`() {
    val code = """
      import androidx.compose.ui.unit.dp
      fun layout() = Modifier.padding(16.dp)
    """.trimIndent()
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun `flags float dp literals`() {
    val code = "fun layout() = Modifier.size(1.5.dp)"
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun `ignores token references`() {
    val code = "fun layout() = Modifier.padding(SpacingTokens.lg)"
    assertEquals(0, rule.lint(code).size)
  }
}
