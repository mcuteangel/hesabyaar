package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.usecase.GetPersonBalancesUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.FakeRepository
import io.github.mojri.hesabyar.ui.PersonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
 * Component and interaction tests for [PersonDetailSheet] (plans/011 Phase 3).
 *
 * Verifies person ledger header, empty state, loan timeline with payments,
 * and quick-action dialog flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PersonDetailSheetTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val testDispatcher = StandardTestDispatcher()
  private var previousUnit: CurrencyUnit? = null
  private val personsFlow = MutableStateFlow<List<Person>>(emptyList())
  private val loansFlow = MutableStateFlow<List<Loan>>(emptyList())
  private val paymentsMap = mutableMapOf<Long, MutableStateFlow<List<PaymentHistory>>>()
  private var makeRepaymentCalls = 0

  private val fakeRepository =
    object : FakeRepository() {
      var failNextInsertLoan = false
      var failNextRepayment = false
      var delayRepayment: kotlinx.coroutines.CompletableDeferred<Unit>? = null
      override val allPersons = personsFlow
      override val allLoans = loansFlow

      override fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> =
        paymentsMap.getOrPut(loanId) { MutableStateFlow(emptyList()) }

      override suspend fun addPaymentToLoan(
        loanId: Long,
        amount: Long,
        notes: String,
        customDate: Long?
      ): Boolean {
        delayRepayment?.await()
        makeRepaymentCalls++
        if (failNextRepayment) {
          failNextRepayment = false
          return false
        }
        return true
      }

      override suspend fun insertLoan(loan: Loan): Long {
        if (failNextInsertLoan) {
          throw IllegalStateException("Simulated insert loan failure")
        }
        return 1L
      }
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
    makeRepaymentCalls = 0
    fakeRepository.failNextInsertLoan = false
    paymentsMap.clear()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    previousUnit?.let { CurrencyFormatter.setUnit(it) }
  }

  private fun settle() {
    repeat(3) {
      testDispatcher.scheduler.advanceUntilIdle()
      composeRule.waitForIdle()
    }
  }

  @Test
  fun emptyLoansDisplaysEmptyState() {
    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value = emptyList()

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(NAME_ALI).assertIsDisplayed()
    composeRule.onNodeWithText(MSG_NO_LOANS).assertIsDisplayed()
    composeRule.onNodeWithText(BTN_ADD_RECEIVABLE).assertIsDisplayed()
    composeRule.onNodeWithText(BTN_ADD_DEBT).assertIsDisplayed()
  }

  @Test
  fun loansTimelineDisplaysAmountsAndSettledBadge() {
    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value =
      listOf(
        Loan(
          id = 10L,
          personId = 1L,
          personName = NAME_ALI,
          type = LoanType.DEBTOR,
          originalAmount = 100_000L,
          remainingAmount = 40_000L,
          description = DESC_BUSINESS,
          date = 1_000L,
          isSettled = false
        ),
        Loan(
          id = 11L,
          personId = 1L,
          personName = NAME_ALI,
          type = LoanType.CREDITOR,
          originalAmount = 50_000L,
          remainingAmount = 0L,
          description = DESC_GROCERIES,
          date = 2_000L,
          isSettled = true
        )
      )

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(DESC_BUSINESS).assertIsDisplayed()
    // The second item is composed but may sit below the fold — scroll it into
    // view before asserting visibility.
    composeRule.onNodeWithText(DESC_GROCERIES).performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText(LABEL_SETTLED_BADGE).assertExists()
    composeRule.onNodeWithText(BTN_SETTLE).assertIsDisplayed()
  }

  @Test
  fun paymentHistoryDisplaysInTimelineItem() {
    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    val loan =
      Loan(
        id = 10L,
        personId = 1L,
        personName = NAME_ALI,
        type = LoanType.DEBTOR,
        originalAmount = 100_000L,
        remainingAmount = 50_000L,
        description = DESC_BUSINESS,
        date = 1_000L,
        isSettled = false
      )
    loansFlow.value = listOf(loan)

    val flow =
      MutableStateFlow(
        listOf(
          PaymentHistory(
            id = 101L,
            loanId = 10L,
            amount = 35_000L,
            date = 1_500L,
            notes = "Partial payment"
          )
        )
      )
    paymentsMap[10L] = flow

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(LABEL_PAYMENTS).assertIsDisplayed()
    composeRule.onNodeWithText(CurrencyFormatter.format(35_000L)).assertIsDisplayed()
  }

  @Test
  fun settleButtonShowsConfirmationDialogAndConfirms() {
    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value =
      listOf(
        Loan(
          id = 10L,
          personId = 1L,
          personName = NAME_ALI,
          type = LoanType.DEBTOR,
          originalAmount = 100_000L,
          remainingAmount = 100_000L,
          description = "",
          date = 1_000L,
          isSettled = false
        )
      )

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(BTN_SETTLE).performClick()
    settle()

    composeRule.onNodeWithText(DIALOG_SETTLE_TITLE).assertIsDisplayed()
    composeRule.onNodeWithText(BTN_CONFIRM_SETTLE).performClick()
    settle()

    assertEquals("Settle repayment performed", 1, makeRepaymentCalls)
  }

  @Test
  fun settleButtonDisabledWhileBatchInFlight() {
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    fakeRepository.delayRepayment = gate

    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value =
      listOf(
        Loan(
          id = 10L,
          personId = 1L,
          personName = NAME_ALI,
          type = LoanType.DEBTOR,
          originalAmount = 100_000L,
          remainingAmount = 100_000L,
          description = "",
          date = 1_000L,
          isSettled = false
        )
      )

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(BTN_SETTLE).performClick()
    settle()

    composeRule.onNodeWithText(BTN_CONFIRM_SETTLE).performClick()
    testDispatcher.scheduler.runCurrent()
    composeRule.waitForIdle()

    // While repayment is suspended at gate, the button renders loading semantics and stays disabled.
    composeRule.onNodeWithContentDescription(EXPECTED_SETTLING_DESC).assertIsNotEnabled()

    // A second confirmation or direct invocation does not start another batch
    viewModel.settleFully(1L)
    testDispatcher.scheduler.runCurrent()
    composeRule.waitForIdle()

    // Release the gate and allow repayment to finish.
    gate.complete(Unit)
    settle()

    assertEquals("Only one repayment batch completed", 1, makeRepaymentCalls)
  }

  @Test
  fun settleFailureRestoresSettleActionAndShowsError() {
    fakeRepository.failNextRepayment = true

    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value =
      listOf(
        Loan(
          id = 10L,
          personId = 1L,
          personName = NAME_ALI,
          type = LoanType.DEBTOR,
          originalAmount = 100_000L,
          remainingAmount = 100_000L,
          description = "",
          date = 1_000L,
          isSettled = false
        )
      )

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(BTN_SETTLE).performClick()
    settle()

    composeRule.onNodeWithText(BTN_CONFIRM_SETTLE).performClick()
    settle()

    // Settle error is displayed
    composeRule.onNodeWithText(MSG_SETTLE_FAILED).assertIsDisplayed()

    // Settle button is restored and enabled for retry
    composeRule.onNodeWithText(BTN_SETTLE).assertIsEnabled()
  }

  @Test
  fun addReceivableButtonOpensDialog() {
    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value = emptyList()

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(BTN_ADD_RECEIVABLE).performClick()
    settle()

    composeRule.onNodeWithText(BTN_SUBMIT).assertIsDisplayed()
    composeRule.onNodeWithText(BTN_CANCEL).assertIsDisplayed()
  }

  @Test
  fun addDebtButtonOpensDialog() {
    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value = emptyList()

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(BTN_ADD_DEBT).performClick()
    settle()

    composeRule.onNodeWithText(BTN_SUBMIT).assertIsDisplayed()
    composeRule.onNodeWithText(BTN_CANCEL).assertIsDisplayed()
  }

  @Test
  fun addLoanSubmissionFailureDisplaysErrorAndAllowsRetry() {
    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value = emptyList()
    fakeRepository.failNextInsertLoan = true

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(BTN_ADD_RECEIVABLE).performClick()
    settle()

    composeRule
      .onNode(hasText(amountInputLabel()).and(hasSetTextAction()))
      .performTextInput(TEST_AMOUNT_1000)

    composeRule.onNodeWithText(BTN_SUBMIT).assertIsEnabled()
    composeRule.onNodeWithText(BTN_SUBMIT).performClick()
    settle()

    composeRule.onNodeWithText(MSG_ADD_LOAN_FAILED).assertIsDisplayed()
    composeRule.onNodeWithText(BTN_SUBMIT).assertIsEnabled()

    composeRule.onNodeWithText(BTN_SUBMIT).performClick()
    settle()

    composeRule.onNodeWithText(MSG_ADD_LOAN_FAILED).assertIsDisplayed()
    composeRule.onNodeWithText(BTN_SUBMIT).assertIsEnabled()
  }

  @Test
  fun dismissingAddLoanDialogClosesDialogAndClearsState() {
    personsFlow.value = listOf(Person(id = 1L, name = NAME_ALI, normalizedName = NORMALIZED_NAME_ALI))
    loansFlow.value = emptyList()

    composeRule.setContent {
      PersonDetailSheet(
        personId = 1L,
        personName = NAME_ALI,
        personViewModel = viewModel,
        onDismiss = {}
      )
    }
    settle()

    composeRule.onNodeWithText(BTN_ADD_RECEIVABLE).performClick()
    settle()

    composeRule.onNodeWithText(BTN_CANCEL).performClick()
    settle()

    composeRule.onNodeWithText(BTN_SUBMIT).assertDoesNotExist()
  }

  private companion object {
    const val NAME_ALI = "Ali"
    const val NORMALIZED_NAME_ALI = "ali"
    const val MSG_NO_LOANS = "هیچ وامی برای این شخص ثبت نشده است."
    const val BTN_ADD_RECEIVABLE = "ثبت طلب"
    const val BTN_ADD_DEBT = "ثبت بدهی"
    const val BTN_SETTLE = "تسویه"
    const val EXPECTED_SETTLING_DESC = "تسویه، در حال تسویه وام‌ها"
    const val BTN_CONFIRM_SETTLE = "تسویه کن"
    const val BTN_SUBMIT = "ثبت"
    const val BTN_CANCEL = "انصراف"
    const val DIALOG_SETTLE_TITLE = "تسویه کامل"
    const val LABEL_SETTLED_BADGE = "(تسویه شده)"
    const val LABEL_PAYMENTS = "پرداخت‌ها:"
    const val DESC_BUSINESS = "بابت کسب‌وکار"
    const val DESC_GROCERIES = "خرید مایحتاج"
    const val MSG_ADD_LOAN_FAILED = "ثبت ناموفق بود. دوباره تلاش کنید."
    const val MSG_SETTLE_FAILED = "تسویه برخی از وام‌ها ناموفق بود."
    const val TEST_AMOUNT_1000 = "1000"

    fun amountInputLabel() = "مبلغ (${CurrencyFormatter.unitLabel})"
  }
}
