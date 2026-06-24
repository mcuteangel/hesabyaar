package io.github.mojri.hesabyar.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE type = :type OR type = 'BOTH' ORDER BY isDefault DESC, name ASC")
    fun getCategoriesByType(type: String): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE key = :key LIMIT 1")
    suspend fun getCategoryByKey(key: String): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY date DESC")
    fun getAllLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loans WHERE id = :id LIMIT 1")
    suspend fun getLoanById(id: Long): Loan?

    @Query("SELECT * FROM loans ORDER BY date DESC")
    suspend fun getAllLoansSync(): List<Loan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: Loan): Long

    @Update
    suspend fun updateLoan(loan: Loan)

    @Delete
    suspend fun deleteLoan(loan: Loan)

    @Query("DELETE FROM loans")
    suspend fun deleteAllLoans()
}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments ORDER BY dueDate ASC")
    fun getAllInstallments(): Flow<List<Installment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: Installment): Long

    @Update
    suspend fun updateInstallment(installment: Installment)

    @Delete
    suspend fun deleteInstallment(installment: Installment)

    @Query("SELECT * FROM installments ORDER BY dueDate ASC")
    suspend fun getAllInstallmentsSync(): List<Installment>

    @Query("DELETE FROM installments")
    suspend fun deleteAllInstallments()
}

@Dao
interface PaymentHistoryDao {
    @Query("SELECT * FROM payment_history WHERE loanId = :loanId ORDER BY date DESC")
    fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentHistory): Long

    @Delete
    suspend fun deletePayment(payment: PaymentHistory)

    @Query("DELETE FROM payment_history")
    suspend fun deleteAllPaymentHistory()

    @Query("SELECT * FROM payment_history ORDER BY date DESC")
    suspend fun getAllPaymentHistories(): List<PaymentHistory>
}
