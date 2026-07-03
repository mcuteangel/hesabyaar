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

    fun resolveAmount(input: AmountResolutionInput): AmountResolutionResult {
        if (!input.isEditMode) {
            return AmountResolutionResult(
                rialAmount = CurrencyFormatter.toRial(input.displayedAmount),
                preservedOriginal = false
            )
        }

        return if (input.userModifiedAmount) {
            AmountResolutionResult(
                rialAmount = CurrencyFormatter.toRial(input.displayedAmount),
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
