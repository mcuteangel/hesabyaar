package io.github.mojri.hesabyar.data

import androidx.room.TypeConverter

enum class TransactionType { EXPENSE, INCOME }

enum class CategoryType { EXPENSE, INCOME, BOTH }

enum class LoanType { DEBTOR, CREDITOR }

class TypeConverters {
  @TypeConverter
  fun transactionTypeToString(type: TransactionType?): String? = type?.name

  @TypeConverter
  fun stringToTransactionType(value: String?): TransactionType? = value?.let { TransactionType.valueOf(it) }

  @TypeConverter
  fun categoryTypeToString(type: CategoryType?): String? = type?.name

  @TypeConverter
  fun stringToCategoryType(value: String?): CategoryType? = value?.let { CategoryType.valueOf(it) }

  @TypeConverter
  fun loanTypeToString(type: LoanType?): String? = type?.name

  @TypeConverter
  fun stringToLoanType(value: String?): LoanType? = value?.let { LoanType.valueOf(it) }
}
