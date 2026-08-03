package io.github.mojri.hesabyar.domain.usecase
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.RustTest
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(RustTest::class)
class GetAnalyticsUseCaseFallbackTest {
  private val useCase = GetAnalyticsUseCase()

  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun setUp() {
    HesabyarApp.setRustInitializedForTesting(false)
  }

  @After
  fun tearDown() {
    HesabyarApp.setRustInitializedForTesting(true)
  }

  @Test
  fun kotlinFallbackPopulatesBankLoanSummaries() =
    runTest {
      val bankLoans =
        listOf(
          BankLoan(
            bankName = "بانک",
            loanName = "l",
            receivedAmount = 100_000_000L,
            monthlyInstallmentAmount = 10_000_000L,
            numberOfInstallments = 10,
            totalRepayableAmount = 120_000_000L,
            totalInterest = 20_000_000L,
            startDate = 1_700_000_000_000L,
            description = "",
            isSettled = false
          )
        )
      val result =
        useCase.computeAnalytics(emptyList(), emptyList(), emptyList(), emptyList(), bankLoans)

      assertEquals(1, result.bankLoans.size)
      assertEquals(120_000_000L, result.bankLoansTotalDebt)
      assertEquals(120_000_000L, result.bankLoans[0].remainingDebt)
    }

  @Test
  fun kotlinFallbackZeroesDebtForSettledLoan() =
    runTest {
      val bankLoans =
        listOf(
          BankLoan(
            bankName = "b",
            loanName = "l",
            receivedAmount = 100_000_000L,
            monthlyInstallmentAmount = 10_000_000L,
            numberOfInstallments = 10,
            totalRepayableAmount = 120_000_000L,
            totalInterest = 20_000_000L,
            startDate = 1_700_000_000_000L,
            description = "",
            isSettled = true
          )
        )
      val result =
        useCase.computeAnalytics(emptyList(), emptyList(), emptyList(), emptyList(), bankLoans)

      assertEquals(0L, result.bankLoansTotalDebt)
      assertTrue(result.bankLoans.all { it.remainingDebt == 0L })
    }

  // --- AccountId fallback filtering (T2-8) ---

  private fun tx(
    type: TransactionType,
    amount: Long,
    accountId: Long,
    destId: Long? = null
  ) = Transaction(
    type = type,
    categoryId = 1L,
    amount = amount,
    description = "test",
    date = System.currentTimeMillis(),
    accountId = accountId,
    destinationAccountId = destId
  )

  private fun cat(
    id: Long,
    name: String
  ) = io.github.mojri.hesabyar.data.Category(
    id = id,
    name = name,
    key = "test",
    icon = "",
    color = 0xFF000000L,
    type = CategoryType.EXPENSE
  )

  @Test
  fun fallbackAccountIdFiltersAnalyticsToMatchingTransactions() =
    runTest {
      val categories = listOf(cat(1, "خوراک"))
      val txs =
        listOf(
          tx(TransactionType.INCOME, 5_000_000, accountId = 1),
          tx(TransactionType.EXPENSE, 1_000_000, accountId = 1),
          tx(TransactionType.INCOME, 3_000_000, accountId = 2),
          tx(TransactionType.EXPENSE, 500_000, accountId = 2),
        )

      // accountId=1: only account 1's transactions
      val result1 =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accountId = 1
        )
      assertEquals("account 1 monthlyIncome", 5_000_000L, result1.monthlySpending.first().income)
      assertEquals("account 1 monthlyExpense", 1_000_000L, result1.monthlySpending.first().expense)

      // accountId=2: only account 2's transactions
      val result2 =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accountId = 2
        )
      assertEquals("account 2 monthlyIncome", 3_000_000L, result2.monthlySpending.first().income)
      assertEquals("account 2 monthlyExpense", 500_000L, result2.monthlySpending.first().expense)
    }

  @Test
  fun fallbackNullAccountIdIncludesAllTransactions() =
    runTest {
      val categories = listOf(cat(1, "خوراک"))
      val txs =
        listOf(
          tx(TransactionType.INCOME, 5_000_000, accountId = 1),
          tx(TransactionType.EXPENSE, 1_000_000, accountId = 1),
          tx(TransactionType.INCOME, 3_000_000, accountId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accountId = null
        )
      // null → all accounts: income=8M, expense=1M
      assertEquals(8_000_000L, result.monthlySpending.first().income)
      assertEquals(1_000_000L, result.monthlySpending.first().expense)
    }

  // --- Archived-account exclusion (all-accounts analytics) ---

  private fun account(
    id: Long,
    archived: Boolean = false
  ) = AccountEntity(
    id = id,
    name = "Account $id",
    type = AccountType.BANK,
    isArchived = archived
  )

  @Test
  fun fallbackAllAccountsExcludesArchivedAccountTransactions() =
    runTest {
      val categories = listOf(cat(1, "خوراک"))
      val accounts = listOf(account(1), account(2, archived = true))
      val txs =
        listOf(
          tx(TransactionType.INCOME, 3_000_000, accountId = 1),
          tx(TransactionType.EXPENSE, 1_000_000, accountId = 1),
          tx(TransactionType.INCOME, 500_000, accountId = 2),
          tx(TransactionType.EXPENSE, 200_000, accountId = 2),
        )

      val result =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts
        )
      // Archived-account transactions must not leak into all-accounts totals
      assertEquals(3_000_000L, result.monthlySpending.first().income)
      assertEquals(1_000_000L, result.monthlySpending.first().expense)
      assertEquals(1, result.categoryBreakdown.size)
      assertEquals(1_000_000L, result.categoryBreakdown.first().total)

      // includeArchived = true keeps them
      val withArchived =
        useCase.computeAnalytics(
          txs,
          emptyList(),
          emptyList(),
          categories,
          accounts = accounts,
          includeArchived = true
        )
      assertEquals(3_500_000L, withArchived.monthlySpending.first().income)
      assertEquals(1_200_000L, withArchived.monthlySpending.first().expense)
    }
}
