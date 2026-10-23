package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.domain.usecase.ManageCategoryUseCase
import io.github.mojri.hesabyar.ui.CategoryViewModel
import io.github.mojri.hesabyar.ui.FakeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * Robolectric Compose UI tests for [CategoryManagementScreen].
 * Exercises HesabyarScaffold integration, empty state, and category list rendering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CategoryManagementScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()
  private val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

  private val fakeRepository =
    object : FakeRepository() {
      override val allCategories = categoriesFlow
    }

  private val manageCategoryUseCase = ManageCategoryUseCase(fakeRepository)
  private lateinit var viewModel: CategoryViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    viewModel = CategoryViewModel(manageCategoryUseCase)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun emptyStateRendersScaffoldTitleBackAndEmptyMessage() {
    var backClicked = false
    composeRule.setContent {
      CategoryManagementScreen(
        categoryViewModel = viewModel,
        onBack = { backClicked = true }
      )
    }

    composeRule.onNodeWithText("مدیریت دسته‌بندی‌ها").assertIsDisplayed()
    composeRule.onNodeWithText("هنوز دسته‌بندی‌ای ثبت نشده است.").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("افزودن دسته‌بندی").assertIsDisplayed()

    composeRule.onNodeWithContentDescription("بازگشت").performClick()
    assertTrue("onBack should be called when back button clicked", backClicked)
  }

  @Test
  fun withCategoriesRendersDefaultAndCustomCategories() {
    val sampleCategories =
      listOf(
        Category(
          id = 1,
          name = "خوراک",
          key = "Food",
          icon = "Restaurant",
          color = 0xFF4CAF50L,
          type = CategoryType.EXPENSE,
          isDefault = true
        ),
        Category(
          id = 2,
          name = "حقوق",
          key = "Salary",
          icon = "Paid",
          color = 0xFF2196F3L,
          type = CategoryType.INCOME,
          isDefault = false
        )
      )
    categoriesFlow.value = sampleCategories

    composeRule.setContent {
      CategoryManagementScreen(
        categoryViewModel = viewModel,
        onBack = {}
      )
    }
    testDispatcher.scheduler.advanceUntilIdle()
    composeRule.waitForIdle()

    composeRule.onNodeWithText("دسته‌بندی‌های پیش‌فرض").assertIsDisplayed()
    composeRule.onNodeWithText("دسته‌بندی‌های اختصاصی").assertIsDisplayed()
    composeRule.onNodeWithText("خوراک").assertIsDisplayed()
    composeRule.onNodeWithText("حقوق").assertIsDisplayed()
  }

  @Test
  fun clickFabOpensAddCategoryDialog() {
    composeRule.setContent {
      CategoryManagementScreen(
        categoryViewModel = viewModel,
        onBack = {}
      )
    }

    composeRule.onNodeWithContentDescription("افزودن دسته‌بندی").performClick()
    composeRule.onNodeWithText("افزودن دسته‌بندی جدید").assertIsDisplayed()
    composeRule.onNodeWithText("انصراف").performClick()
    composeRule.onNodeWithText("افزودن دسته‌بندی جدید").assertDoesNotExist()
  }

  @Test
  fun deleteCustomCategoryPassesTheConfirmedCategoryToViewModel() {
    val deleted = mutableListOf<Category>()
    val recordingRepository =
      object : FakeRepository() {
        override val allCategories = categoriesFlow

        override suspend fun deleteCategory(category: Category) {
          deleted += category
        }
      }
    val recordingViewModel = CategoryViewModel(ManageCategoryUseCase(recordingRepository))
    val custom =
      Category(
        id = 7,
        name = "حقوق",
        key = "Salary",
        icon = "Paid",
        color = 0xFF2196F3L,
        type = CategoryType.INCOME,
        isDefault = false
      )
    categoriesFlow.value = listOf(custom)

    composeRule.setContent {
      CategoryManagementScreen(
        categoryViewModel = recordingViewModel,
        onBack = {}
      )
    }
    testDispatcher.scheduler.advanceUntilIdle()
    composeRule.waitForIdle()

    // Default categories render no delete button, so this node is the custom one.
    composeRule.onNodeWithContentDescription("حذف").performClick()
    composeRule.onNodeWithText("حذف دسته‌بندی").assertIsDisplayed()
    composeRule.onNodeWithText("حذف").performClick()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals("The confirmed category must be the one deleted", listOf(custom), deleted)
  }
}
