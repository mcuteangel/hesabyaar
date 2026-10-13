package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import io.github.mojri.hesabyar.ui.FakeRepository
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the delete confirmation in [InstallmentListItem]: tapping the delete
 * button asks first, confirming deletes exactly the tapped installment (never a
 * row the dialog shifted onto), and dismissing deletes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class InstallmentDeleteConfirmTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val installment =
    Installment(id = 7, title = "قسط تست", amount = 100000L, dueDate = 0L)

  private class RecordingRepository : FakeRepository() {
    val deleted = mutableListOf<Installment>()

    override suspend fun deleteInstallment(installment: Installment) {
      deleted += installment
    }
  }

  private fun viewModelWith(repo: RecordingRepository): InstallmentViewModel =
    InstallmentViewModel(ManageInstallmentUseCase(repo))

  @Test
  fun deleteButtonOpensConfirmationDialog() {
    val repo = RecordingRepository()
    composeRule.setContent {
      InstallmentListItem(
        installment = installment,
        installmentViewModel = viewModelWith(repo)
      )
    }

    composeRule.onNodeWithContentDescription("حذف قسط").performClick()

    composeRule.onNodeWithText("حذف قسط").assertIsDisplayed()
    assertTrue(repo.deleted.isEmpty())
  }

  @Test
  fun confirmingDeletesTheTappedInstallment() {
    val repo = RecordingRepository()
    composeRule.setContent {
      InstallmentListItem(
        installment = installment,
        installmentViewModel = viewModelWith(repo)
      )
    }

    composeRule.onNodeWithContentDescription("حذف قسط").performClick()
    composeRule.onNodeWithText("حذف").performClick()

    composeRule.waitUntil(timeoutMillis = 5000) { repo.deleted.size == 1 }
    assertEquals(installment, repo.deleted.single())
    composeRule.onNodeWithText("حذف قسط").assertDoesNotExist()
  }

  @Test
  fun dismissingDeletesNothing() {
    val repo = RecordingRepository()
    composeRule.setContent {
      InstallmentListItem(
        installment = installment,
        installmentViewModel = viewModelWith(repo)
      )
    }

    composeRule.onNodeWithContentDescription("حذف قسط").performClick()
    composeRule.onNodeWithText("انصراف").performClick()

    composeRule.onNodeWithText("حذف قسط").assertDoesNotExist()
    assertTrue(repo.deleted.isEmpty())
  }
}
