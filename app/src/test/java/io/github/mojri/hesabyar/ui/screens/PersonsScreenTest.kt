package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.usecase.GetPersonBalancesUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.FakeRepository
import io.github.mojri.hesabyar.ui.PersonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Filter and navigation tests for [PersonsScreen] (plans/011 Phase 3).
 *
 * PersonRow clears its subtree semantics and exposes a single merged
 * contentDescription, so nodes are located by that description, not by name.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PersonsScreenTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val testDispatcher = StandardTestDispatcher()
  private var previousUnit: CurrencyUnit? = null
  private val personsFlow = MutableStateFlow<List<Person>>(emptyList())
  private val loansFlow = MutableStateFlow<List<Loan>>(emptyList())

  private val fakeRepository =
    object : FakeRepository() {
      override val allPersons = personsFlow
      override val allLoans = loansFlow
    }

  private val manageLoanUseCase = ManageLoanUseCase(fakeRepository)
  private val getPersonBalancesUseCase = GetPersonBalancesUseCase()

  private val viewModel by lazy {
    PersonViewModel(fakeRepository, manageLoanUseCase, getPersonBalancesUseCase)
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    previousUnit = CurrencyFormatter.currentUnit
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    previousUnit?.let { CurrencyFormatter.setUnit(it) }
  }

  private fun setupSampleData() {
    personsFlow.value =
      listOf(
        Person(id = 1L, name = NAME_ALI, normalizedName = "ali"),
        Person(id = 2L, name = NAME_REZA, normalizedName = "reza"),
        Person(id = 3L, name = NAME_SARA, normalizedName = "sara"),
      )
    loansFlow.value =
      listOf(
        activeLoan(10L, 1L, NAME_ALI, LoanType.DEBTOR, 100_000L),
        activeLoan(20L, 2L, NAME_REZA, LoanType.CREDITOR, 200_000L),
        Loan(
          id = 30L,
          personId = 3L,
          personName = NAME_SARA,
          type = LoanType.DEBTOR,
          originalAmount = 50_000L,
          remainingAmount = 0L,
          description = "",
          date = 1000L,
          isSettled = true
        ),
      )
  }

  private fun activeLoan(
    id: Long,
    personId: Long,
    personName: String,
    type: LoanType,
    amount: Long,
  ): Loan =
    Loan(
      id = id,
      personId = personId,
      personName = personName,
      type = type,
      originalAmount = amount,
      remainingAmount = amount,
      description = "",
      date = 1000L,
      isSettled = false
    )

  /** Mirrors the merged contentDescription that [PersonRow] exposes. */
  private fun rowDescription(
    name: String,
    directionLabel: String,
    netRial: Long,
  ): String = "$name: $directionLabel, موجودی ${CurrencyFormatter.format(netRial)}"

  /** Compose idle does not drain the test dispatcher; do both until stable. */
  private fun settle() {
    repeat(3) {
      testDispatcher.scheduler.advanceUntilIdle()
      composeRule.waitForIdle()
    }
  }

  @Test
  fun allFilterDisplaysAllPersons() {
    setupSampleData()

    composeRule.setContent {
      PersonsScreen(personViewModel = viewModel)
    }
    settle()

    composeRule.onNodeWithContentDescription(rowDescription(NAME_ALI, LABEL_DEBTOR, 100_000L)).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_REZA, LABEL_CREDITOR, -200_000L)).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_SARA, LABEL_BALANCED, 0L)).assertIsDisplayed()
  }

  @Test
  fun debtorFilterDisplaysOnlyPersonsWithReceivables() {
    setupSampleData()

    composeRule.setContent {
      PersonsScreen(
        personViewModel = viewModel,
        initialDirectionFilter = LoanDirectionFilter.DEBTOR
      )
    }
    settle()

    composeRule.onNodeWithContentDescription(rowDescription(NAME_ALI, LABEL_DEBTOR, 100_000L)).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_REZA, LABEL_CREDITOR, -200_000L)).assertDoesNotExist()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_SARA, LABEL_BALANCED, 0L)).assertDoesNotExist()
  }

  @Test
  fun creditorFilterDisplaysOnlyPersonsWithDebts() {
    setupSampleData()

    composeRule.setContent {
      PersonsScreen(
        personViewModel = viewModel,
        initialDirectionFilter = LoanDirectionFilter.CREDITOR
      )
    }
    settle()

    composeRule.onNodeWithContentDescription(rowDescription(NAME_REZA, LABEL_CREDITOR, -200_000L)).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_ALI, LABEL_DEBTOR, 100_000L)).assertDoesNotExist()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_SARA, LABEL_BALANCED, 0L)).assertDoesNotExist()
  }

  @Test
  fun settledFilterDisplaysOnlySettledPersons() {
    setupSampleData()

    composeRule.setContent {
      PersonsScreen(
        personViewModel = viewModel,
        initialDirectionFilter = LoanDirectionFilter.SETTLED
      )
    }
    settle()

    composeRule.onNodeWithContentDescription(rowDescription(NAME_SARA, LABEL_BALANCED, 0L)).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_ALI, LABEL_DEBTOR, 100_000L)).assertDoesNotExist()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_REZA, LABEL_CREDITOR, -200_000L)).assertDoesNotExist()
  }

  @Test
  fun emptyStateDisplaysWhenNoPersonsExist() {
    composeRule.setContent {
      PersonsScreen(personViewModel = viewModel)
    }
    settle()

    composeRule.onNodeWithText("هیچ شخصی ثبت نشده است.").assertIsDisplayed()
  }

  @Test
  fun clickingPersonRowInvokesCallback() {
    setupSampleData()
    var clickedId: Long? = null
    var clickedName: String? = null

    composeRule.setContent {
      PersonsScreen(
        personViewModel = viewModel,
        onPersonClick = { id, name ->
          clickedId = id
          clickedName = name
        }
      )
    }
    settle()

    composeRule
      .onNodeWithContentDescription(rowDescription(NAME_ALI, LABEL_DEBTOR, 100_000L))
      .performClick()

    assertEquals("Clicked person ID", 1L, clickedId)
    assertEquals("Clicked person name", NAME_ALI, clickedName)
  }

  @Test
  fun searchFiltersPersonsByName() {
    setupSampleData()

    composeRule.setContent {
      PersonsScreen(personViewModel = viewModel)
    }
    settle()

    composeRule
      .onNode(hasText("جستجو بر اساس نام شخص").and(hasSetTextAction()))
      .performTextInput(NAME_REZA)
    settle()

    composeRule.onNodeWithContentDescription(rowDescription(NAME_REZA, LABEL_CREDITOR, -200_000L)).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_ALI, LABEL_DEBTOR, 100_000L)).assertDoesNotExist()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_SARA, LABEL_BALANCED, 0L)).assertDoesNotExist()

    composeRule.onNodeWithContentDescription("پاک‌سازی جستجو").performClick()
    settle()

    composeRule.onNodeWithContentDescription(rowDescription(NAME_ALI, LABEL_DEBTOR, 100_000L)).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_REZA, LABEL_CREDITOR, -200_000L)).assertIsDisplayed()
    composeRule.onNodeWithContentDescription(rowDescription(NAME_SARA, LABEL_BALANCED, 0L)).assertIsDisplayed()
  }

  private companion object {
    const val NAME_ALI = "Ali"
    const val NAME_REZA = "Reza"
    const val NAME_SARA = "Sara"
    const val LABEL_DEBTOR = "بدهکار"
    const val LABEL_CREDITOR = "طلبکار"
    const val LABEL_BALANCED = "تعادل"
  }
}
