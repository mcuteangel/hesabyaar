package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import io.github.mojri.hesabyar.ui.FakeRepository
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class InstallmentDeleteConfirmTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val testDispatcher = StandardTestDispatcher()

  private val installment =
    Installment(id = 7, title = "قسط تست", amount = 100000L, dueDate = 0L)

  private class RecordingRepository : FakeRepository() {
    val deleted = mutableListOf<Installment>()

    override suspend fun deleteInstallment(installment: Installment) {
      deleted += installment
    }
  }

  private val repo = RecordingRepository()
  private val viewModel: InstallmentViewModel by lazy {
    InstallmentViewModel(ManageInstallmentUseCase(repo))
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repo.deleted.clear()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun settle() {
    testDispatcher.scheduler.advanceUntilIdle()
    composeRule.waitForIdle()
  }

  private fun setContent() {
    composeRule.setContent {
      InstallmentListItem(installment = installment, installmentViewModel = viewModel)
    }
    // The delete button lives in the expandable section: expand the card first.
    composeRule.onNodeWithText("قسط تست").performClick()
    composeRule.waitForIdle()
  }

  @Test
  fun deleteButtonOpensConfirmationDialog() {
    setContent()

    composeRule.onNodeWithContentDescription("حذف قسط").performClick()

    composeRule.onNodeWithText("حذف قسط").assertIsDisplayed()
    assertTrue(repo.deleted.isEmpty())
  }

  @Test
  fun confirmingDeletesTheTappedInstallment() {
    setContent()

    composeRule.onNodeWithContentDescription("حذف قسط").performClick()
    composeRule.onNodeWithText("حذف").performClick()
    settle()

    assertEquals(listOf(installment), repo.deleted)
    composeRule.onNodeWithText("حذف قسط").assertDoesNotExist()
  }

  @Test
  fun dismissingDeletesNothing() {
    setContent()

    composeRule.onNodeWithContentDescription("حذف قسط").performClick()
    composeRule.onNodeWithText("انصراف").performClick()
    settle()

    composeRule.onNodeWithText("حذف قسط").assertDoesNotExist()
    assertTrue(repo.deleted.isEmpty())
  }
}
