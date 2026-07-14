package io.github.mojri.hesabyar

// Application entry point — Hilt injects dependencies here.
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.HiltAndroidApp
import io.github.mojri.hesabyar.BuildConfig
import io.github.mojri.hesabyar.rust.InternalException
import io.github.mojri.hesabyar.rust.RustBridge
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper

@HiltAndroidApp
class HesabyarApp : Application() {
  companion object {
    private const val TAG = "HesabyarApp"

    private val initLock = Any()

    @Volatile
    private var rustInitialized = false

    init {
      // Wire the Jalali calendar helper to the Rust core. The provider lazily
      // initializes the native library on first calendar call, preserving the
      // previous behavior where the helper itself triggered Rust init.
      JalaliCalendarHelper.bridgeProvider = { if (ensureRustInitialized()) RustBridge else null }
    }

    @JvmStatic
    fun isRustInitialized(): Boolean = rustInitialized

    /**
     * Force Rust availability state for unit tests only.
     *
     * Guarded two ways so production can never flip the real initialization
     * state: (1) `internal` visibility — only code in this module (i.e. tests)
     * may call it; (2) a `BuildConfig.DEBUG` check means a release build ignores
     * the call entirely. The `internal` modifier already restricts visibility to
     * this module (where the tests live); `@VisibleForTesting` merely signals the
     * method exists for tests. Note: the annotation's `otherwise` enum has no
     * `INTERNAL` value (only PRIVATE/PROTECTED/PACKAGE/NONE), so it intentionally
     * defaults rather than overstating visibility.
     */
    @VisibleForTesting
    @JvmStatic
    internal fun setRustInitializedForTesting(value: Boolean) {
      if (!BuildConfig.DEBUG) return
      rustInitialized = value
    }

    @JvmStatic
    fun ensureRustInitialized(): Boolean {
      if (rustInitialized) return true
      synchronized(initLock) {
        if (rustInitialized) return true
        return try {
          System.loadLibrary("hesabyar_core")
          io.github.mojri.hesabyar.rust.HesabyarCore
            .initialize()
          rustInitialized = true
          Log.i(TAG, "Rust shared core initialized successfully (lazy)")
          true
        } catch (e: UnsatisfiedLinkError) {
          Log.e(TAG, "Failed to load hesabyar_core native library", e)
          false
        } catch (e: InternalException) {
          Log.e(TAG, "Failed to initialize Rust core", e)
          false
        } catch (e: SecurityException) {
          Log.e(TAG, "Unexpected failure initializing Rust core", e)
          false
        }
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    val prefs = getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)
    val unit = CurrencyUnit.fromKey(prefs.getString("currency_unit", "تومان") ?: "تومان")
    CurrencyFormatter.setUnit(unit)
  }
}
