package io.github.mojri.hesabyar.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mojri.hesabyar.data.ExcelExporter
import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.domain.usecase.ExportExcelUseCase
import io.github.mojri.hesabyar.domain.usecase.GetAnalyticsUseCase
import io.github.mojri.hesabyar.domain.usecase.GetBudgetAdviceUseCase
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import io.github.mojri.hesabyar.domain.usecase.GetForecastUseCase
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
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
    fun provideGetDashboardDataUseCase(repository: HesabyarRepositoryInterface): GetDashboardDataUseCase {
        return GetDashboardDataUseCase(repository)
    }

    @Provides
    fun provideManageTransactionUseCase(repository: HesabyarRepositoryInterface): ManageTransactionUseCase {
        return ManageTransactionUseCase(repository)
    }

    @Provides
    fun provideManageLoanUseCase(repository: HesabyarRepositoryInterface): ManageLoanUseCase {
        return ManageLoanUseCase(repository)
    }

    @Provides
    fun provideManageInstallmentUseCase(repository: HesabyarRepositoryInterface): ManageInstallmentUseCase {
        return ManageInstallmentUseCase(repository)
    }

    @Provides
    fun provideManageCategoryUseCase(repository: HesabyarRepositoryInterface): ManageCategoryUseCase {
        return ManageCategoryUseCase(repository)
    }

    @Provides
    fun provideParseTransactionUseCase(repository: HesabyarRepositoryInterface): ParseTransactionUseCase {
        return ParseTransactionUseCase(repository)
    }

    @Provides
    fun provideGetBudgetAdviceUseCase(): GetBudgetAdviceUseCase {
        return GetBudgetAdviceUseCase()
    }

    @Provides
    fun provideGetForecastUseCase(): GetForecastUseCase {
        return GetForecastUseCase()
    }

    @Provides
    fun provideGetAnalyticsUseCase(): GetAnalyticsUseCase {
        return GetAnalyticsUseCase()
    }

    @Provides
    fun provideManageBackupUseCase(repository: HesabyarRepositoryInterface): ManageBackupUseCase {
        return ManageBackupUseCase(repository)
    }

    @Provides
    fun provideExportExcelUseCase(
        repository: HesabyarRepositoryInterface,
        excelExporter: ExcelExporter
    ): ExportExcelUseCase {
        return ExportExcelUseCase(repository, excelExporter)
    }

    @Provides
    @Singleton
    fun provideGetSettingsUseCase(@ApplicationContext context: Context): GetSettingsUseCase {
        return GetSettingsUseCase(context.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE))
    }

    @Provides
    @Singleton
    fun provideExcelExporter(@ApplicationContext context: Context): ExcelExporter {
        return ExcelExporter(context)
    }
}
