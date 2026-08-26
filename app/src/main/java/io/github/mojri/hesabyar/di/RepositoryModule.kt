package io.github.mojri.hesabyar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mojri.hesabyar.data.AccountDao
import io.github.mojri.hesabyar.data.AppDatabase
import io.github.mojri.hesabyar.data.BankLoanDao
import io.github.mojri.hesabyar.data.CategoryDao
import io.github.mojri.hesabyar.data.HesabyarRepository
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.InstallmentDao
import io.github.mojri.hesabyar.data.LoanDao
import io.github.mojri.hesabyar.data.PaymentHistoryDao
import io.github.mojri.hesabyar.data.PersonDao
import io.github.mojri.hesabyar.data.TransactionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
  @Provides
  @Singleton
  fun provideRepository(
    transactionDao: TransactionDao,
    loanDao: LoanDao,
    installmentDao: InstallmentDao,
    paymentHistoryDao: PaymentHistoryDao,
    categoryDao: CategoryDao,
    bankLoanDao: BankLoanDao,
    accountDao: AccountDao,
    personDao: PersonDao,
    database: AppDatabase
  ): HesabyarRepositoryInterface =
    HesabyarRepository(
      transactionDao,
      loanDao,
      installmentDao,
      paymentHistoryDao,
      categoryDao,
      bankLoanDao,
      accountDao,
      personDao,
      database
    )
}
