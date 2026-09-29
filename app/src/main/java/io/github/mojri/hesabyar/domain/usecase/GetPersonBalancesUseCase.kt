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
 * exception from the FFI call and the argument mapping, and returns a safe
 * fallback. It rethrows only cancellation, interruption and VM errors on
 * purpose, so those must keep propagating.
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
    // compute_person_balances returns one entry per input person, so a
    // non-empty persons list with an empty native result means the native call
    // failed (panic recovery returns an empty list). Trust native otherwise:
    // an empty persons list legitimately produces an empty result.
    return if (persons.isEmpty() || native.isNotEmpty()) {
      fromNative(native, persons)
    } else {
      // Native failed but we have persons → run the Kotlin mirror.
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
