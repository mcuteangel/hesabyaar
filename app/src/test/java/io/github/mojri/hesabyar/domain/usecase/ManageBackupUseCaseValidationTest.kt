package io.github.mojri.hesabyar.domain.usecase
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.RustTest
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Exercises [ManageBackupUseCase.validateBackup] on its **native (Rust)**
 * path — the only path reachable in unit tests, because the hesabyar_core
 * library always loads here (the Kotlin fallback in validatebackupkotlin
 * is exercised by an instrumentation run without the native library).
 *
 * This locks in the dispatch + Rust-backed validation contract: a well-formed
 * payload is accepted, and a structurally broken one is surfaced as invalid.
 */
@org.junit.experimental.categories.Category(RustTest::class)
class ManageBackupUseCaseValidationTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(true)
  }

  @After
  fun tearDown() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  private val useCase = ManageBackupUseCase(FakeRepository())

  @Test
  fun wellFormedPayloadIsValid() =
    runTest {
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 1L,
                amount = 1_000_000L,
                description = "t",
                date = 1_700_000_000_000L
              )
            ),
          loans =
            listOf(
              Loan(
                id = 1L,
                personName = "علی",
                type = LoanType.DEBTOR,
                originalAmount = 5_000_000L,
                remainingAmount = 3_000_000L,
                description = "l",
                date = 1_700_000_000_000L
              )
            ),
          installments =
            listOf(
              Installment(title = "قسط", amount = 1_000_000L, dueDate = 1_700_000_000_000L)
            ),
          categories =
            listOf(
              Category(
                id = 1L,
                name = "خوراک",
                key = "food",
                icon = "i",
                color = 0xFF0000L,
                type = CategoryType.EXPENSE
              )
            ),
          paymentHistories =
            listOf(PaymentHistory(id = 1L, loanId = 1L, amount = 100_000L, date = 1_700_000_000_000L))
        )

      val result = useCase.validateBackup(payload)
      assertTrue("expected $result to be Valid", result is BackupValidationResult.Valid)
    }

  @Test
  fun malformedJsonStylePayloadIsSurfacedAsInvalid() =
    runTest {
      // A transaction with a non-positive amount is invalid by every validator
      // (Kotlin fallback and the Rust core agree), so this must not pass as Valid.
      val payload =
        BackupPayload(
          transactions =
            listOf(
              Transaction(
                type = TransactionType.EXPENSE,
                categoryId = 1L,
                amount = 0L,
                description = "t",
                date = 1_700_000_000_000L
              )
            )
        )

      val result = useCase.validateBackup(payload)
      assertTrue("expected $result to be Invalid for zero-amount transaction", result is BackupValidationResult.Invalid)
    }

  @Test
  fun kotlinFallbackFlagsInvalidBankLoan() =
    runTest {
      // Force the Kotlin validation path (no native library).
      val previousState = HesabyarApp.isRustInitialized()
      HesabyarApp.setRustInitializedForTesting(false)
      try {
        val payload =
          BackupPayload(
            bankLoans =
              listOf(
                // Blank bank name + non-positive amounts => invalid.
                io.github.mojri.hesabyar.data.BankLoan(
                  bankName = "",
                  loanName = "x",
                  receivedAmount = 0L,
                  monthlyInstallmentAmount = 0L,
                  numberOfInstallments = 0,
                  totalRepayableAmount = 0L,
                  totalInterest = 0L,
                  startDate = 0L,
                  description = ""
                )
              )
          )

        val result = useCase.validateBackup(payload)
        assertTrue(
          "expected $result to be Invalid for malformed bank loan",
          result is BackupValidationResult.Invalid
        )
      } finally {
        HesabyarApp.setRustInitializedForTesting(previousState)
      }
    }
}
