package io.github.mojri.hesabyar.data

import kotlinx.coroutines.flow.Flow

class HesabyarRepository(
    private val transactionDao: TransactionDao,
    private val loanDao: LoanDao,
    private val installmentDao: InstallmentDao,
    private val paymentHistoryDao: PaymentHistoryDao,
    private val categoryDao: CategoryDao
) : HesabyarRepositoryInterface {
    override val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    override val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
    override val allInstallments: Flow<List<Installment>> = installmentDao.getAllInstallments()
    override val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    override fun getTransactionsInRange(start: Long, end: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsInRange(start, end)
    }

    override fun getCategoriesByType(type: String): Flow<List<Category>> {
        return categoryDao.getCategoriesByType(type)
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getCategoryById(id)
    }

    override suspend fun getCategoryByKey(key: String): Category? {
        return categoryDao.getCategoryByKey(key)
    }

    override suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category)
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category)
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
    }

    override suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction)
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }

    // Loans and Payments logic combined
    override suspend fun insertLoan(loan: Loan): Long {
        return loanDao.insertLoan(loan)
    }

    override suspend fun updateLoan(loan: Loan) {
        loanDao.updateLoan(loan)
    }

    override suspend fun deleteLoan(loan: Loan) {
        loanDao.deleteLoan(loan)
    }

    override fun getPaymentHistoryForLoan(loanId: Long): Flow<List<PaymentHistory>> {
        return paymentHistoryDao.getPaymentHistoryForLoan(loanId)
    }

    override suspend fun addPaymentToLoan(loanId: Long, amount: Long, notes: String): Boolean {
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
        val loansCategory = getCategoryByKey("Loans")
        val desc = if (loan.type == "CREDITOR") {
            "بازپرداخت بدهی به ${loan.personName} - $notes"
        } else {
            "دریافت بازپرداخت از ${loan.personName} - $notes"
        }

        insertTransaction(Transaction(
            type = if (loan.type == "CREDITOR") "EXPENSE" else "INCOME",
            categoryId = loansCategory?.id ?: 1L,
            amount = amount,
            description = desc,
            personName = loan.personName
        ))

        return true
    }

    // Installments
    override suspend fun insertInstallment(installment: Installment): Long {
        return installmentDao.insertInstallment(installment)
    }

    override suspend fun updateInstallment(installment: Installment) {
        installmentDao.updateInstallment(installment)
        // If paid, create an associated transaction!
        if (installment.isPaid) {
            val installmentsCategory = getCategoryByKey("Installments")
            insertTransaction(Transaction(
                type = "EXPENSE",
                categoryId = installmentsCategory?.id ?: 1L,
                amount = installment.amount,
                description = "پرداخت قسط: ${installment.title} - ${installment.notes}"
            ))
        }
    }

    override suspend fun deleteInstallment(installment: Installment) {
        installmentDao.deleteInstallment(installment)
    }

    // Backup & Restore structure
    override suspend fun importBackup(
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
