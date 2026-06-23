package io.github.mojri.hesabyar.data

import kotlinx.coroutines.flow.Flow
import java.io.Serializable

class HesabyarRepository(
    private val transactionDao: TransactionDao,
    private val loanDao: LoanDao,
    private val installmentDao: InstallmentDao,
    private val paymentHistoryDao: PaymentHistoryDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
    val allInstallments: Flow<List<Installment>> = installmentDao.getAllInstallments()

    fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsInRange(start, end)
    }

    suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }

    // Loans and Payments logic combined
    suspend fun insertLoan(loan: Loan): Long {
        return loanDao.insertLoan(loan)
    }

    suspend fun updateLoan(loan: Loan) {
        loanDao.updateLoan(loan)
    }

    suspend fun deleteLoan(loan: Loan) {
        loanDao.deleteLoan(loan)
    }

    fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> {
        return paymentHistoryDao.getPaymentHistoryForLoan(loanId)
    }

    suspend fun addPaymentToLoan(loanId: Long, amount: Long, notes: String): Boolean {
        val loan = loanDao.getLoanById(loanId) ?: return false
        val newRemaining = (loan.remainingAmount - amount).coerceAtLeast(0L)
        val isSettled = newRemaining <= 0L

        updateLoan(loan.copy(
            remainingAmount = newRemaining,
            isSettled = isSettled
        ))

        paymentHistoryDao.insertPayment(PaymentHistory(
            loanId = loanId,
            amount = amount,
            notes = notes
        ))

        // Create an associated transaction matching this repayment
        val categoryName = "Loans"
        val desc = if (loan.type == "CREDITOR") {
            "بازپرداخت بدهی به ${loan.personName} - $notes"
        } else {
            "دریافت بازپرداخت از ${loan.personName} - $notes"
        }

        insertTransaction(Transaction(
            type = if (loan.type == "CREDITOR") "EXPENSE" else "INCOME",
            category = categoryName,
            amount = amount,
            description = desc,
            personName = loan.personName
        ))

        return true
    }

    // Installments
    suspend fun insertInstallment(installment: Installment): Long {
        return installmentDao.insertInstallment(installment)
    }

    suspend fun updateInstallment(installment: Installment) {
        installmentDao.updateInstallment(installment)
        // If paid, create an associated transaction!
        if (installment.isPaid) {
            insertTransaction(Transaction(
                type = "EXPENSE",
                category = "Installments",
                amount = installment.amount,
                description = "پرداخت قسط: ${installment.title} - ${installment.notes}"
            ))
        }
    }

    suspend fun deleteInstallment(installment: Installment) {
        installmentDao.deleteInstallment(installment)
    }

    // Backup & Restore structure
    suspend fun importBackup(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        paymentHistories: List<PaymentHistory>
    ) {
        transactionDao.deleteAllTransactions()
        loanDao.deleteAllLoans()
        installmentDao.deleteAllInstallments()
        paymentHistoryDao.deleteAllPaymentHistory()

        transactions.forEach { transactionDao.insertTransaction(it) }
        loans.forEach { loanDao.insertLoan(it) }
        installments.forEach { installmentDao.insertInstallment(it) }
        paymentHistories.forEach { paymentHistoryDao.insertPayment(it) }
    }
}
