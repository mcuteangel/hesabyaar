package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the fresh-install seeding callback registered in
 * [AppDatabase.getDatabase] ([AppDatabase.DEFAULT_ACCOUNT_SEED_CALLBACK]).
 *
 * A brand-new database (fresh install) starts directly at the latest schema
 * version and never runs migrations, so the onCreate callback is the only
 * path that seeds the default account for it. The migration-based seeding
 * path (MIGRATION_5_6 on upgrade from v5) is covered separately in
 * [AppDatabaseMigrationTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppDatabaseFreshInstallTest {
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .addCallback(AppDatabase.DEFAULT_ACCOUNT_SEED_CALLBACK)
        .build()
    // Force the first open so Room fires the onCreate callback (Room opens lazily).
    database.accountDao().getAllAccountsBlocking()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun freshInstallSeedsDefaultAccountRow() {
    val accounts = database.accountDao().getAllAccountsBlocking()
    assertEquals("Fresh install must have exactly 1 seeded account", 1, accounts.size)

    val seeded = accounts.first()
    assertEquals("Seeded account id", 1L, seeded.id)
    assertEquals("Seeded account name", "حساب اصلی", seeded.name)
    assertEquals("Seeded account type", AccountType.BANK, seeded.type)
    assertEquals("Seeded account initialBalance", 0L, seeded.initialBalance)
    assertEquals("Seeded account displayOrder", 0, seeded.displayOrder)
    assertEquals("Seeded account createdAt", 0L, seeded.createdAt)
    assertEquals("Seeded account updatedAt", 0L, seeded.updatedAt)
    // color is not set by the seed SQL, so the schema column default applies
    assertEquals("Seeded account color falls back to DEFAULT_COLOR", AccountEntity.DEFAULT_COLOR, seeded.color)
  }
}
