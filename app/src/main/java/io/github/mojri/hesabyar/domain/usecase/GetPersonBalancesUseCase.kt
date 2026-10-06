package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.utils.PersonBalanceCalculator
import io.github.mojri.hesabyar.rust.RustBridge

/**
 * Computes per-person ledger balances (plans/011 Phase 3).
 *
 * Primary path: native Rust [RustBridge.computePersonBalancesSync]. Fallback:
 * [PersonBalanceCalculator.compute] when Rust is unavailable or panics.
 *
 * The bridge call needs no try/catch here. `rustCallSync` already catches every
 * non-fatal checked exception from the FFI call and argument mapping, returning
 * a safe fallback. It rethrows cancellation, interruption, VM errors, and
 * unchecked runtime exceptions on purpose, so callers must not mask them.
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
  fun computePersonBalances(
    persons: List<Person>,
    loans: List<Loan>,
  ): List<PersonBalanceCalculator.PersonBalance> {
    val native = RustBridge.computePersonBalancesSync(persons = persons, loans = loans)
    // compute_person_balances returns one entry per input person. A size match
    // guarantees complete computation; an empty native result on non-empty
    // persons indicates bridge failure and triggers the Kotlin mirror fallback.
    return if (persons.isEmpty() || native.size == persons.size) {
      fromNative(native)
    } else {
      // Native failed or returned incomplete result → run the Kotlin mirror.
      PersonBalanceCalculator.compute(persons, loans)
    }
  }

  internal fun fromNative(
    native: List<io.github.mojri.hesabyar.rust.PersonBalanceSummary>,
  ): List<PersonBalanceCalculator.PersonBalance> =
    native.map {
      PersonBalanceCalculator.PersonBalance(
        personId = it.personId,
        personName = it.personName,
        totalReceivables = it.totalReceivables,
        totalDebts = it.totalDebts,
        netBalance = it.netBalance,
        activeLoanCount = it.activeLoanCount,
        settledLoanCount = it.settledLoanCount,
      )
    }
}
