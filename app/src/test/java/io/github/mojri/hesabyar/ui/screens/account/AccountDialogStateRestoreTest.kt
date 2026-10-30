package io.github.mojri.hesabyar.ui.screens.account

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [restoreDialogState]'s restore decision. The Add instanceId and the Edit
 * account id live in separate persisted slots, so:
 * - a live Add instanceId always restores the Add dialog;
 * - an Edit target only restores when the account still exists;
 * - an unmatched (deleted) Edit target restores None — it never falls back to
 *   a phantom Add dialog (the one-Long-for-two-namespaces bug).
 */
class AccountDialogStateRestoreTest {
  private val accounts =
    listOf(
      AccountEntity(id = 10L, name = "بانک ملت", type = AccountType.BANK)
    )

  @Test
  fun addSlotRestoresAddDialog() {
    val state = restoreDialogState(formDialogTargetId = -1L, addRestoreId = 123L, accounts = accounts)
    assertEquals(AccountDialogState.Add(instanceId = 123L), state)
  }

  @Test
  fun matchedPositiveTargetRestoresEditDialog() {
    val state = restoreDialogState(formDialogTargetId = 10L, addRestoreId = 0L, accounts = accounts)
    assertEquals(AccountDialogState.Edit(accounts[0]), state)
  }

  @Test
  fun unmatchedPositiveTargetRestoresNone() {
    val state = restoreDialogState(formDialogTargetId = 99L, addRestoreId = 0L, accounts = accounts)
    assertEquals(AccountDialogState.None, state)
  }

  @Test
  fun noDialogMarkersRestoreNone() {
    val state = restoreDialogState(formDialogTargetId = -1L, addRestoreId = 0L, accounts = accounts)
    assertEquals(AccountDialogState.None, state)
  }

  @Test
  fun addSlotWinsOverStaleEditTarget() {
    val state = restoreDialogState(formDialogTargetId = 10L, addRestoreId = 55L, accounts = accounts)
    assertEquals(AccountDialogState.Add(instanceId = 55L), state)
  }
}
