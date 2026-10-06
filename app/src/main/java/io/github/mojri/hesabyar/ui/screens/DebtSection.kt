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

  // Phase 3: the third tab hosts the persons ledger. Individual loans are
  // viewed and managed per-person via the PersonDetailSheet ledger timeline
  // and quick-actions (plans/011 Phase 3).
  PERSONS("PERSONS", "اشخاص", Icons.Filled.AccountCircle)
}
