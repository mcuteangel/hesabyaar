package io.github.mojri.hesabyar.rust
import io.github.mojri.hesabyar.RustTest
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(RustTest::class)
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
  fun fromrusttransactionExpenseMapsToExpense() {
    assertEquals(TransactionType.EXPENSE, map(io.github.mojri.hesabyar.rust.TransactionType.EXPENSE))
  }

  @Test
  fun fromrusttransactionIncomeMapsToIncome() {
    assertEquals(TransactionType.INCOME, map(io.github.mojri.hesabyar.rust.TransactionType.INCOME))
  }

  @Test
  fun fromrusttransactionLoandebtorCollapsesToExpense() {
    assertEquals(
      TransactionType.EXPENSE,
      map(io.github.mojri.hesabyar.rust.TransactionType.LOAN_DEBTOR)
    )
  }

  @Test
  fun fromrusttransactionLoancreditorCollapsesToIncome() {
    assertEquals(
      TransactionType.INCOME,
      map(io.github.mojri.hesabyar.rust.TransactionType.LOAN_CREDITOR)
    )
  }

  @Test
  fun fromrusttransactionInstallmentCollapsesToExpense() {
    assertEquals(
      TransactionType.EXPENSE,
      map(io.github.mojri.hesabyar.rust.TransactionType.INSTALLMENT)
    )
  }
}
