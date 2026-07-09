package io.github.mojri.hesabyar.data

import androidx.room.TypeConverter

enum class TransactionType { EXPENSE, INCOME }

enum class CategoryType { EXPENSE, INCOME, BOTH }

enum class LoanType { DEBTOR, CREDITOR }

class TypeConverters {
  @TypeConverter
  fun transactionTypeToString(type: TransactionType?): String? = type?.name

  @TypeConverter
  fun stringToTransactionType(value: String?): TransactionType? =
    value?.let {
      try {
        TransactionType.valueOf(it)
      } catch (_: IllegalArgumentException) {
        null
      }
    }

  @TypeConverter
  fun categoryTypeToString(type: CategoryType?): String? = type?.name

  @TypeConverter
  fun stringToCategoryType(value: String?): CategoryType? =
    value?.let {
      try {
        CategoryType.valueOf(it)
      } catch (_: IllegalArgumentException) {
        null
      }
    }

  @TypeConverter
  fun loanTypeToString(type: LoanType?): String? = type?.name

  @TypeConverter
  fun stringToLoanType(value: String?): LoanType? =
    value?.let {
      try {
        LoanType.valueOf(it)
      } catch (_: IllegalArgumentException) {
        null
      }
    }
}
