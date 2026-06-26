package io.github.mojri.hesabyar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mojri.hesabyar.data.ExcelExporter
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.domain.usecase.ExportExcelUseCase
import io.github.mojri.hesabyar.domain.usecase.GetAnalyticsUseCase
import io.github.mojri.hesabyar.domain.usecase.GetBudgetAdviceUseCase
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import io.github.mojri.hesabyar.domain.usecase.GetForecastUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageCategoryUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageInstallmentUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageLoanUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageTransactionUseCase
import io.github.mojri.hesabyar.domain.usecase.ParseTransactionUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetDashboardDataUseCase(repository: HesabyarRepositoryInterface): GetDashboardDataUseCase {
        return GetDashboardDataUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideManageTransactionUseCase(repository: HesabyarRepositoryInterface): ManageTransactionUseCase {
        return ManageTransactionUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideManageLoanUseCase(repository: HesabyarRepositoryInterface): ManageLoanUseCase {
        return ManageLoanUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideManageInstallmentUseCase(repository: HesabyarRepositoryInterface): ManageInstallmentUseCase {
        return ManageInstallmentUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideManageCategoryUseCase(repository: HesabyarRepositoryInterface): ManageCategoryUseCase {
        return ManageCategoryUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideParseTransactionUseCase(repository: HesabyarRepositoryInterface): ParseTransactionUseCase {
        return ParseTransactionUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideGetBudgetAdviceUseCase(): GetBudgetAdviceUseCase {
        return GetBudgetAdviceUseCase()
    }

    @Provides
    @Singleton
    fun provideGetForecastUseCase(): GetForecastUseCase {
        return GetForecastUseCase()
    }

    @Provides
    @Singleton
    fun provideGetAnalyticsUseCase(): GetAnalyticsUseCase {
        return GetAnalyticsUseCase()
    }

    @Provides
    @Singleton
    fun provideManageBackupUseCase(repository: HesabyarRepositoryInterface): ManageBackupUseCase {
        return ManageBackupUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideExportExcelUseCase(
        repository: HesabyarRepositoryInterface,
        excelExporter: ExcelExporter
    ): ExportExcelUseCase {
        return ExportExcelUseCase(repository, excelExporter)
    }
}
