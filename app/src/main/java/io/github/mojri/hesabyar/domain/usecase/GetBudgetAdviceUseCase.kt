package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.api.AiProviderConfig
import io.github.mojri.hesabyar.api.BudgetAdvisor
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction

class GetBudgetAdviceUseCase {
    suspend fun getAdvice(
        transactions: List<Transaction>,
        categories: List<Category>,
        config: AiProviderConfig?
    ): String = BudgetAdvisor.getBudgetAdvice(transactions, categories, config)
}
