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
import kotlinx.coroutines.flow.first
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

    /** All active (non-archived) persons with their computed net balance. */
    val personBalances: StateFlow<List<PersonBalanceCalculator.PersonBalance>> =
      combine(
        repository.allPersons,
        repository.allLoans,
        searchQuery
      ) { persons, loans, query ->
        val balances = getPersonBalancesUseCase.computePersonBalances(persons, loans)
        val q = query.trim()
        if (q.isEmpty()) {
          balances
        } else {
          val needle = q.lowercase(Locale.getDefault())
          balances.filter { it.personName.contains(needle, ignoreCase = true) }
        }
      }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

    /** Active loans for a single person. */
    fun getLoansForPerson(personId: Long): Flow<List<Loan>> =
      repository.allLoans.map { list -> list.filter { it.personId == personId } }

    /** Payment history for a single loan (chronological — newest last). */
    fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> = manageLoanUseCase.getPaymentHistory(loanId)

    /**
     * Quick-action: create a new receivable/debt for this person.
     *
     * Safety net: repository write failure must be logged instead of crashing.
     * Cancellation is rethrown to keep structured concurrency intact.
     */
    @Suppress("TooGenericExceptionCaught")
    fun addLoanForPerson(
      personId: Long,
      personName: String,
      type: LoanType,
      amount: Long,
      description: String,
      customDate: Long? = null,
    ) {
      viewModelScope.launch {
        try {
          manageLoanUseCase.addLoan(personName, type, amount, description, customDate, personId)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          AppLogger.e("PersonViewModel", "addLoanForPerson failed: ${e.message}", e)
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
    fun settleFully(personId: Long) {
      viewModelScope.launch {
        val snapshot =
          repository.allLoans.first().filter {
            it.personId == personId && !it.isSettled
          }
        snapshot.forEach { loan ->
          val remaining = loan.remainingAmount
          if (remaining > 0L) {
            try {
              manageLoanUseCase.makeRepayment(loan.id, remaining, "", null)
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              AppLogger.e("PersonViewModel", "settle loan ${loan.id} failed: ${e.message}", e)
            }
          }
        }
      }
    }

    private companion object {
      const val SUBSCRIBE_TIMEOUT_MS = 5000L
    }
  }
