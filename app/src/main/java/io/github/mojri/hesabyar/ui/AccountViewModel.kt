package io.github.mojri.hesabyar.ui

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.ui.designsystem.DEFAULT_ACCOUNT_COLOR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
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
    @ApplicationContext private val context: Context,
    private val repository: HesabyarRepositoryInterface
  ) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> =
      repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Emits user-facing error messages for snackbar display. */
    private val _errorEvents =
      MutableSharedFlow<String>(
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
      )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    /** Sealed result type for addAccount to communicate validation/insert outcomes. */
    sealed class AddAccountResult {
      data object Success : AddAccountResult()

      data class ValidationError(
        val message: String
      ) : AddAccountResult()

      data class InsertError(
        val message: String
      ) : AddAccountResult()
    }

    /**
     * Sealed result type for the delete pre-check ([canDeleteAccount]) so the
     * UI can distinguish *why* an account cannot be deleted instead of inferring
     * the reason from the account list — a repository failure must not be shown
     * as a misleading "has active transactions" warning.
     */
    sealed class DeleteCheckResult {
      data object CanDelete : DeleteCheckResult()

      data object HasTransactions : DeleteCheckResult()

      data object LastActiveAccount : DeleteCheckResult()

      data class CheckFailed(
        val message: String
      ) : DeleteCheckResult()
    }

    /**
     * Creates a new account after validating the [name].
     *
     * Returns [AddAccountResult.ValidationError] immediately when the name is
     * blank.  Otherwise **awaits** the database insert and returns
     * [AddAccountResult.Success] only after the row is persisted.  Database
     * errors are emitted via [errorEvents] (for the snackbar) and reported as
     * [AddAccountResult.InsertError] so callers can keep the dialog open.
     */
    suspend fun addAccount(
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
      return try {
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
        AddAccountResult.Success
      } catch (e: CancellationException) {
        throw e
      } catch (e: SQLiteException) {
        emitAddAccountError(e, "خطای پایگاه داده")
      } catch (e: IllegalStateException) {
        emitAddAccountError(e, "خطای ناشناخته")
      }
    }

    /** Emits an error event (snackbar) and returns the insert-failure result. */
    private suspend fun emitAddAccountError(
      e: Exception,
      fallbackMessage: String
    ): AddAccountResult.InsertError {
      Log.e(TAG, "addAccount failed", e)
      val message = "خطا در ایجاد حساب: ${e.localizedMessage ?: fallbackMessage}"
      _errorEvents.emit(message)
      return AddAccountResult.InsertError(message)
    }

    fun updateAccount(account: AccountEntity) =
      runGuarded(errorPrefix = "ویرایش حساب") {
        repository.updateAccount(account.copy(updatedAt = System.currentTimeMillis()))
      }

    fun deleteAccount(account: AccountEntity) =
      runGuarded(errorPrefix = "حذف حساب") {
        // Pre-check for a user-friendly Persian message. The repository
        // also performs this check atomically within a transaction to
        // prevent TOCTOU race conditions.
        val count = repository.getTransactionCountForAccount(account.id)
        if (count > 0) {
          _errorEvents.emit(
            context.getString(
              R.string.account_delete_transaction_count_error,
              account.name,
              count
            )
          )
          return@runGuarded
        }
        val allAccounts = repository.getAllAccounts()
        val activeAccountCount = allAccounts.count { !it.isArchived }
        if (activeAccountCount == 1 && !account.isArchived) {
          _errorEvents.emit(
            context.getString(
              R.string.account_delete_last_active_account_error,
              account.name
            )
          )
          return@runGuarded
        }
        repository.deleteAccount(account)
      }

    /**
     * Pre-checks whether [accountId] may be deleted. Reports the concrete
     * reason via [onResult] — a repository failure is reported as
     * [DeleteCheckResult.CheckFailed] (with the user-facing message) instead of
     * being conflated with the transaction-block or last-account cases.
     */
    fun canDeleteAccount(
      accountId: Long,
      onResult: (DeleteCheckResult) -> Unit
    ) = runGuarded(
      errorPrefix = "بررسی حساب",
      onError = { message -> onResult(DeleteCheckResult.CheckFailed(message)) }
    ) {
      val count = repository.getTransactionCountForAccount(accountId)
      if (count > 0) {
        onResult(DeleteCheckResult.HasTransactions)
        return@runGuarded
      }
      val allAccounts = repository.getAllAccounts()
      val activeAccountCount = allAccounts.count { !it.isArchived }
      val isLastActiveAccount =
        activeAccountCount == 1 && allAccounts.firstOrNull { it.id == accountId }?.isArchived == false
      onResult(
        if (isLastActiveAccount) {
          DeleteCheckResult.LastActiveAccount
        } else {
          DeleteCheckResult.CanDelete
        }
      )
    }

    fun archiveAccount(account: AccountEntity) =
      runGuarded(errorPrefix = "بایگانی حساب") {
        repository.updateAccount(
          account.copy(isArchived = true, updatedAt = System.currentTimeMillis())
        )
      }

    /**
     * Runs [action] in the viewModelScope and converts DB-layer failures into
     * user-facing error events. [onError] receives the same user-facing message
     * when present, so callers can report *why* an operation failed rather than
     * a bare boolean. Keeps the account operations above DRY.
     */
    private fun runGuarded(
      errorPrefix: String,
      onError: ((String) -> Unit)? = null,
      action: suspend () -> Unit
    ) {
      viewModelScope.launch {
        try {
          action()
        } catch (e: CancellationException) {
          throw e
        } catch (e: SQLiteException) {
          Log.e(TAG, "$errorPrefix failed", e)
          val message = "خطا در $errorPrefix: ${e.localizedMessage ?: "خطای پایگاه داده"}"
          _errorEvents.emit(message)
          onError?.invoke(message)
        } catch (e: IllegalStateException) {
          Log.e(TAG, "$errorPrefix failed", e)
          val message = "خطا در $errorPrefix: ${e.localizedMessage ?: "خطای ناشناخته"}"
          _errorEvents.emit(message)
          onError?.invoke(message)
        }
      }
    }

    private companion object {
      const val TAG = "AccountViewModel"
    }
  }
