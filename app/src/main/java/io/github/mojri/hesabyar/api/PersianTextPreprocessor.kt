package io.github.mojri.hesabyar.api

object PersianTextPreprocessor {

    fun preprocessPersianText(text: String): String {
        return text
            .replace("،", ",")
            .replace("٫", ".")
            .replace("؛", ";")
            .replace("؟", "?")
            .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3")
            .replace("۴", "4").replace("۵", "5").replace("۶", "6").replace("۷", "7")
            .replace("۸", "8").replace("۹", "9")
            .replace("٠", "0").replace("١", "1").replace("٢", "2").replace("٣", "3")
            .replace("٤", "4").replace("٥", "5").replace("٦", "6").replace("٧", "7")
            .replace("٨", "8").replace("٩", "9")
            .replace(Regex("\\u200C"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun normalizeMoneyText(text: String): String {
        return text
            .replace("٬", "")
            .replace(",", "")
            .replace(Regex("\\s+و\\s+"), " ")
            .trim()
    }

    fun validateParsedResult(result: ParsedResult): Boolean {
        val validTypes = listOf("EXPENSE", "INCOME", "LOAN_DEBTOR", "LOAN_CREDITOR", "INSTALLMENT")
        if (result.amount <= 0) return false
        if (result.type !in validTypes) return false
        if (result.category.isBlank()) return false
        val hour = result.hour
        if (hour != null && (hour < 0 || hour > 23)) return false
        val minute = result.minute
        if (minute != null && (minute < 0 || minute > 59)) return false
        return true
    }
}
