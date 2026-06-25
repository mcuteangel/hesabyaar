package io.github.mojri.hesabyar.data

import kotlinx.coroutines.flow.Flow

interface HesabyarRepositoryInterface {
    val allTransactions: Flow<List<Transaction>>
    val allLoans: Flow<List<Loan>>
    val allInstallments: Flow<List<Installment>>
    val allCategories: Flow<List<Category>>

    fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>>
    fun getCategoriesByType(type: String): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
    suspend fun getCategoryByKey(key: String): Category?
    suspend fun insertCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun deleteTransaction(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun insertLoan(loan: Loan): Long
    suspend fun updateLoan(loan: Loan)
    suspend fun deleteLoan(loan: Loan)
    fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>>
    suspend fun addPaymentToLoan(loanId: Long, amount: Long, notes: String, customDate: Long? = null): Boolean
    suspend fun insertInstallment(installment: Installment): Long
    suspend fun updateInstallment(installment: Installment)
    suspend fun deleteInstallment(installment: Installment)
    suspend fun importBackup(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        paymentHistories: List<PaymentHistory>
    )

    suspend fun replaceAllFromBackup(backup: BackupPayload)
    suspend fun mergeFromBackup(backup: BackupPayload)
    suspend fun getAllPaymentHistories(): List<PaymentHistory>
}
