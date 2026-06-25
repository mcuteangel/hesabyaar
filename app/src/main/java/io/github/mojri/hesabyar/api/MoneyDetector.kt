package io.github.mojri.hesabyar.api

object MoneyDetector {

    private val unitWords = listOf(
        "هزار", "میلیون", "ملیون", "میلیارد", "تومان", "تومن"
    )

    private val contextKeywords = listOf(
        "خریدم", "خرید", "پرداخت", "هزینه", "خرج", "دادم", "گرفتم",
        "حقوق", "درآمد", "قرض", "وام", "قسط", "واریز", "بانک",
        "فروش", "پول", "مبلغ", "قیمت", "ارزان", "گران"
    )

    fun containsMoney(sentence: String): Boolean {
        val normalized = sentence.trim()
        return unitWords.any { normalized.contains(it) } ||
                contextKeywords.any { normalized.contains(it) }
    }
}
