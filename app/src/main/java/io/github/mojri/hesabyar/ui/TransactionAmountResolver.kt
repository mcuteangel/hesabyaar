package io.github.mojri.hesabyar.ui

data class AmountResolutionInput(
    val displayedAmount: Long,
    val isEditMode: Boolean,
    val originalRialAmount: Long,
    val userModifiedAmount: Boolean = true
)

data class AmountResolutionResult(
    val rialAmount: Long,
    val preservedOriginal: Boolean
)

object TransactionAmountResolver {

    private const val MAX_SAFE_DISPLAY_AMOUNT = Long.MAX_VALUE / 10

    private fun safeToRial(displayedAmount: Long): Long {
        val clampedAmount = displayedAmount.coerceAtMost(MAX_SAFE_DISPLAY_AMOUNT)
        return CurrencyFormatter.toRial(clampedAmount)
    }

    fun resolveAmount(input: AmountResolutionInput): AmountResolutionResult {
        if (!input.isEditMode) {
            return AmountResolutionResult(
                rialAmount = safeToRial(input.displayedAmount),
                preservedOriginal = false
            )
        }

        return if (input.userModifiedAmount) {
            AmountResolutionResult(
                rialAmount = safeToRial(input.displayedAmount),
                preservedOriginal = false
            )
        } else {
            AmountResolutionResult(
                rialAmount = input.originalRialAmount,
                preservedOriginal = true
            )
        }
    }
}
