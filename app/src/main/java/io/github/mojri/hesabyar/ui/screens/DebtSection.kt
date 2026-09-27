package io.github.mojri.hesabyar.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.ui.graphics.vector.ImageVector

enum class DebtSection(
  val id: String,
  val label: String,
  val icon: ImageVector
) {
  INSTALLMENTS("INSTALLMENTS", "اقساط", Icons.Filled.CreditCard),
  BANK_LOANS("BANK_LOANS", "وام بانکی", Icons.Filled.AccountBalance),

  // Phase 3: the third tab now hosts the persons ledger. Loan management
  // remains reachable through the person quick-action flow; this screen is
  // retained for future deep-link support but no longer has a top-level tab.
  PERSONS("PERSONS", "اشخاص", Icons.Filled.AccountCircle)
}
