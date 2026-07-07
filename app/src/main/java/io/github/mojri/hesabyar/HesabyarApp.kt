package io.github.mojri.hesabyar

import android.app.Application
import android.content.Context
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit

@HiltAndroidApp
class HesabyarApp : Application() {
  companion object {
    private const val TAG = "HesabyarApp"
    @Volatile
    private var rustInitialized = false

    @JvmStatic
    fun isRustInitialized(): Boolean = rustInitialized
  }

  override fun onCreate() {
    super.onCreate()
    val prefs = getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)
    val unit = CurrencyUnit.fromKey(prefs.getString("currency_unit", "تومان") ?: "تومان")
    CurrencyFormatter.setUnit(unit)

    initializeRustCore()
  }

  private fun initializeRustCore() {
    try {
      System.loadLibrary("hesabyar_core")
      // Call Rust initialize() to install panic hook
      io.github.mojri.hesabyar.rust.HesabyarCore
        .initialize()
      rustInitialized = true
      Log.i(TAG, "Rust shared core initialized successfully")
    } catch (e: UnsatisfiedLinkError) {
      Log.e(TAG, "Failed to load hesabyar_core native library", e)
      // App continues without Rust core — AI/offline parser features unavailable
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize Rust core", e)
    }
  }
}
