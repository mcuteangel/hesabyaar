package io.github.mojri.hesabyar.domain.utils

import io.github.mojri.hesabyar.core.MathUtils
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Person

/**
 * Pure-Kotlin mirror of the Rust core `compute_person_balances` (plans/011
 * Phase 3).
 *
 * This is the Kotlin fallback path: it produces byte-for-byte identical results
 * to the Rust implementation in every case the FFI exposes. The two are kept
 * in lockstep by [PersonBalanceParityTest]. When the native library fails or is
 * unavailable, [GetPersonBalancesUseCase] runs this function instead of the
 * FFI result.
 *
 * Rules (must match `compute_person_balances` in
 * `rust/hesabyar-core/src/models/mod.rs` exactly):
 *  - Only loans with a non-null `personId` contribute.
 *  - A loan whose `personId` references an unknown person is ignored.
 *  - Settled loans are counted in `settledLoanCount` but add nothing to the
 *    balance or `activeLoanCount`.
 *  - Unsettled DEBTOR ("person owes me") contributes `remainingAmount` to
 *    `totalReceivables` and `netBalance`.
 *  - Unsettled CREDITOR ("I owe person") contributes `remainingAmount` to
 *    `totalDebts` and subtracts from `netBalance`.
 *  - Result is ordered by `personId` ascending.
 */
object PersonBalanceCalculator {
  data class PersonBalance(
    val personId: Long,
    val personName: String,
    val totalReceivables: Long,
    val totalDebts: Long,
    val netBalance: Long,
    val activeLoanCount: Int,
    val settledLoanCount: Int,
  )

  fun compute(
    persons: List<Person>,
    loans: List<Loan>
  ): List<PersonBalance> {
    val totals = mutableMapOf<Long, PersonBalance>()
    for (p in persons) {
      totals[p.id] =
        PersonBalance(
          personId = p.id,
          personName = p.name,
          totalReceivables = 0L,
          totalDebts = 0L,
          netBalance = 0L,
          activeLoanCount = 0,
          settledLoanCount = 0,
        )
    }

    for (loan in loans) {
      // Skip loans with no person link and loans pointing at an unknown person.
      val pid = loan.personId?.takeIf { totals.containsKey(it) } ?: continue
      when (loan.type) {
        LoanType.DEBTOR -> applyDebtor(totals, pid, loan)
        LoanType.CREDITOR -> applyCreditor(totals, pid, loan)
        LoanType.UNKNOWN -> Unit
      }
    }

    return totals.values.sortedBy { it.personId }
  }

  private fun applyDebtor(
    totals: MutableMap<Long, PersonBalance>,
    pid: Long,
    loan: Loan
  ) {
    val e = totals.getValue(pid)
    if (loan.isSettled) {
      totals[pid] = e.copy(settledLoanCount = e.settledLoanCount.saturatingInc())
      return
    }
    val remaining = loan.remainingAmount
    totals[pid] =
      e.copy(
        totalReceivables = e.totalReceivables.saturatingAdd(remaining),
        netBalance = e.netBalance.saturatingAdd(remaining),
        activeLoanCount = e.activeLoanCount.saturatingInc(),
      )
  }

  private fun applyCreditor(
    totals: MutableMap<Long, PersonBalance>,
    pid: Long,
    loan: Loan
  ) {
    val e = totals.getValue(pid)
    if (loan.isSettled) {
      totals[pid] = e.copy(settledLoanCount = e.settledLoanCount.saturatingInc())
      return
    }
    val remaining = loan.remainingAmount
    totals[pid] =
      e.copy(
        totalDebts = e.totalDebts.saturatingAdd(remaining),
        netBalance = e.netBalance.saturatingSub(remaining),
        activeLoanCount = e.activeLoanCount.saturatingInc(),
      )
  }

  // Saturation delegates to the shared MathUtils helpers (already covered by
  // MathUtilsTest) so the fallback cannot diverge from other Kotlin call sites
  // or from the Rust core's saturating_add/saturating_sub on extreme amounts.
  private fun Long.saturatingAdd(other: Long): Long = MathUtils.saturatingAdd(this, other)

  private fun Long.saturatingSub(other: Long): Long = MathUtils.saturatingSub(this, other)

  private fun Int.saturatingInc(): Int = if (this == Int.MAX_VALUE) Int.MAX_VALUE else this + 1
}
