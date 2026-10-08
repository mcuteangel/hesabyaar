package io.github.mojri.hesabyar.ui.utils

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

/** DEBTOR = primary, CREDITOR = error, UNKNOWN = neutral so it is not shown as a creditor. */
@Composable
fun LoanType.partyColor(): Color =
  when (this) {
    LoanType.DEBTOR -> MaterialTheme.colorScheme.primary
    LoanType.CREDITOR -> MaterialTheme.colorScheme.error
    LoanType.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
  }
