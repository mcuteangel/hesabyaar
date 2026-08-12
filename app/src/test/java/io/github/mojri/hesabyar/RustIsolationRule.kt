package io.github.mojri.hesabyar

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Ensures the Rust bridge global state is clean at test-class boundaries.
 *
 * Usage: add ruleValRustisolationruleRustisolationrule to any test
 * class that calls [HesabyarApp.setRustInitializedForTesting].
 *
 * Before the annotated item the Kotlin flag is reset to false (clean slate);
 * after it the saved previous value is restored, so that state cannot leak
 * across test classes even when a test method or [org.junit.After] crashes.
 */
class RustIsolationRule : TestRule {
  override fun apply(
    base: Statement,
    description: Description,
  ): Statement =
    object : Statement() {
      override fun evaluate() {
        val previousState = HesabyarApp.isRustInitialized()
        HesabyarApp.setRustInitializedForTesting(false)
        try {
          base.evaluate()
        } finally {
          HesabyarApp.setRustInitializedForTesting(previousState)
        }
      }
    }
}
