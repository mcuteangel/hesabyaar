package io.github.mojri.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CategoryRepositoryLogicTest {
  private lateinit var database: AppDatabase
  private lateinit var repository: HesabyarRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room
        .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    database.accountDao().insertAllBlocking(listOf(AccountEntity.DEFAULT_ACCOUNT))
    repository =
      HesabyarRepository(
        database.transactionDao(),
        database.loanDao(),
        database.installmentDao(),
        database.paymentHistoryDao(),
        database.categoryDao(),
        database.bankLoanDao(),
        database.accountDao(),
        database.personDao(),
        database
      )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun getCategoriesByTypeFiltersCorrectlyWithBoth() =
    runTest {
      val expenseCategory =
        Category(
          name = "خوراک",
          key = "food",
          icon = "Restaurant",
          color = 0xFFFF0000L,
          type = CategoryType.EXPENSE,
          isDefault = false
        )
      val incomeCategory =
        Category(
          name = "حقوق",
          key = "salary",
          icon = "Work",
          color = 0xFF00FF00L,
          type = CategoryType.INCOME,
          isDefault = false
        )
      val bothCategory =
        Category(
          name = "عمومی",
          key = "general",
          icon = "Category",
          color = 0xFF0000FFL,
          type = CategoryType.BOTH,
          isDefault = false
        )

      repository.insertCategory(expenseCategory)
      repository.insertCategory(incomeCategory)
      repository.insertCategory(bothCategory)

      val expenseResult = repository.getCategoriesByType(CategoryType.EXPENSE).first()
      assertEquals("EXPENSE filter must return EXPENSE and BOTH categories", 2, expenseResult.size)
      assertTrue("Must contain EXPENSE category", expenseResult.any { it.key == "food" })
      assertTrue("Must contain BOTH category", expenseResult.any { it.key == "general" })
      assertFalse("Must not contain INCOME category", expenseResult.any { it.key == "salary" })

      val incomeResult = repository.getCategoriesByType(CategoryType.INCOME).first()
      assertEquals("INCOME filter must return INCOME and BOTH categories", 2, incomeResult.size)
      assertTrue("Must contain INCOME category", incomeResult.any { it.key == "salary" })
      assertTrue("Must contain BOTH category", incomeResult.any { it.key == "general" })
      assertFalse("Must not contain EXPENSE category", incomeResult.any { it.key == "food" })
    }
}
