package io.github.mojri.hesabyar.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mojri.hesabyar.api.AiConfigManager
import io.github.mojri.hesabyar.data.ExcelExporter
import io.github.mojri.hesabyar.domain.usecase.ManageAiConfigUseCase
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiConfigManager(@ApplicationContext context: Context): AiConfigManager {
        return AiConfigManager(context)
    }

    @Provides
    @Singleton
    fun provideManageAiConfigUseCase(aiConfigManager: AiConfigManager): ManageAiConfigUseCase {
        return ManageAiConfigUseCase(aiConfigManager)
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
