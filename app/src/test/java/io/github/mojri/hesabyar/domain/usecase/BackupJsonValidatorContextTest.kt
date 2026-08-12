package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupValidationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the DI contract that [BackupJsonValidator] is constructed with an
 * application [Context] in production (UseCaseModule → ManageBackupUseCase):
 * validation messages must resolve to real localized strings from strings.xml,
 * never the `<string-res-...>` sentinel that the plain-JVM tests tolerate.
 * Guards against a regression in the Context wiring that would silently ship
 * sentinel text to users.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupJsonValidatorContextTest {
  @Test
  fun contextWiredValidatorResolvesRealLocalizedMessages() {
    val validator = BackupJsonValidator(application = RuntimeEnvironment.getApplication())

    val result =
      validator.validateBackupKotlin(
        BackupPayload(
          version = 0,
          accounts = listOf(AccountEntity(id = 1L, name = "", type = AccountType.BANK))
        )
      )

    val errors = (result as BackupValidationResult.Invalid).errors
    assertTrue(
      "expected Invalid with messages, got $result",
      result is BackupValidationResult.Invalid
    )
    assertTrue(
      "version message must be the localized string, got: $errors",
      errors.any { it == "نسخه پشتیبان نامعتبر است" }
    )
    assertTrue(
      "account message must be the localized string, got: $errors",
      errors.any { it.contains("نام حساب #0 خالی است") }
    )
    assertFalse(
      "no message may leak the resource-id sentinel, got: $errors",
      errors.any { it.startsWith("<string-res-") }
    )
  }
}
