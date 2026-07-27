package io.github.mojri.hesabyar.domain.usecase

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AiForecastAdviceCache] using an in-memory
 * [SharedPreferences] fake — no Robolectric required.
 */
class AiForecastAdviceCacheTest {
  private lateinit var prefs: FakeSharedPreferences
  private lateinit var cache: SharedPrefsAiForecastAdviceCache

  @Before
  fun setUp() {
    prefs = FakeSharedPreferences()
    cache = SharedPrefsAiForecastAdviceCache(prefs)
  }

  // ── Forecast tests ────────────────────────────────────────────────────

  @Test
  fun `putForecast then getForecast returns same value`() {
    cache.putForecast("sig-1", "forecast content")

    val entry = cache.getForecast("sig-1")

    assertNotNull(entry)
    assertEquals("forecast content", entry!!.value)
    assert(entry.fetchedAtMillis > 0)
  }

  @Test
  fun `getForecast returns null when signature does not match`() {
    cache.putForecast("sig-1", "forecast content")

    val entry = cache.getForecast("sig-2")

    assertNull(entry)
  }

  @Test
  fun `getForecast returns null when content is empty`() {
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST, "")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, System.currentTimeMillis())
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_SIG, "sig-1")
      .apply()

    val entry = cache.getForecast("sig-1")

    assertNull(entry)
  }

  @Test
  fun `getForecast returns null when entry is expired`() {
    cache.putForecast("sig-1", "forecast content")

    // Backdate the timestamp by 11 minutes (beyond 10-min TTL)
    val elevenMinutesAgo = System.currentTimeMillis() - 11 * 60 * 1000L
    prefs
      .edit()
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, elevenMinutesAgo)
      .apply()

    val entry = cache.getForecast("sig-1")

    assertNull(entry)
  }

  @Test
  fun `getForecast returns entry when within TTL`() {
    cache.putForecast("sig-1", "forecast content")

    // Set timestamp to 5 minutes ago (within 10-min TTL)
    val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000L
    prefs
      .edit()
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, fiveMinutesAgo)
      .apply()

    val entry = cache.getForecast("sig-1")

    assertNotNull(entry)
    assertEquals("forecast content", entry!!.value)
  }

  // ── Advice tests ──────────────────────────────────────────────────────

  @Test
  fun `putAdvice then getAdvice returns same value`() {
    cache.putAdvice("sig-1", "advice content")

    val entry = cache.getAdvice("sig-1")

    assertNotNull(entry)
    assertEquals("advice content", entry!!.value)
    assert(entry.fetchedAtMillis > 0)
  }

  @Test
  fun `getAdvice returns null when signature does not match`() {
    cache.putAdvice("sig-1", "advice content")

    val entry = cache.getAdvice("sig-2")

    assertNull(entry)
  }

  @Test
  fun `getAdvice returns null when content is empty`() {
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE, "")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_TIME, System.currentTimeMillis())
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_SIG, "sig-1")
      .apply()

    val entry = cache.getAdvice("sig-1")

    assertNull(entry)
  }

  @Test
  fun `getAdvice returns null when entry is expired`() {
    cache.putAdvice("sig-1", "advice content")

    // Backdate the timestamp by 11 minutes (beyond 10-min TTL)
    val elevenMinutesAgo = System.currentTimeMillis() - 11 * 60 * 1000L
    prefs
      .edit()
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_TIME, elevenMinutesAgo)
      .apply()

    val entry = cache.getAdvice("sig-1")

    assertNull(entry)
  }

  // ── Clear tests ───────────────────────────────────────────────────────

  @Test
  fun `clear empties both forecast and advice caches`() {
    cache.putForecast("sig-1", "forecast content")
    cache.putAdvice("sig-2", "advice content")

    cache.clear()

    assertNull(cache.getForecast("sig-1"))
    assertNull(cache.getAdvice("sig-2"))
  }

  // ── Overwrite tests ───────────────────────────────────────────────────

  @Test
  fun `putForecast overwrites previous entry`() {
    cache.putForecast("sig-1", "old forecast")
    cache.putForecast("sig-1", "new forecast")

    val entry = cache.getForecast("sig-1")

    assertNotNull(entry)
    assertEquals("new forecast", entry!!.value)
  }

  @Test
  fun `putAdvice overwrites previous entry`() {
    cache.putAdvice("sig-1", "old advice")
    cache.putAdvice("sig-1", "new advice")

    val entry = cache.getAdvice("sig-1")

    assertNotNull(entry)
    assertEquals("new advice", entry!!.value)
  }

  // ── Independent storage ───────────────────────────────────────────────

  @Test
  fun `forecast and advice caches are independent`() {
    cache.putForecast("sig-1", "forecast content")
    cache.putAdvice("sig-1", "advice content")

    cache.clear()

    // After clear, both should be gone
    assertNull(cache.getForecast("sig-1"))
    assertNull(cache.getAdvice("sig-1"))
  }
}

/**
 * Minimal in-memory implementation of [SharedPreferences] for unit testing.
 * Supports the subset of operations used by [SharedPrefsAiForecastAdviceCache].
 */
private class FakeSharedPreferences : SharedPreferences {
  private val store = mutableMapOf<String, Any?>()
  private var editor: FakeEditor? = null

  override fun edit(): SharedPreferences.Editor {
    val e = FakeEditor(store)
    editor = e
    return e
  }

  override fun getString(
    key: String?,
    defValue: String?
  ): String? = store[key] as? String ?: defValue

  override fun getLong(
    key: String?,
    defValue: Long
  ): Long = store[key] as? Long ?: defValue

  override fun getInt(
    key: String?,
    defValue: Int
  ): Int = store[key] as? Int ?: defValue

  override fun getFloat(
    key: String?,
    defValue: Float
  ): Float = store[key] as? Float ?: defValue

  override fun getBoolean(
    key: String?,
    defValue: Boolean
  ): Boolean = store[key] as? Boolean ?: defValue

  override fun contains(key: String?): Boolean = store.containsKey(key)

  override fun getAll(): MutableMap<String, *> = store.toMutableMap()

  override fun getStringSet(
    key: String?,
    defValues: MutableSet<String>?
  ): MutableSet<String>? =
    @Suppress("UNCHECKED_CAST")
    (store[key] as? MutableSet<String>)
      ?: defValues

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
  }

  override fun unregisterOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?,
  ) {
  }

  private class FakeEditor(
    private val store: MutableMap<String, Any?>,
  ) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removals = mutableListOf<String>()

    override fun putString(
      key: String?,
      value: String?
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putLong(
      key: String?,
      value: Long
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putInt(
      key: String?,
      value: Int
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putFloat(
      key: String?,
      value: Float
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putBoolean(
      key: String?,
      value: Boolean
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putStringSet(
      key: String?,
      values: MutableSet<String>?
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = values
      return this
    }

    override fun remove(key: String?): SharedPreferences.Editor {
      if (key != null) removals.add(key)
      return this
    }

    override fun clear(): SharedPreferences.Editor {
      store.clear()
      return this
    }

    override fun commit(): Boolean {
      apply()
      return true
    }

    override fun apply() {
      store.putAll(pending)
      removals.forEach { store.remove(it) }
      pending.clear()
      removals.clear()
    }
  }
}
