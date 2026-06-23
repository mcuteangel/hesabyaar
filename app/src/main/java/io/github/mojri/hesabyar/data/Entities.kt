package io.github.mojri.hesabyar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "EXPENSE", "INCOME"
    val category: String, // "Food", "Transportation", "Shopping", "Bills", "Installments", "Loans", "Income", "Other"
    val amount: Long, // Rial
    val description: String,
    val personName: String? = null,
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val installmentId: Long? = null
) : Serializable

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val type: String, // "DEBTOR" (you are owed money), "CREDITOR" (you owe money)
    val originalAmount: Long, // Rial
    val remainingAmount: Long, // Rial
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val isSettled: Boolean = false
) : Serializable

@Entity(tableName = "installments")
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long, // Rial
    val dueDate: Long,
    val isPaid: Boolean = false,
    val reminderEnabled: Boolean = true,
    val notes: String = ""
) : Serializable

@Entity(tableName = "payment_history")
data class PaymentHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amount: Long, // Rial
    val date: Long = System.currentTimeMillis(),
    val notes: String = ""
) : Serializable
