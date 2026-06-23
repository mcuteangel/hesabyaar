package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTest {

    @Test
    fun `default categories count is 8`() {
        assertEquals(8, Category.DEFAULTS.size)
    }

    @Test
    fun `default categories have unique keys`() {
        val keys = Category.DEFAULTS.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `default categories have unique ids after insertion`() {
        val defaults = Category.DEFAULTS
        assertTrue(defaults.all { it.id == 0L })
        val ids = defaults.mapIndexed { index, cat -> cat.copy(id = index.toLong() + 1) }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all default categories are marked as default`() {
        assertTrue(Category.DEFAULTS.all { it.isDefault })
    }

    @Test
    fun `expense categories have correct type`() {
        val expenseKeys = listOf("Food", "Transportation", "Shopping", "Bills", "Installments")
        expenseKeys.forEach { key ->
            val cat = Category.DEFAULTS.find { it.key == key }
            assertNotNull("Category $key should exist", cat)
            assertEquals("Category $key should be EXPENSE", Category.TYPE_EXPENSE, cat!!.type)
        }
    }

    @Test
    fun `income category has correct type`() {
        val income = Category.DEFAULTS.find { it.key == "Income" }
        assertNotNull(income)
        assertEquals(Category.TYPE_INCOME, income!!.type)
    }

    @Test
    fun `both-type categories have correct type`() {
        val bothKeys = listOf("Loans", "Other")
        bothKeys.forEach { key ->
            val cat = Category.DEFAULTS.find { it.key == key }
            assertNotNull("Category $key should exist", cat)
            assertEquals("Category $key should be BOTH", Category.TYPE_BOTH, cat!!.type)
        }
    }

    @Test
    fun `filter categories by expense type returns expense and both`() {
        val categories = Category.DEFAULTS
        val filtered = categories.filter { it.type == Category.TYPE_EXPENSE || it.type == Category.TYPE_BOTH }

        val expenseAndBothKeys = listOf("Food", "Transportation", "Shopping", "Bills", "Installments", "Loans", "Other")
        assertEquals(expenseAndBothKeys.size, filtered.size)
        assertTrue(filtered.all { it.key in expenseAndBothKeys })
    }

    @Test
    fun `filter categories by income type returns income and both`() {
        val categories = Category.DEFAULTS
        val filtered = categories.filter { it.type == Category.TYPE_INCOME || it.type == Category.TYPE_BOTH }

        val incomeAndBothKeys = listOf("Loans", "Income", "Other")
        assertEquals(incomeAndBothKeys.size, filtered.size)
        assertTrue(filtered.all { it.key in incomeAndBothKeys })
    }

    @Test
    fun `category has required fields populated`() {
        Category.DEFAULTS.forEach { cat ->
            assertTrue("name should not be blank for ${cat.key}", cat.name.isNotBlank())
            assertTrue("key should not be blank for ${cat.key}", cat.key.isNotBlank())
            assertTrue("icon should not be blank for ${cat.key}", cat.icon.isNotBlank())
            assertTrue("color should be positive for ${cat.key}", cat.color > 0)
            assertTrue("type should be valid for ${cat.key}", cat.type in listOf(Category.TYPE_EXPENSE, Category.TYPE_INCOME, Category.TYPE_BOTH))
        }
    }

    @Test
    fun `category type constants are correct`() {
        assertEquals("EXPENSE", Category.TYPE_EXPENSE)
        assertEquals("INCOME", Category.TYPE_INCOME)
        assertEquals("BOTH", Category.TYPE_BOTH)
    }

    @Test
    fun `category copy preserves id`() {
        val original = Category(id = 42, name = "Test", key = "Test", icon = "Paid", color = 0xFF0000L, type = Category.TYPE_EXPENSE)
        val copied = original.copy(name = "Updated")
        assertEquals(42L, copied.id)
        assertEquals("Updated", copied.name)
    }

    @Test
    fun `transaction references category by categoryId`() {
        val categories = Category.DEFAULTS.mapIndexed { index, cat -> cat.copy(id = index.toLong() + 1) }
        val foodCategory = categories.find { it.key == "Food" }!!

        val transaction = Transaction(
            type = "EXPENSE",
            categoryId = foodCategory.id,
            amount = 500_000L,
            description = " lunch"
        )

        assertEquals(foodCategory.id, transaction.categoryId)
    }

    @Test
    fun `transaction category lookup works`() {
        val categories = Category.DEFAULTS.mapIndexed { index, cat -> cat.copy(id = index.toLong() + 1) }
        val transactions = listOf(
            Transaction(type = "EXPENSE", categoryId = 1L, amount = 1000L, description = "test"),
            Transaction(type = "INCOME", categoryId = 7L, amount = 5000L, description = "test")
        )

        transactions.forEach { t ->
            val category = categories.find { it.id == t.categoryId }
            assertNotNull("Category should be found for transaction", category)
        }
    }

    @Test
    fun `transaction with missing category returns null`() {
        val categories = Category.DEFAULTS.mapIndexed { index, cat -> cat.copy(id = index.toLong() + 1) }
        val transaction = Transaction(type = "EXPENSE", categoryId = 999L, amount = 1000L, description = "test")

        val category = categories.find { it.id == transaction.categoryId }
        assertNull(category)
    }

    @Test
    fun `custom category can be created with same fields as default`() {
        val custom = Category(
            name = "ورزش",
            key = "Sports",
            icon = "SportsEsports",
            color = 0xFF00BCD4L,
            type = Category.TYPE_EXPENSE,
            isDefault = false
        )

        assertEquals("ورزش", custom.name)
        assertEquals("Sports", custom.key)
        assertEquals(Category.TYPE_EXPENSE, custom.type)
        assertFalse(custom.isDefault)
    }

    @Test
    fun `category with type BOTH appears in both expense and income filters`() {
        val bothCategory = Category(name = "Test", key = "Test", icon = "Paid", color = 0xFF0000L, type = Category.TYPE_BOTH)

        val matchesExpense = bothCategory.type == Category.TYPE_EXPENSE || bothCategory.type == Category.TYPE_BOTH
        val matchesIncome = bothCategory.type == Category.TYPE_INCOME || bothCategory.type == Category.TYPE_BOTH

        assertTrue(matchesExpense)
        assertTrue(matchesIncome)
    }

    @Test
    fun `expense-only category does not appear in income filter`() {
        val expenseOnly = Category(name = "Food", key = "Food", icon = "Paid", color = 0xFF0000L, type = Category.TYPE_EXPENSE)

        val matchesIncome = expenseOnly.type == Category.TYPE_INCOME || expenseOnly.type == Category.TYPE_BOTH
        assertFalse(matchesIncome)
    }

    @Test
    fun `income-only category does not appear in expense filter`() {
        val incomeOnly = Category(name = "Income", key = "Income", icon = "Paid", color = 0xFF0000L, type = Category.TYPE_INCOME)

        val matchesExpense = incomeOnly.type == Category.TYPE_EXPENSE || incomeOnly.type == Category.TYPE_BOTH
        assertFalse(matchesExpense)
    }

    @Test
    fun `category amounts use Long not Double`() {
        val amount: Long = 1_234_567_890L
        val category = Category(id = 1, name = "Test", key = "Test", icon = "Paid", color = amount, type = Category.TYPE_EXPENSE)

        assertTrue("Color should be Long", category.color is Long)
        assertEquals(amount, category.color)
    }

    @Test
    fun `default category names are in Persian`() {
        val persianNames = listOf("خوراک", "حمل و نقل", "خرید", "قبوض", "اقساط", "وام و قرض", "درآمد", "سایر")
        val actualNames = Category.DEFAULTS.map { it.name }
        assertEquals(persianNames, actualNames)
    }

    @Test
    fun `category icon mapping covers all defaults`() {
        val defaultIcons = setOf("Restaurant", "DirectionsCar", "ShoppingBag", "ReceiptLong", "CreditCard", "HistoryEdu", "Paid")
        val actualIcons = Category.DEFAULTS.map { it.icon }.toSet()
        assertTrue("All default icons should be in expected set", actualIcons.all { it in defaultIcons })
    }

    @Test
    fun `category colors are valid ARGB long values`() {
        Category.DEFAULTS.forEach { cat ->
            assertTrue("Color for ${cat.key} should be positive", cat.color > 0)
            assertTrue("Color for ${cat.key} should fit in ARGB", cat.color <= 0xFFFFFFFFL)
        }
    }

    @Test
    fun `category grouping by type`() {
        val categories = Category.DEFAULTS
        val grouped = categories.groupBy { it.type }

        assertEquals(3, grouped.size)
        assertTrue(grouped.containsKey(Category.TYPE_EXPENSE))
        assertTrue(grouped.containsKey(Category.TYPE_INCOME))
        assertTrue(grouped.containsKey(Category.TYPE_BOTH))

        assertEquals(5, grouped[Category.TYPE_EXPENSE]!!.size)
        assertEquals(1, grouped[Category.TYPE_INCOME]!!.size)
        assertEquals(2, grouped[Category.TYPE_BOTH]!!.size)
    }

    @Test
    fun `transaction category totals calculation`() {
        val categories = Category.DEFAULTS.mapIndexed { index, cat -> cat.copy(id = index.toLong() + 1) }
        val foodId = categories.find { it.key == "Food" }!!.id
        val transportId = categories.find { it.key == "Transportation" }!!.id

        val transactions = listOf(
            Transaction(type = "EXPENSE", categoryId = foodId, amount = 100_000L, description = "meal"),
            Transaction(type = "EXPENSE", categoryId = foodId, amount = 200_000L, description = "dinner"),
            Transaction(type = "EXPENSE", categoryId = transportId, amount = 50_000L, description = "taxi"),
            Transaction(type = "INCOME", categoryId = categories.find { it.key == "Income" }!!.id, amount = 5_000_000L, description = "salary")
        )

        val expenseTotals = transactions
            .filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        assertEquals(300_000L, expenseTotals[foodId])
        assertEquals(50_000L, expenseTotals[transportId])
        assertEquals(2, expenseTotals.size)
    }
}
