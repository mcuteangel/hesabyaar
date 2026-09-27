package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.utils.PersonBalanceCalculator
import io.github.mojri.hesabyar.rust.RustBridge
import kotlinx.coroutines.flow.Flow

/**
 * Computes per-person ledger balances (plans/011 Phase 3).
 *
 * Primary path: native Rust [RustBridge.computePersonBalancesSync]. Fallback:
 * [PersonBalanceCalculator.compute] when Rust is unavailable or panics (the
 * FFI wrapper returns an empty list on failure rather than crashing the process).
 *
 * The Kotlin fallback is a pure mirror — see [PersonBalanceCalculator] and
 * [io.github.mojri.hesabyar.rust.PersonBalanceParityTest] for the parity
 * contract.
 */
class GetPersonBalancesUseCase {
  /** Snapshot of the source data used by the use case. */
  class Source(
    val persons: Flow<List<Person>>,
    val loans: Flow<List<Loan>>
  )

  /**
   * Compute balances from in-memory lists. Mirrors the Rust FFI signature so
   * the same call site serves both the native and fallback paths.
   */
  fun computePersonBalances(
    persons: List<Person>,
    loans: List<Loan>,
  ): List<PersonBalanceCalculator.PersonBalance> {
    val native =
      RustBridge.computePersonBalancesSync(persons = persons, loans = loans)
    // Rust returns an empty list on panic/unavailable. Re-derive from Kotlin
    // only when native failed; otherwise trust the FFI result to keep the
    // parity guarantee visible (the parity test asserts identical ordering too).
    return if (native.isNotEmpty() || persons.isEmpty() && loans.isEmpty()) {
      fromNative(native, persons)
    } else {
      // Native failed but we have data → run the Kotlin mirror.
      PersonBalanceCalculator.compute(persons, loans)
    }
  }

  private fun fromNative(
    native: List<io.github.mojri.hesabyar.rust.PersonBalanceSummary>,
    persons: List<Person>,
  ): List<PersonBalanceCalculator.PersonBalance> {
    val nameById = persons.associate { it.id to it.name }
    return native.map {
      PersonBalanceCalculator.PersonBalance(
        personId = it.personId,
        personName = nameById[it.personId] ?: it.personName,
        totalReceivables = it.totalReceivables,
        totalDebts = it.totalDebts,
        netBalance = it.netBalance,
        activeLoanCount = it.activeLoanCount,
        settledLoanCount = it.settledLoanCount,
      )
    }
  }
}
