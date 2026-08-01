package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.domain.usecase.FakeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
  private lateinit var fakeRepo: FakeRepository
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    fakeRepo = FakeRepository()
    viewModel = AccountViewModel(fakeRepo)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun addAccountWithBlankNameReturnsValidationError() =
    runTest {
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
      fakeRepo.transactionCountOverride = 3
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
      advanceUntilIdle()

      val result = viewModel.addAccount(name = "fail test", type = AccountType.BANK)
      advanceUntilIdle()

      assertTrue(result is AccountViewModel.AddAccountResult.InsertError)
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
}
