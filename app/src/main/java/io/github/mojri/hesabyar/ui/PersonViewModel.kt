package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.domain.usecase.GetPersonBalancesUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import io.github.mojri.hesabyar.domain.utils.PersonBalanceCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PersonViewModel
  @Inject
  constructor(
    private val repository: HesabyarRepositoryInterface,
    private val manageLoanUseCase: ManageLoanUseCase,
    private val getPersonBalancesUseCase: GetPersonBalancesUseCase,
  ) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
      _searchQuery.value = query
    }

    /** Raw computed balances from persons and loans, isolated from search keystrokes. */
    private val rawBalances: Flow<List<PersonBalanceCalculator.PersonBalance>> =
      combine(
        repository.allPersons.distinctUntilChanged(),
        repository.allLoans.distinctUntilChanged()
      ) { persons, loans ->
        getPersonBalancesUseCase.computePersonBalances(persons, loans)
      }

    /** All active (non-archived) persons with their computed net balance and search filter. */
    val personBalances: StateFlow<List<PersonBalanceCalculator.PersonBalance>> =
      combine(
        rawBalances,
        searchQuery
      ) { balances, query ->
        val q = query.trim()
        if (q.isEmpty()) {
          balances
        } else {
          val needle = q.lowercase(Locale.getDefault())
          balances.filter { it.personName.contains(needle, ignoreCase = true) }
        }
      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    /** Net balance position for a single person, independent of search filter. */
    fun getBalanceForPerson(personId: Long): Flow<PersonBalanceCalculator.PersonBalance?> =
      rawBalances.map { list -> list.firstOrNull { it.personId == personId } }

    /** Active loans for a single person in chronological order (oldest first). */
    fun getLoansForPerson(personId: Long): Flow<List<Loan>> =
      repository.allLoans.map { list ->
        list.filter { it.personId == personId }.sortedBy { it.date }
      }

    /** Payment history for a single loan (chronological — newest last). */
    fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> = manageLoanUseCase.getPaymentHistory(loanId)

    /**
     * Quick-action: create a new receivable/debt for this person.
     *
     * Safety net: repository write failure must be logged instead of crashing.
     * Cancellation is rethrown to keep structured concurrency intact.
     * [onResult] reports success so the caller can show feedback on failure.
     */
    @Suppress("TooGenericExceptionCaught")
    fun addLoanForPerson(
      personId: Long,
      personName: String,
      type: LoanType,
      amount: Long,
      description: String,
      customDate: Long? = null,
      onResult: ((Boolean) -> Unit)? = null,
    ) {
      viewModelScope.launch {
        val success =
          try {
            manageLoanUseCase.addLoan(personName, type, amount, description, customDate, personId)
            true
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            AppLogger.e(TAG, "addLoanForPerson failed", e)
            false
          }
        try {
          onResult?.invoke(success)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          AppLogger.e(TAG, "addLoanForPerson onResult callback threw", e)
        }
      }
    }

    /**
     * Quick-action: settle all of a person's active loans in one go.
     *
     * Safety net: repository write failure must be logged instead of crashing.
     * Cancellation is rethrown to keep structured concurrency intact.
     */
    @Suppress("TooGenericExceptionCaught")
    fun settleFully(
      personId: Long,
      onResult: ((Boolean) -> Unit)? = null,
    ) {
      viewModelScope.launch {
        var overallSuccess = true
        try {
          val snapshot =
            (repository.allLoans.firstOrNull() ?: emptyList()).filter {
              it.personId == personId && !it.isSettled
            }
          snapshot.forEach { loan ->
            if (!repayLoanSafely(loan)) {
              overallSuccess = false
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          AppLogger.e(TAG, "settleFully failed", e)
          overallSuccess = false
        }
        try {
          onResult?.invoke(overallSuccess)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          AppLogger.e(TAG, "settleFully onResult callback threw", e)
        }
      }
    }

    // Structured concurrency: cancellation is rethrown untouched.
    // Per-loan isolation: every other throwable (including Rust FFI Errors such
    // as UnsatisfiedLinkError) is logged and reported as a failed loan, so the
    // remaining loans in the batch still settle.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun repayLoanSafely(loan: Loan): Boolean {
      val remaining = loan.remainingAmount
      if (remaining <= 0L) return true
      return try {
        val success = manageLoanUseCase.makeRepayment(loan.id, remaining, SETTLE_REPAYMENT_NOTE, null)
        if (!success) {
          AppLogger.w(TAG, "makeRepayment returned false for loan ${loan.id}")
        }
        success
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        AppLogger.e(TAG, "makeRepayment threw for loan ${loan.id}", e)
        false
      }
    }

    private companion object {
      const val TAG = "PersonViewModel"
      const val SUBSCRIBE_TIMEOUT_MS = 5000L
      const val SETTLE_REPAYMENT_NOTE = "تسویه خودکار"
    }
  }
