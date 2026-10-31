package io.github.mojri.hesabyar.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawColorOutsideThemeTest {
  private val rule = RawColorOutsideTheme(TestConfig())

  @Test
  fun flagsRawColorHexLiteral() {
    val code = """
      import androidx.compose.ui.graphics.Color
      fun palette() = Color(0xFF4CAF50)
    """.trimIndent()
    val findings = rule.lint(code)
    assertEquals(1, findings.size)
    assertTrue(findings[0].message.contains("0xFF4CAF50"))
  }

  @Test
  fun ignoresThemeColorReferences() {
    val code = """
      import androidx.compose.ui.graphics.Color
      val brand = MaterialTheme.colorScheme.primary
    """.trimIndent()
    assertEquals(0, rule.lint(code).size)
  }

  @Test
  fun ignoresNonHexColorArguments() {
    val code = """
      import androidx.compose.ui.graphics.Color
      fun f(c: Long) = Color(c)
    """.trimIndent()
    assertEquals(0, rule.lint(code).size)
  }

  @Test
  fun flagsParenthesizedHexLiteral() {
    val code = """
      import androidx.compose.ui.graphics.Color
      fun palette() = Color((0xFF4CAF50))
    """.trimIndent()
    assertEquals(1, rule.lint(code).size)
  }

  @Test
  fun flagsNamedHexArgument() {
    val code = """
      import androidx.compose.ui.graphics.Color
      fun palette() = Color(color = 0xFF4CAF50)
    """.trimIndent()
    assertEquals(1, rule.lint(code).size)
  }
}
