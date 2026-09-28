package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.utils.PersonBalanceCalculator
import io.github.mojri.hesabyar.rust.RustBridge

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
  /**
   * Compute balances from in-memory lists. Mirrors the Rust FFI signature so
   * the same call site serves both the native and fallback paths.
   */
  @Suppress("TooGenericExceptionCaught")
  fun computePersonBalances(
    persons: List<Person>,
    loans: List<Loan>,
  ): List<PersonBalanceCalculator.PersonBalance> {
    val native =
      try {
        RustBridge.computePersonBalancesSync(persons = persons, loans = loans)
      } catch (e: Exception) {
        // Native call threw an unchecked exception — execute Kotlin fallback directly.
        AppLogger.e("GetPersonBalancesUseCase", "Native balance calculation failed: ${e.message}", e)
        return PersonBalanceCalculator.compute(persons, loans)
      }
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
