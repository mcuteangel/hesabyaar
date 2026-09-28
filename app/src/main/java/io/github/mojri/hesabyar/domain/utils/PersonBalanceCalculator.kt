package io.github.mojri.hesabyar.domain.utils

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
    val totals =
      persons
        .associate { p ->
          p.id to
            PersonBalance(
              personId = p.id,
              personName = p.name,
              totalReceivables = 0L,
              totalDebts = 0L,
              netBalance = 0L,
              activeLoanCount = 0,
              settledLoanCount = 0,
            )
        }.toMutableMap()

    for (loan in loans) {
      // Skip loans with no person link and loans pointing at an unknown person.
      val pid = loan.personId?.takeIf { totals.containsKey(it) } ?: continue
      val e = totals.getValue(pid)
      val remaining = loan.remainingAmount
      when (loan.type) {
        LoanType.DEBTOR -> {
          if (loan.isSettled) {
            totals[pid] = e.copy(settledLoanCount = e.settledLoanCount.saturatingInc())
          } else {
            totals[pid] =
              e.copy(
                totalReceivables = e.totalReceivables.saturatingAdd(remaining),
                netBalance = e.netBalance.saturatingAdd(remaining),
                activeLoanCount = e.activeLoanCount.saturatingInc(),
              )
          }
        }
        LoanType.CREDITOR -> {
          if (loan.isSettled) {
            totals[pid] = e.copy(settledLoanCount = e.settledLoanCount.saturatingInc())
          } else {
            totals[pid] =
              e.copy(
                totalDebts = e.totalDebts.saturatingAdd(remaining),
                netBalance = e.netBalance.saturatingSub(remaining),
                activeLoanCount = e.activeLoanCount.saturatingInc(),
              )
          }
        }
        LoanType.UNKNOWN -> Unit
      }
    }

    return totals.values.sortedBy { it.personId }
  }

  // Saturating helpers mirror the Rust core's saturating_add/saturating_sub so
  // the fallback cannot wrap into a negative balance on extreme magnitudes.
  private fun Long.saturatingAdd(other: Long): Long {
    val sum = this + other
    // Overflow sign check: operands disagree with the result's sign bit.
    val sumXorThis = sum xor this
    val sumXorOther = sum xor other
    return if (sumXorThis and sumXorOther < 0L) {
      if (this > 0L) Long.MAX_VALUE else Long.MIN_VALUE
    } else {
      sum
    }
  }

  private fun Long.saturatingSub(other: Long): Long {
    val diff = this - other
    // Overflow sign check: operands disagree and result disagrees with minuend.
    val thisXorOther = this xor other
    val thisXorDiff = this xor diff
    return if (thisXorOther and thisXorDiff < 0L) {
      if (this > 0L) Long.MAX_VALUE else Long.MIN_VALUE
    } else {
      diff
    }
  }

  private fun Int.saturatingInc(): Int = if (this == Int.MAX_VALUE) Int.MAX_VALUE else this + 1
}
