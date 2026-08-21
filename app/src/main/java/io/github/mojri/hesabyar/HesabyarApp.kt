package io.github.mojri.hesabyar

// Application entry point — Hilt injects dependencies here

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

    /**
     * Performs the real native-library load and Rust core initialization.
     * Exposed as an overridable action so tests can substitute a throwing
     * lambda (for example, to simulate a UniFFI checksum/contract mismatch
     * that raises RuntimeException) without a real native library. The default
     * runs the production path unchanged.
     */
    @VisibleForTesting
    internal var rustNativeInitAction: () -> Unit = ::runRustNativeInit

    @Volatile
    private var rustInitialized = false

    /**
     * Test-only availability override, see [setRustInitializedForTesting].
     * `null` means "no override — derive availability from the real load state".
     */
    @Volatile
    private var rustInitializedOverride: Boolean? = null

    init {
      // Wire the Jalali calendar helper to the Rust core. The provider lazily
      // initializes the native library on first calendar call, preserving the
      // previous behavior where the helper itself triggered Rust init.
      JalaliCalendarHelper.bridgeProvider = { if (ensureRustInitialized()) RustBridge else null }
    }

    @JvmStatic
    fun isRustInitialized(): Boolean = rustInitializedOverride ?: rustInitialized

    /**
     * Force Rust availability state for unit tests only.
     *
     * The value is an **override of the availability decision**, checked by
     * [ensureRustInitialized] BEFORE any attempt to load the native library:
     * `false` forces every `RustBridge` caller onto the Kotlin fallback even
     * when `hesabyar_core` is loadable, and `true` forces the Rust path. This
     * is what makes fallback tests deterministic — without it, `false` merely
     * reset the memoization flag and the library was re-loaded on the next
     * access whenever it sat on `java.library.path`. Passing `null` clears the
     * override and restores load-based availability.
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
    internal fun setRustInitializedForTesting(value: Boolean?) {
      if (!BuildConfig.DEBUG) return
      rustInitializedOverride = value
    }

    /** Returns the current test override, or null when none is set. */
    @VisibleForTesting
    @JvmStatic
    internal fun getRustInitializedOverrideForTesting(): Boolean? = rustInitializedOverride

    private fun runRustNativeInit() {
      System.loadLibrary("hesabyar_core")
      io.github.mojri.hesabyar.rust.HesabyarCore
        .initialize()
    }

    @JvmStatic
    fun ensureRustInitialized(): Boolean =
      rustInitializedOverride
        ?: if (rustInitialized) true else initializeRustCore()

    // Safety-net catch: UniFFI throws RuntimeException on a contract/checksum
    // mismatch while lazily initializing the native library; we degrade to the
    // Kotlin fallback instead of crashing the app.
    @Suppress("TooGenericExceptionCaught")
    private fun initializeRustCore(): Boolean {
      var initialized = false
      synchronized(initLock) {
        if (!rustInitialized) {
          initialized =
            try {
              rustNativeInitAction()
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
              Log.e(TAG, "Security manager denied Rust core initialization", e)
              false
            } catch (e: RuntimeException) {
              Log.e(TAG, "Unexpected runtime failure initializing Rust core", e)
              false
            }
        }
      }
      return initialized
    }
  }

  override fun onCreate() {
    super.onCreate()
    val prefs = getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)
    val unit = CurrencyUnit.fromKey(prefs.getString("currency_unit", "تومان") ?: "تومان")
    CurrencyFormatter.setUnit(unit)
  }
}
