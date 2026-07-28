package io.github.mojri.hesabyar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "categories")
data class Category(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val key: String,
  val icon: String,
  val color: Long,
  val type: CategoryType,
  val isDefault: Boolean = false
) : Serializable {
  companion object {
    val DEFAULTS =
      listOf(
        Category(
          name = "خوراک",
          key = "Food",
          icon = "Restaurant",
          color = 0xFF4CAF50L,
          type = CategoryType.EXPENSE,
          isDefault = true
        ),
        Category(
          name = "حمل و نقل",
          key = "Transportation",
          icon = "DirectionsCar",
          color = 0xFFFF9800L,
          type = CategoryType.EXPENSE,
          isDefault = true
        ),
        Category(
          name = "خرید",
          key = "Shopping",
          icon = "ShoppingBag",
          color = 0xFF2196F3L,
          type = CategoryType.EXPENSE,
          isDefault = true
        ),
        Category(
          name = "قبوض",
          key = "Bills",
          icon = "ReceiptLong",
          color = 0xFF009688L,
          type = CategoryType.EXPENSE,
          isDefault = true
        ),
        Category(
          name = "اقساط",
          key = "Installments",
          icon = "CreditCard",
          color = 0xFFF44336L,
          type = CategoryType.EXPENSE,
          isDefault = true
        ),
        Category(
          name = "وام و قرض",
          key = "Loans",
          icon = "HistoryEdu",
          color = 0xFF9C27B0L,
          type = CategoryType.BOTH,
          isDefault = true
        ),
        Category(
          name = "درآمد",
          key = "Income",
          icon = "Paid",
          color = 0xFF4CAF50L,
          type = CategoryType.INCOME,
          isDefault = true
        ),
        Category(
          name = "سایر",
          key = "Other",
          icon = "Paid",
          color = 0xFF757575L,
          type = CategoryType.BOTH,
          isDefault = true
        )
      )
  }
}

@Entity(tableName = "transactions")
data class Transaction(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val type: TransactionType,
  val categoryId: Long,
  // Rial
  val amount: Long,
  val description: String,
  val personName: String? = null,
  val date: Long = System.currentTimeMillis(),
  val dueDate: Long? = null,
  val installmentId: Long? = null,
  // FK to accounts (default: main account)
  val accountId: Long = 1L,
  // For internal transfers
  val destinationAccountId: Long? = null
) : Serializable

@Entity(tableName = "loans")
data class Loan(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val personName: String,
  val type: LoanType,
  // Rial
  val originalAmount: Long,
  // Rial
  val remainingAmount: Long,
  val description: String,
  val date: Long = System.currentTimeMillis(),
  val isSettled: Boolean = false
) : Serializable

@Entity(tableName = "installments")
data class Installment(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  // Rial
  val amount: Long,
  val dueDate: Long,
  val isPaid: Boolean = false,
  val reminderEnabled: Boolean = true,
  val notes: String = "",
  val bankLoanId: Long? = null
) : Serializable

@Entity(tableName = "payment_history")
data class PaymentHistory(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val loanId: Long,
  val amount: Long, // Rial
  val date: Long = System.currentTimeMillis(),
  val notes: String = ""
) : Serializable

@Entity(tableName = "bank_loans")
data class BankLoan(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val bankName: String,
  val loanName: String,
  val receivedAmount: Long, // Rial (actual disbursed amount)
  val monthlyInstallmentAmount: Long, // Rial
  val numberOfInstallments: Int,
  val totalRepayableAmount: Long, // Rial
  val totalInterest: Long, // Rial
  val startDate: Long, // epoch millis
  val description: String,
  val isSettled: Boolean = false
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }
}
