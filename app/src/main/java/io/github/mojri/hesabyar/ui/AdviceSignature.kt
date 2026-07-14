package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.Transaction

/**
 * Pure, dependency-free data-signature helpers shared by [AiAssistantViewModel]
 * (production cache invalidation) and tests.
 *
 * Kept free of [android.content.Context] and config state so the values are
 * deterministic and unit-testable. [AiAssistantViewModel] appends its own
 * config signature to [computeDataSignature] when building the cache key, so
 * the ViewModel's public signature behavior is unchanged.
 */
internal object AdviceSignature {
  fun computeDataSignature(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): String {
    val txCount = transactions.size
    val txTotal = transactions.sumOf { it.amount }
    val loanCount = loans.size
    val loanRemaining = loans.sumOf { it.remainingAmount }
    val loanSettled = loans.count { it.isSettled }
    val instCount = installments.size
    val instPaid = installments.count { it.isPaid }
    val instAmount = installments.sumOf { it.amount }
    val catCount = categories.size
    return "$txCount|$txTotal|$loanCount|$loanRemaining|$loanSettled|" +
      "$instCount|$instPaid|$instAmount|$catCount"
  }

  fun computeAdviceSignature(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>
  ): String = computeDataSignature(transactions, loans, installments, categories)
}
