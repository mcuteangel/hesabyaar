package io.github.mojri.hesabyar.ui.screens

import androidx.compose.runtime.saveable.SaverScope
import io.github.mojri.hesabyar.data.LoanType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip tests for [LoanFormStateSaver], the saver behind the
 * rememberSaveable add-loan form. No composition is needed here: the saver
 * is pure save/restore logic.
 */
class LoanFormStateSaverTest {
  @Test
  fun roundTripPreservesAllFields() {
    val state = LoanFormState(LoanType.CREDITOR, "Ali", 0L, "desc", 123L)
    state.amountText = "5,000"

    val scope = SaverScope { true }
    val saved = checkNotNull(with(scope) { LoanFormStateSaver.save(state) })
    val restored = checkNotNull(LoanFormStateSaver.restore(saved))

    assertEquals("Ali", restored.personName)
    assertEquals(LoanType.CREDITOR, restored.loanType)
    assertEquals("5,000", restored.amountText)
    assertEquals("desc", restored.description)
    assertEquals(123L, restored.customDate)
  }

  @Test
  fun unknownLoanTypeFallsBackToDebtor() {
    val restored =
      checkNotNull(LoanFormStateSaver.restore(listOf("A", "NOPE", "1", "d", 2L)))

    assertEquals(LoanType.DEBTOR, restored.loanType)
    assertEquals("A", restored.personName)
  }
}
