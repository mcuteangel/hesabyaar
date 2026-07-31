package io.github.mojri.hesabyar.ui

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.ui.designsystem.DEFAULT_ACCOUNT_COLOR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** Emits user-facing error messages for snackbar display. */
    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    /** Sealed result type for addAccount to communicate validation errors. */
    sealed class AddAccountResult {
      data object Success : AddAccountResult()

      data class ValidationError(
        val message: String
      ) : AddAccountResult()
    }

    fun addAccount(
      name: String,
      type: AccountType,
      bankName: String? = null,
      cardNumber: String? = null,
      accountNumber: String? = null,
      iban: String? = null,
      initialBalance: Long = 0L,
      color: Long = DEFAULT_ACCOUNT_COLOR,
    ): AddAccountResult {
      if (name.isBlank()) {
        return AddAccountResult.ValidationError("نام حساب نمی‌تواند خالی باشد")
      }
      viewModelScope.launch {
        try {
          val now = System.currentTimeMillis()
          // TODO(#152): TOCTOU — read then write is not atomic. Low risk due to
          // SQLite single-writer serialization, but not architecturally guaranteed.
          val nextOrder = repository.getMaxDisplayOrder() + 1
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
              displayOrder = nextOrder,
              createdAt = now,
              updatedAt = now
            )
          repository.insertAccount(account)
        } catch (e: CancellationException) {
          throw e
        } catch (e: SQLiteException) {
          Log.e(TAG, "addAccount failed", e)
          _errorEvents.emit("خطا در ایجاد حساب: ${e.localizedMessage ?: "خطای پایگاه داده"}")
        } catch (e: IllegalStateException) {
          Log.e(TAG, "addAccount failed", e)
          _errorEvents.emit("خطا در ایجاد حساب: ${e.localizedMessage ?: "خطای ناشناخته"}")
        }
      }
      return AddAccountResult.Success
    }

    fun updateAccount(account: AccountEntity) {
      viewModelScope.launch {
        try {
          repository.updateAccount(account.copy(updatedAt = System.currentTimeMillis()))
        } catch (e: CancellationException) {
          throw e
        } catch (e: SQLiteException) {
          Log.e(TAG, "updateAccount failed", e)
          _errorEvents.emit("خطا در ویرایش حساب: ${e.localizedMessage ?: "خطای پایگاه داده"}")
        } catch (e: IllegalStateException) {
          Log.e(TAG, "updateAccount failed", e)
          _errorEvents.emit("خطا در ویرایش حساب: ${e.localizedMessage ?: "خطای ناشناخته"}")
        }
      }
    }

    fun deleteAccount(account: AccountEntity) {
      viewModelScope.launch {
        try {
          repository.deleteAccount(account)
        } catch (e: CancellationException) {
          throw e
        } catch (e: SQLiteException) {
          Log.e(TAG, "deleteAccount failed", e)
          _errorEvents.emit("خطا در حذف حساب: ${e.localizedMessage ?: "خطای پایگاه داده"}")
        } catch (e: IllegalStateException) {
          Log.e(TAG, "deleteAccount failed", e)
          _errorEvents.emit("خطا در حذف حساب: ${e.localizedMessage ?: "خطای ناشناخته"}")
        }
      }
    }

    fun canDeleteAccount(
      accountId: Long,
      onResult: (Boolean) -> Unit
    ) {
      viewModelScope.launch {
        try {
          val count = repository.getTransactionCountForAccount(accountId)
          onResult(count == 0)
        } catch (e: CancellationException) {
          throw e
        } catch (e: SQLiteException) {
          Log.e(TAG, "canDeleteAccount failed", e)
          _errorEvents.emit("خطا در بررسی حساب: ${e.localizedMessage ?: "خطای پایگاه داده"}")
          onResult(false)
        } catch (e: IllegalStateException) {
          Log.e(TAG, "canDeleteAccount failed", e)
          _errorEvents.emit("خطا در بررسی حساب: ${e.localizedMessage ?: "خطای ناشناخته"}")
          onResult(false)
        }
      }
    }

    fun archiveAccount(account: AccountEntity) {
      viewModelScope.launch {
        try {
          repository.updateAccount(
            account.copy(isArchived = true, updatedAt = System.currentTimeMillis())
          )
        } catch (e: CancellationException) {
          throw e
        } catch (e: SQLiteException) {
          Log.e(TAG, "archiveAccount failed", e)
          _errorEvents.emit("خطا در بایگانی حساب: ${e.localizedMessage ?: "خطای پایگاه داده"}")
        } catch (e: IllegalStateException) {
          Log.e(TAG, "archiveAccount failed", e)
          _errorEvents.emit("خطا در بایگانی حساب: ${e.localizedMessage ?: "خطای ناشناخته"}")
        }
      }
    }

    private companion object {
      const val TAG = "AccountViewModel"
    }
  }
