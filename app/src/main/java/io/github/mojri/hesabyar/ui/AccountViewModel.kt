package io.github.mojri.hesabyar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel
  @Inject
  constructor(
    private val repository: HesabyarRepositoryInterface
  ) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> =
      repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAccount(
      name: String,
      type: AccountType,
      bankName: String? = null,
      cardNumber: String? = null,
      accountNumber: String? = null,
      iban: String? = null,
      initialBalance: Long = 0L,
      color: Long = 0xFF4CAF50L,
    ) {
      viewModelScope.launch {
        val account =
          AccountEntity(
            name = name,
            type = type,
            bankName = bankName,
            cardNumber = cardNumber,
            accountNumber = accountNumber,
            iban = iban,
            initialBalance = initialBalance,
            color = color,
            displayOrder = accounts.value.size,
          )
        repository.insertAccount(account)
      }
    }

    fun updateAccount(account: AccountEntity) {
      viewModelScope.launch { repository.updateAccount(account) }
    }

    fun deleteAccount(account: AccountEntity) {
      viewModelScope.launch {
        repository.deleteAccount(account)
      }
    }

    fun canDeleteAccount(
      accountId: Long,
      onResult: (Boolean) -> Unit
    ) {
      viewModelScope.launch {
        val count = repository.getTransactionCountForAccount(accountId)
        onResult(count == 0)
      }
    }

    fun archiveAccount(account: AccountEntity) {
      viewModelScope.launch {
        repository.updateAccount(account.copy(isArchived = true))
      }
    }
  }
