package io.github.mojri.hesabyar.api

object PersianAmountParser {

    sealed class Token {
        data class Number(val value: Double) : Token()
        data class Unit(val type: UnitType) : Token()
    }

    enum class UnitType(val multiplier: Long) {
        BILLION(1_000_000_000L),
        MILLION(1_000_000L),
        THOUSAND(1_000L),
        TUMAN(1L);

        fun lower(): UnitType? = when (this) {
            BILLION -> MILLION
            MILLION -> THOUSAND
            THOUSAND -> TUMAN
            TUMAN -> null
        }
    }

    private val UNIT_WORDS = listOf(
        "میلیون تومان" to UnitType.MILLION,
        "میلیارد" to UnitType.BILLION,
        "میلیون" to UnitType.MILLION,
        "ملیون" to UnitType.MILLION,
        "هزار" to UnitType.THOUSAND,
        "تومان" to UnitType.TUMAN,
        "تومن" to UnitType.TUMAN,
    )

    fun parseAmount(sentence: String, shorthandMode: Boolean = true): Long {
        if (!MoneyDetector.containsMoney(sentence)) return 0L

        val normalized = normalizeText(sentence)
        val cleaned = normalized.replace(Regex("\\s+و\\s+"), " ")
        val tokens = tokenize(cleaned)
        if (tokens.isEmpty()) return 0L

        val hasUnits = tokens.any { it is Token.Unit }
        return when {
            hasUnits -> interpretWithUnits(tokens)
            shorthandMode -> interpretShorthand(tokens)
            else -> interpretBareLast(tokens)
        }
    }

    fun normalizeText(text: String): String = text
        .replace("٠", "0").replace("١", "1").replace("٢", "2").replace("٣", "3")
        .replace("٤", "4").replace("٥", "5").replace("٦", "6").replace("٧", "7")
        .replace("٨", "8").replace("٩", "9")
        .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3")
        .replace("۴", "4").replace("۵", "5").replace("۶", "6").replace("۷", "7")
        .replace("۸", "8").replace("۹", "9")
        .replace("٬", "").replace(",", "")
        .trim()

    private fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                i++
                continue
            }

            if (text[i].isDigit()) {
                val start = i
                while (i < text.length && (text[i].isDigit() || text[i] == '.')) i++
                tokens.add(Token.Number(text.substring(start, i).toDouble()))
                continue
            }

            var matched = false
            for ((word, type) in UNIT_WORDS) {
                if (text.startsWith(word, i)) {
                    tokens.add(Token.Unit(type))
                    i += word.length
                    matched = true
                    break
                }
            }
            if (!matched) i++
        }
        return tokens
    }

    private fun interpretWithUnits(tokens: List<Token>): Long {
        var total = 0L
        var currentNum = 0.0
        var lastUnit: UnitType? = null

        for (token in tokens) {
            when (token) {
                is Token.Number -> currentNum = token.value
                is Token.Unit -> {
                    if (currentNum > 0) {
                        total += (currentNum * token.type.multiplier).toLong()
                    }
                    lastUnit = token.type
                    currentNum = 0.0
                }
            }
        }

        if (currentNum > 0) {
            val multiplier = lastUnit?.lower()?.multiplier ?: 1L
            total += (currentNum * multiplier).toLong()
        }

        return total
    }

    private fun interpretShorthand(tokens: List<Token>): Long {
        val numbers = tokens.filterIsInstance<Token.Number>()
        if (numbers.isEmpty()) return 0L

        val count = numbers.size
        if (count == 1) return numbers[0].value.toLong()

        val unitSteps = listOf(
            UnitType.BILLION.multiplier,
            UnitType.MILLION.multiplier,
            UnitType.THOUSAND.multiplier
        )
        val startIdx = (3 - count).coerceAtLeast(0)

        var total = 0L
        for ((i, num) in numbers.withIndex()) {
            val idx = (startIdx + i).coerceAtMost(unitSteps.size - 1)
            total += (num.value * unitSteps[idx]).toLong()
        }
        return total
    }

    private fun interpretBareLast(tokens: List<Token>): Long {
        val lastNum = tokens.filterIsInstance<Token.Number>().lastOrNull()
        return lastNum?.value?.toLong() ?: 0L
    }
}
