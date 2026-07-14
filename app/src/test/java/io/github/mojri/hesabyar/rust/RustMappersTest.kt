package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

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
          installmentId = null
        )
      ).type

  @Test
  fun `fromRustTransaction - Expense maps to EXPENSE`() {
    assertEquals(TransactionType.EXPENSE, map(io.github.mojri.hesabyar.rust.TransactionType.EXPENSE))
  }

  @Test
  fun `fromRustTransaction - Income maps to INCOME`() {
    assertEquals(TransactionType.INCOME, map(io.github.mojri.hesabyar.rust.TransactionType.INCOME))
  }

  @Test
  fun `fromRustTransaction - LoanDebtor collapses to EXPENSE`() {
    assertEquals(
      TransactionType.EXPENSE,
      map(io.github.mojri.hesabyar.rust.TransactionType.LOAN_DEBTOR)
    )
  }

  @Test
  fun `fromRustTransaction - LoanCreditor collapses to INCOME`() {
    assertEquals(
      TransactionType.INCOME,
      map(io.github.mojri.hesabyar.rust.TransactionType.LOAN_CREDITOR)
    )
  }

  @Test
  fun `fromRustTransaction - Installment collapses to EXPENSE`() {
    assertEquals(
      TransactionType.EXPENSE,
      map(io.github.mojri.hesabyar.rust.TransactionType.INSTALLMENT)
    )
  }
}
