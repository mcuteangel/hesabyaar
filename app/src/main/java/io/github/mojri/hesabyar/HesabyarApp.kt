package io.github.mojri.hesabyar

import android.app.Application
import androidx.preference.PreferenceManager
import dagger.hilt.android.HiltAndroidApp
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit

@HiltAndroidApp
class HesabyarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val unit = CurrencyUnit.fromKey(prefs.getString("currency_unit", "تومان") ?: "تومان")
        CurrencyFormatter.setUnit(unit)
    }
}
