package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.CategoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageCategoryUseCaseTest {
  private val fake = FakeRepository()
  private val useCase = ManageCategoryUseCase(fake)

  @Test
  fun getCategoriesByTypeFiltersExpenseAndBoth() =
    runTest {
      useCase.addCategory(
        name = "خوراک",
        key = "food",
        icon = "ic_food",
        color = 0xFF0000L,
        type = CategoryType.EXPENSE
      )
      useCase.addCategory(
        name = "حقوق",
        key = "salary",
        icon = "ic_salary",
        color = 0x00FF00L,
        type = CategoryType.INCOME
      )
      useCase.addCategory(
        name = "عمومی",
        key = "general",
        icon = "ic_general",
        color = 0x0000FFL,
        type = CategoryType.BOTH
      )

      val expenseCategories = useCase.getCategoriesByType(CategoryType.EXPENSE).first()
      assertEquals("Should return EXPENSE and BOTH categories", 2, expenseCategories.size)
      assertTrue("Contains EXPENSE category", expenseCategories.any { it.key == "food" })
      assertTrue("Contains BOTH category", expenseCategories.any { it.key == "general" })
      assertTrue("Does not contain INCOME category", expenseCategories.none { it.key == "salary" })
    }

  @Test
  fun getCategoriesByTypeFiltersIncomeAndBoth() =
    runTest {
      useCase.addCategory(
        name = "خوراک",
        key = "food",
        icon = "ic_food",
        color = 0xFF0000L,
        type = CategoryType.EXPENSE
      )
      useCase.addCategory(
        name = "حقوق",
        key = "salary",
        icon = "ic_salary",
        color = 0x00FF00L,
        type = CategoryType.INCOME
      )
      useCase.addCategory(
        name = "عمومی",
        key = "general",
        icon = "ic_general",
        color = 0x0000FFL,
        type = CategoryType.BOTH
      )

      val incomeCategories = useCase.getCategoriesByType(CategoryType.INCOME).first()
      assertEquals("Should return INCOME and BOTH categories", 2, incomeCategories.size)
      assertTrue("Contains INCOME category", incomeCategories.any { it.key == "salary" })
      assertTrue("Contains BOTH category", incomeCategories.any { it.key == "general" })
      assertTrue("Does not contain EXPENSE category", incomeCategories.none { it.key == "food" })
    }

  @Test
  fun addCategoryStoresAllFieldsWithCategoryType() =
    runTest {
      val id =
        useCase.addCategory(
          name = "حمل و نقل",
          key = "transport",
          icon = "ic_transport",
          color = 0x123456L,
          type = CategoryType.EXPENSE
        )

      val stored = fake.allCategories.first()
      assertEquals(1, stored.size)
      val category = stored.first()
      assertEquals(id, category.id)
      assertEquals("حمل و نقل", category.name)
      assertEquals("transport", category.key)
      assertEquals("ic_transport", category.icon)
      assertEquals(0x123456L, category.color)
      assertEquals(CategoryType.EXPENSE, category.type)
    }

  @Test
  fun getCategoryByIdReturnsMatchingCategoryOrNull() =
    runTest {
      val id =
        useCase.addCategory(
          name = "پوشاک",
          key = "clothing",
          icon = "ic_clothing",
          color = 0x654321L,
          type = CategoryType.EXPENSE
        )

      val found = useCase.getCategoryById(id)
      assertNotNull(found)
      assertEquals("clothing", found?.key)

      val notFound = useCase.getCategoryById(9999L)
      assertNull(notFound)
    }

  @Test
  fun getCategoryByKeyReturnsMatchingCategoryOrNull() =
    runTest {
      useCase.addCategory(
        name = "هدیه",
        key = "gift",
        icon = "ic_gift",
        color = 0xABCDEFL,
        type = CategoryType.BOTH
      )

      val found = useCase.getCategoryByKey("gift")
      assertNotNull(found)
      assertEquals(CategoryType.BOTH, found?.type)

      val notFound = useCase.getCategoryByKey("unknown_key")
      assertNull(notFound)
    }

  @Test
  fun updateCategoryModifiesStoredCategory() =
    runTest {
      val id =
        useCase.addCategory(
          name = "قبوض",
          key = "bills",
          icon = "ic_bills",
          color = 0x111111L,
          type = CategoryType.EXPENSE
        )

      val original = useCase.getCategoryById(id)!!
      val updated = original.copy(name = "قبض‌ها و اشتراک", color = 0x222222L)
      useCase.updateCategory(updated)

      val retrieved = useCase.getCategoryById(id)
      assertEquals("قبض‌ها و اشتراک", retrieved?.name)
      assertEquals(0x222222L, retrieved?.color)
    }

  @Test
  fun deleteCategoryRemovesCategoryFromRepository() =
    runTest {
      val id =
        useCase.addCategory(
          name = "ورزش",
          key = "sport",
          icon = "ic_sport",
          color = 0x333333L,
          type = CategoryType.EXPENSE
        )

      val added = useCase.getCategoryById(id)!!
      assertEquals(1, useCase.allCategories.first().size)

      useCase.deleteCategory(added)
      assertTrue(useCase.allCategories.first().isEmpty())
    }
}
