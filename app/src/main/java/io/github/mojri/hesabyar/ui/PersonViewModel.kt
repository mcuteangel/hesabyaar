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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonViewModel
  internal constructor(
    private val repository: HesabyarRepositoryInterface,
    private val manageLoanUseCase: ManageLoanUseCase,
    private val getPersonBalancesUseCase: GetPersonBalancesUseCase,
    private val defaultDispatcher: CoroutineDispatcher,
  ) : ViewModel() {
    @Inject
    constructor(
      repository: HesabyarRepositoryInterface,
      manageLoanUseCase: ManageLoanUseCase,
      getPersonBalancesUseCase: GetPersonBalancesUseCase,
    ) : this(repository, manageLoanUseCase, getPersonBalancesUseCase, Dispatchers.Default)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
      _searchQuery.value = query
    }

    private val _settlingPersonIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Person ids with a settle batch in flight, so the UI can disable the action. */
    val settlingPersonIds: StateFlow<Set<Long>> = _settlingPersonIds.asStateFlow()

    /** Raw computed balances from persons and loans, isolated from search keystrokes. */
    private val rawBalances: Flow<List<PersonBalanceCalculator.PersonBalance>> =
      combine(
        repository.allPersons.distinctUntilChanged(),
        repository.allLoans.distinctUntilChanged()
      ) { persons, loans ->
        getPersonBalancesUseCase.computePersonBalances(persons, loans)
      }.flowOn(defaultDispatcher)

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
          balances.filter { it.personName.contains(q, ignoreCase = true) }
        }
      }.flowOn(defaultDispatcher)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

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
    @Suppress("TooGenericExceptionCaught") // CancellationException is rethrown first for structured cancellation
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
          } catch (e: Exception) {
            // skipcq: KT-W1064
            AppLogger.e(TAG, "addLoanForPerson failed", e)
            false
          }
        if (onResult != null) {
          dispatchCallbackSafely("addLoanForPerson", onResult, success)
        }
      }
    }

    private fun tryAcquireSettleLock(personId: Long): Boolean {
      while (true) {
        val current = _settlingPersonIds.value
        if (personId in current) return false
        if (_settlingPersonIds.compareAndSet(current, current + personId)) return true
      }
    }

    private fun releaseSettleLock(personId: Long) {
      while (true) {
        val current = _settlingPersonIds.value
        if (_settlingPersonIds.compareAndSet(current, current - personId)) break
      }
    }

    /**
     * Quick-action: settle all of a person's active loans in one go.
     *
     * Safety net: repository write failure must be logged instead of crashing.
     * Cancellation is rethrown to keep structured concurrency intact.
     *
     * Contract: if a settlement batch is already running for [personId], the
     * duplicate launch is ignored and [onResult] is deliberately NOT invoked,
     * so callers are not tricked into showing false errors while the in-flight
     * batch succeeds.
     */
    @Suppress("TooGenericExceptionCaught") // CancellationException is rethrown first for structured cancellation
    fun settleFully(
      personId: Long,
      onResult: ((Boolean) -> Unit)? = null,
    ) {
      viewModelScope.launch {
        if (!tryAcquireSettleLock(personId)) {
          AppLogger.w(TAG, "settleFully already in flight for person $personId, ignoring duplicate")
          return@launch
        }
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
        } catch (e: Exception) {
          // skipcq: KT-W1064
          AppLogger.e(TAG, "settleFully failed", e)
          overallSuccess = false
        } finally {
          releaseSettleLock(personId)
        }
        if (onResult != null) {
          dispatchCallbackSafely("settleFully", onResult, overallSuccess)
        }
      }
    }

    @Suppress("TooGenericExceptionCaught") // CancellationException is rethrown first for structured cancellation
    private fun dispatchCallbackSafely(
      action: String,
      onResult: (Boolean) -> Unit,
      success: Boolean
    ) {
      try {
        onResult(success)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // skipcq: KT-W1064
        AppLogger.e(TAG, "$action onResult callback threw", e)
      }
    }

    // Structured concurrency: cancellation is rethrown untouched.
    // Fatal VM errors (Error / VirtualMachineError) bypass catch (Exception) and fail fast.
    // Per-loan isolation: non-fatal exceptions are logged and reported as a failed loan,
    // so the remaining loans in the batch still settle.
    @Suppress("TooGenericExceptionCaught") // CancellationException is rethrown first for structured cancellation
    private suspend fun repayLoanSafely(loan: Loan): Boolean {
      val remaining = loan.remainingAmount
      if (remaining <= 0L) {
        AppLogger.w(TAG, "unsettled loan ${loan.id} has remaining=$remaining; cannot settle")
        return false
      }
      return try {
        val success = manageLoanUseCase.makeRepayment(loan.id, remaining, SETTLE_REPAYMENT_NOTE, null)
        if (!success) {
          AppLogger.w(TAG, "makeRepayment returned false for loan ${loan.id}")
        }
        success
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // skipcq: KT-W1064
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
