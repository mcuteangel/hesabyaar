package io.github.mojri.hesabyar.ui

import android.content.Context
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private lateinit var viewModel: BackupViewModel
    private lateinit var fakeRepo: FakeRepository
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        fakeRepo = FakeRepository()
        val useCase = ManageBackupUseCase(fakeRepo)
        viewModel = BackupViewModel(context, useCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `importBackupFromFile success sets ImportSuccess`() = runTest {
        val json = """
            {
                "version": 1,
                "transactions": [{"type": "EXPENSE", "categoryId": 1, "amount": 1000, "description": "test"}],
                "loans": [],
                "installments": [],
                "paymentHistories": []
            }
        """.trimIndent()

        viewModel.importBackupFromFile(ByteArrayInputStream(json.toByteArray()))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.operationState.value
        assertTrue("Expected ImportSuccess but got $state", state is BackupOperationState.ImportSuccess)
    }

    @Test
    fun `importBackupFromFile IOException sets Error`() = runTest {
        val inputStream = object : InputStream() {
            override fun read(): Int = throw IOException("disk read failed")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("disk read failed")
        }

        viewModel.importBackupFromFile(inputStream)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.operationState.value
        assertTrue("Expected Error but got $state", state is BackupOperationState.Error)
        assertTrue((state as BackupOperationState.Error).message.contains("خواندن فایل پشتیبان"))
    }

    @Test
    fun `importBackupFromFile JSONException sets Error`() = runTest {
        val badJson = "this is not json"

        viewModel.importBackupFromFile(ByteArrayInputStream(badJson.toByteArray()))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.operationState.value
        assertTrue("Expected Error but got $state", state is BackupOperationState.Error)
        assertTrue((state as BackupOperationState.Error).message.contains("تجزیه فایل پشتیبان"))
    }

    @Test
    fun `importBackupFromFile IllegalStateException sets Error`() = runTest {
        fakeRepo.importShouldThrow = IllegalStateException("UNIQUE constraint failed")

        val json = """
            {
                "version": 1,
                "transactions": [{"type": "EXPENSE", "categoryId": 1, "amount": 500, "description": "dup"}],
                "loans": [],
                "installments": [],
                "paymentHistories": []
            }
        """.trimIndent()

        viewModel.importBackupFromFile(ByteArrayInputStream(json.toByteArray()))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.operationState.value
        assertTrue("Expected Error but got $state", state is BackupOperationState.Error)
        assertTrue((state as BackupOperationState.Error).message.contains("وارد کردن پشتیبان"))
    }

    private class FakeRepository : HesabyarRepositoryInterface {
        var importShouldThrow: Exception? = null

        override val allTransactions: Flow<List<Transaction>> = flowOf(emptyList())
        override val allLoans: Flow<List<Loan>> = flowOf(emptyList())
        override val allInstallments: Flow<List<Installment>> = flowOf(emptyList())
        override val allCategories: Flow<List<Category>> = flowOf(emptyList())

        override fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> = flowOf(emptyList())
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
        override suspend fun addPaymentToLoan(loanId: Long, amount: Long, notes: String, customDate: Long?): Boolean = false
        override suspend fun insertInstallment(installment: Installment): Long = 0L
        override suspend fun updateInstallment(installment: Installment) {}
        override suspend fun deleteInstallment(installment: Installment) {}
        override suspend fun importBackup(
            transactions: List<Transaction>,
            loans: List<Loan>,
            installments: List<Installment>,
            paymentHistories: List<PaymentHistory>
        ) {
            importShouldThrow?.let { throw it }
        }
        override suspend fun replaceAllFromBackup(backup: BackupPayload) {}
        override suspend fun mergeFromBackup(backup: BackupPayload) {}
        override suspend fun getAllPaymentHistories(): List<PaymentHistory> = emptyList()
    }
}
