package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-Kotlin mapper tests — no native library needed, so no
 * [io.github.mojri.hesabyar.RustTest] category.
 */
class RustMappersTest {
  private fun map(txType: io.github.mojri.hesabyar.rust.TransactionType): TransactionType =
    RustMappers
      .fromRustTransaction(
        io.github.mojri.hesabyar.rust.Transaction(
          id = 1L,
          txType = txType,
          categoryId = 0L,
          amount = 0L,
          description = "",
          personName = null,
          date = 0L,
          dueDate = null,
          installmentId = null,
          accountId = 1L,
          destinationAccountId = null
        )
      ).type

  @Test
  fun fromRustTransactionExpenseMapsToExpense() {
    assertEquals(TransactionType.EXPENSE, map(io.github.mojri.hesabyar.rust.TransactionType.EXPENSE))
  }

  @Test
  fun fromRustTransactionIncomeMapsToIncome() {
    assertEquals(TransactionType.INCOME, map(io.github.mojri.hesabyar.rust.TransactionType.INCOME))
  }

  @Test
  fun fromRustTransactionLoanDebtorCollapsesToExpense() {
    assertEquals(
      TransactionType.EXPENSE,
      map(io.github.mojri.hesabyar.rust.TransactionType.LOAN_DEBTOR)
    )
  }

  @Test
  fun fromRustTransactionLoanCreditorCollapsesToIncome() {
    assertEquals(
      TransactionType.INCOME,
      map(io.github.mojri.hesabyar.rust.TransactionType.LOAN_CREDITOR)
    )
  }

  @Test
  fun fromRustTransactionInstallmentCollapsesToExpense() {
    assertEquals(
      TransactionType.EXPENSE,
      map(io.github.mojri.hesabyar.rust.TransactionType.INSTALLMENT)
    )
  }

  // --- mapAnalyticsData accountBreakdown colors -------------------------------

  /** Rust analytics result with a single account that has no category activity. */
  private fun analyticsWithOneAccount(): io.github.mojri.hesabyar.rust.AnalyticsData =
    io.github.mojri.hesabyar.rust.AnalyticsData(
      monthlySpending = emptyList(),
      monthlyIncome = emptyList(),
      categoryBreakdown = emptyList(),
      debtors = emptyList(),
      creditors = emptyList(),
      totalDebt = 0L,
      totalCredit = 0L,
      totalInstallments = 0,
      paidInstallments = 0,
      bankLoans = emptyList(),
      bankLoansTotalDebt = 0L,
      accounts =
        listOf(
          io.github.mojri.hesabyar.rust.AccountAnalytics(
            accountId = 7L,
            accountName = "حساب بانکی",
            monthlyData = emptyList(),
            categoryBreakdown = emptyList()
          )
        )
    )

  @Test
  fun mapAnalyticsDataResolvesAccountBreakdownColorFromAccounts() {
    val accounts =
      listOf(
        AccountEntity(
          id = 7L,
          name = "حساب بانکی",
          type = AccountType.BANK,
          color = 0xFF2196F3L // blue
        )
      )

    val result = RustMappers.mapAnalyticsData(analyticsWithOneAccount(), emptyList(), emptyList(), accounts)

    assertEquals("account segment must exist", 1, result.accountBreakdown.size)
    assertEquals("segment must carry the account's configured color", 0xFF2196F3L, result.accountBreakdown[0].color)
  }

  @Test
  fun mapAnalyticsDataFallsBackToDefaultAccountColorWhenAccountMissing() {
    val result = RustMappers.mapAnalyticsData(analyticsWithOneAccount(), emptyList(), emptyList())

    assertEquals("account segment must exist", 1, result.accountBreakdown.size)
    assertEquals(
      "missing account falls back to the canonical account color",
      AccountEntity.DEFAULT_COLOR,
      result.accountBreakdown[0].color
    )
  }
}
