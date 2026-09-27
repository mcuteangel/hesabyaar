package io.github.mojri.hesabyar.rust

import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Person

// Persons ledger domain of the RustBridge façade.
// See RustBridgeCore for the safe-call split.

/**
 * Per-person net balance summary (plans/011 Phase 3).
 *
 * Mirrors `PersonBalanceSummary` from the Rust core. Computed by
 * [RustBridgeCore.computePersonBalancesSync] via the native FFI; when the
 * native library is unavailable or panics this returns an empty list, letting
 * the caller run a Kotlin fallback.
 */
internal interface RustBridgePersons : RustBridgeCore {
  fun computePersonBalancesSync(
    persons: List<Person>,
    loans: List<Loan>
  ): List<PersonBalanceSummary> =
    rustCallSync(emptyList()) {
      HesabyarCore.computePersonBalances(
        persons = RustMappers.mapPersons(persons),
        loans = RustMappers.mapLoans(loans),
      )
    }
}
