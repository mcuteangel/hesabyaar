package io.github.mojri.hesabyar.domain.utils

data class AmountResolutionInput(
  val displayedAmount: Long,
  val isEditMode: Boolean,
  val originalRialAmount: Long,
  val userModifiedAmount: Boolean
)

data class AmountResolutionResult(
  val rialAmount: Long,
  val preservedOriginal: Boolean
)

object TransactionAmountResolver {
  fun resolveAmount(
    input: AmountResolutionInput,
    toRial: (Long) -> Long
  ): AmountResolutionResult {
    if (!input.isEditMode) {
      return AmountResolutionResult(
        rialAmount = safeToRial(input.displayedAmount, toRial),
        preservedOriginal = false
      )
    }

    return if (input.userModifiedAmount) {
      AmountResolutionResult(
        rialAmount = safeToRial(input.displayedAmount, toRial),
        preservedOriginal = false
      )
    } else {
      AmountResolutionResult(
        rialAmount = input.originalRialAmount,
        preservedOriginal = true
      )
    }
  }

  private fun safeToRial(
    displayedAmount: Long,
    toRial: (Long) -> Long
  ): Long = toRial(displayedAmount)
}
