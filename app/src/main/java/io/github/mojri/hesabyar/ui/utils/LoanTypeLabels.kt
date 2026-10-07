package io.github.mojri.hesabyar.ui.utils

import androidx.annotation.StringRes
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.LoanType

/**
 * Single source for how a loan's counterparty is named in the UI and in exports.
 * The label describes the person: DEBTOR (they owe the user) is «بدهکار» and
 * CREDITOR (the user owes them) is «طلبکار».
 */
@StringRes
fun LoanType.partyLabelRes(): Int =
  when (this) {
    LoanType.DEBTOR -> R.string.loan_party_debtor
    LoanType.CREDITOR -> R.string.loan_party_creditor
    LoanType.UNKNOWN -> R.string.loan_party_unknown
  }
