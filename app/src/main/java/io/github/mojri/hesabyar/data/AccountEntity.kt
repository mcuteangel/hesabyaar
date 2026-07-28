package io.github.mojri.hesabyar.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

enum class AccountType(
  val displayName: String
) {
  BANK("بانکی"),
  CASH_WALLET("کیف پول نقدی"),
  SAVINGS_INVESTMENT("پس\u200cانداز/سرمایه\u200cگذاری"),
  OTHER("سایر")
}

@Entity(tableName = "accounts")
data class AccountEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val type: AccountType,
  val bankName: String? = null,
  val cardNumber: String? = null,
  val accountNumber: String? = null,
  val iban: String? = null,
  val initialBalance: Long = 0L, // Rial
  val color: Long = 0xFF4CAF50L,
  val icon: String? = null,
  val isArchived: Boolean = false,
  val displayOrder: Int = 0
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L

    val DEFAULT_ACCOUNT =
      AccountEntity(
        id = 1L,
        name = "حساب اصلی",
        type = AccountType.BANK,
        initialBalance = 0L,
        displayOrder = 0
      )
  }
}
