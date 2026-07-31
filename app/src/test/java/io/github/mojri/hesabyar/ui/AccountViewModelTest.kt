package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
  private lateinit var viewModel: AccountViewModel
  private lateinit var fakeRepo: AccountFakeRepository
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    fakeRepo = AccountFakeRepository()
    viewModel = AccountViewModel(fakeRepo)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun addAccountWithBlankNameReturnsValidationError() {
    val result =
      viewModel.addAccount(
        name = "",
        type = AccountType.BANK
      )
    assertTrue(result is AccountViewModel.AddAccountResult.ValidationError)
  }

  @Test
  fun addAccountWithBlankNameDoesNotInsert() =
    runTest {
      viewModel.addAccount(name = "  ", type = AccountType.CASH_WALLET)
      advanceUntilIdle()
      assertEquals(0, fakeRepo.accountsList.size)
    }

  @Test
  fun addAccountSuccessInsertsAccount() =
    runTest {
      val result =
        viewModel.addAccount(
          name = ".test account",
          type = AccountType.BANK,
          initialBalance = 1000L
        )
      assertTrue(result is AccountViewModel.AddAccountResult.Success)
      advanceUntilIdle()
      assertEquals(1, fakeRepo.accountsList.size)
      assertEquals(".test account", fakeRepo.accountsList[0].name)
      assertEquals(1000L, fakeRepo.accountsList[0].initialBalance)
    }

  @Test
  fun addAccountSetsCorrectDisplayOrder() =
    runTest {
      fakeRepo.accountsList.add(
        AccountEntity(id = 1, name = "existing", type = AccountType.BANK, displayOrder = 5)
      )
      fakeRepo.refreshAccounts()

      viewModel.addAccount(name = "new account", type = AccountType.CASH_WALLET)
      advanceUntilIdle()

      assertEquals(2, fakeRepo.accountsList.size)
      val newAccount = fakeRepo.accountsList.first { it.name == "new account" }
      assertEquals(6, newAccount.displayOrder)
    }

  @Test
  fun addAccountSetsTimestamps() =
    runTest {
      val before = System.currentTimeMillis()
      viewModel.addAccount(name = ".timestamp test", type = AccountType.OTHER)
      advanceUntilIdle()
      val after = System.currentTimeMillis()

      assertEquals(1, fakeRepo.accountsList.size)
      val account = fakeRepo.accountsList[0]
      assertTrue(account.createdAt in before..after)
      assertTrue(account.updatedAt in before..after)
    }

  @Test
  fun updateAccountSetsNewUpdatedAt() =
    runTest {
      val original =
        AccountEntity(
          id = 10,
          name = "old",
          type = AccountType.BANK,
          updatedAt = 100L
        )
      fakeRepo.accountsList.add(original)
      fakeRepo.refreshAccounts()

      viewModel.updateAccount(original.copy(name = "new"))
      advanceUntilIdle()

      val updated = fakeRepo.accountsList.first { it.id == 10L }
      assertEquals("new", updated.name)
      assertTrue(updated.updatedAt > 100L)
    }

  @Test
  fun deleteAccountRemovesFromRepository() =
    runTest {
      val account = AccountEntity(id = 5, name = "to delete", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()

      viewModel.deleteAccount(account)
      advanceUntilIdle()

      assertTrue(fakeRepo.accountsList.none { it.id == 5L })
    }

  @Test
  fun canDeleteAccountReturnsTrueWhenNoTransactions() =
    runTest {
      var result = false
      viewModel.canDeleteAccount(1L) { result = it }
      advanceUntilIdle()
      assertTrue(result)
    }

  @Test
  fun canDeleteAccountReturnsFalseWhenTransactionsExist() =
    runTest {
      fakeRepo.transactionCount = 3
      var result = true
      viewModel.canDeleteAccount(1L) { result = it }
      advanceUntilIdle()
      assertEquals(false, result)
    }

  @Test
  fun archiveAccountSetsArchivedFlag() =
    runTest {
      val account = AccountEntity(id = 7, name = "archivable", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()

      viewModel.archiveAccount(account)
      advanceUntilIdle()

      val archived = fakeRepo.accountsList.first { it.id == 7L }
      assertTrue(archived.isArchived)
    }

  @Test
  fun addAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnInsert = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      viewModel.addAccount(name = "fail test", type = AccountType.BANK)
      advanceUntilIdle()

      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      job.cancel()
    }

  @Test
  fun updateAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnUpdate = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      val account = AccountEntity(id = 1, name = "test", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()
      viewModel.updateAccount(account)
      advanceUntilIdle()

      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      job.cancel()
    }

  @Test
  fun deleteAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnDelete = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      val account = AccountEntity(id = 1, name = "test", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()
      viewModel.deleteAccount(account)
      advanceUntilIdle()

      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      job.cancel()
    }

  @Test
  fun canDeleteAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnTransactionCount = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      var result = true
      viewModel.canDeleteAccount(1L) { result = it }
      advanceUntilIdle()

      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      assertEquals(false, result)
      job.cancel()
    }

  @Test
  fun archiveAccountErrorEventEmittedOnRepositoryFailure() =
    runTest {
      fakeRepo.shouldThrowOnUpdate = true
      val errorMessages = mutableListOf<String>()
      val job =
        launch {
          viewModel.errorEvents.collect { errorMessages.add(it) }
        }

      val account = AccountEntity(id = 1, name = "test", type = AccountType.BANK)
      fakeRepo.accountsList.add(account)
      fakeRepo.refreshAccounts()
      viewModel.archiveAccount(account)
      advanceUntilIdle()

      assertTrue(errorMessages.isNotEmpty())
      assertTrue(errorMessages[0].contains("خطا"))
      job.cancel()
    }

  private class AccountFakeRepository : HesabyarRepositoryInterface {
    val accountsList = mutableListOf<AccountEntity>()
    private val _allAccounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    var transactionCount = 0
    var shouldThrowOnInsert = false
    var shouldThrowOnUpdate = false
    var shouldThrowOnDelete = false
    var shouldThrowOnTransactionCount = false

    fun refreshAccounts() {
      _allAccounts.value = accountsList.toList()
    }

    override val allTransactions: Flow<List<Transaction>> = flowOf(emptyList())
    override val allLoans: Flow<List<Loan>> = flowOf(emptyList())
    override val allInstallments: Flow<List<Installment>> = flowOf(emptyList())
    override val allCategories: Flow<List<Category>> = flowOf(emptyList())
    override val allBankLoans: Flow<List<BankLoan>> = flowOf(emptyList())
    override val allAccounts: Flow<List<AccountEntity>> = _allAccounts.asStateFlow()

    override fun getTransactionsInRange(
      start: Long,
      end: Long
    ): Flow<List<Transaction>> = flowOf(emptyList())

    override fun getCategoriesByType(type: String): Flow<List<Category>> = flowOf(emptyList())

    override suspend fun getCategoryById(id: Long): Category? = null

    override suspend fun getCategoryByKey(key: String): Category? = null

    override suspend fun insertCategory(category: Category): Long = 0L

    override suspend fun updateCategory(category: Category) {}

    override suspend fun deleteCategory(category: Category) {}

    override suspend fun insertTransaction(transaction: Transaction): Long = 0L

    override suspend fun deleteTransaction(transaction: Transaction) {}

    override suspend fun updateTransaction(transaction: Transaction) {}

    override suspend fun insertLoan(loan: Loan): Long = 0L

    override suspend fun updateLoan(loan: Loan) {}

    override suspend fun deleteLoan(loan: Loan) {}

    override fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> = flowOf(emptyList())

    override suspend fun addPaymentToLoan(
      loanId: Long,
      amount: Long,
      notes: String,
      customDate: Long?
    ): Boolean = false

    override suspend fun insertInstallment(installment: Installment): Long = 0L

    override suspend fun updateInstallment(installment: Installment) {}

    override suspend fun deleteInstallment(installment: Installment) {}

    override suspend fun getBankLoanById(id: Long): BankLoan? = null

    override suspend fun insertBankLoan(bankLoan: BankLoan): Long = 0L

    override suspend fun updateBankLoan(bankLoan: BankLoan) {}

    override suspend fun deleteBankLoan(bankLoan: BankLoan) {}

    override suspend fun getInstallmentsByBankLoanId(bankLoanId: Long): List<Installment> = emptyList()

    override suspend fun addBankLoanWithInstallments(
      bankLoan: BankLoan,
      installments: List<Installment>
    ): Long = 0L

    override suspend fun importBackup(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      paymentHistories: List<PaymentHistory>,
      bankLoans: List<BankLoan>
    ) {
    }

    override suspend fun replaceAllFromBackup(backup: BackupPayload) {}

    override suspend fun mergeFromBackup(backup: BackupPayload) {}

    override suspend fun getAllPaymentHistories(): List<PaymentHistory> = emptyList()

    override suspend fun getActiveAccounts(): List<AccountEntity> = accountsList.filter { !it.isArchived }

    override suspend fun getAllAccounts(): List<AccountEntity> = accountsList.toList()

    override suspend fun getAccountById(id: Long): AccountEntity? = accountsList.firstOrNull { it.id == id }

    override suspend fun insertAccount(account: AccountEntity): Long {
      if (shouldThrowOnInsert) throw IllegalStateException("Simulated DB failure")
      val id = if (account.id != 0L) account.id else 1L
      accountsList.add(account.copy(id = id))
      refreshAccounts()
      return id
    }

    override suspend fun updateAccount(account: AccountEntity) {
      if (shouldThrowOnUpdate) throw IllegalStateException("Simulated DB failure")
      val idx = accountsList.indexOfFirst { it.id == account.id }
      if (idx >= 0) {
        accountsList[idx] = account
        refreshAccounts()
      }
    }

    override suspend fun deleteAccount(account: AccountEntity) {
      if (shouldThrowOnDelete) throw IllegalStateException("Simulated DB failure")
      accountsList.removeIf { it.id == account.id }
      refreshAccounts()
    }

    override suspend fun getTransactionCountForAccount(accountId: Long): Int {
      if (shouldThrowOnTransactionCount) throw IllegalStateException("Simulated DB failure")
      return transactionCount
    }

    override suspend fun getMaxDisplayOrder(): Int = accountsList.maxOfOrNull { it.displayOrder } ?: -1
  }
}
