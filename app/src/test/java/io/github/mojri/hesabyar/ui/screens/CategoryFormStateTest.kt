package io.github.mojri.hesabyar.ui.screens

import androidx.compose.runtime.mutableStateOf
import io.github.mojri.hesabyar.data.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CategoryFormState] validation, key derivation, and [CategoryType] fallbacks.
 */
class CategoryFormStateTest {
  @Test
  fun validCategoryWithNameAndExpenseTypeIsValid() {
    val state =
      CategoryFormState(
        isEditing = false,
        nameState = mutableStateOf("خواروبار"),
        selectedTypeState = mutableStateOf(CategoryType.EXPENSE)
      )

    assertTrue(state.isValid)
    assertEquals(CategoryType.EXPENSE, state.typeToSave)
    assertEquals("خواروبار", state.finalKey)
  }

  @Test
  fun blankOrWhitespaceNameIsInvalid() {
    val state =
      CategoryFormState(
        isEditing = false,
        nameState = mutableStateOf("   "),
        selectedTypeState = mutableStateOf(CategoryType.EXPENSE)
      )

    assertFalse(state.isValid)
  }

  @Test
  fun unknownSelectedTypeFallsBackToInitialType() {
    val state =
      CategoryFormState(
        isEditing = true,
        initialType = CategoryType.INCOME,
        nameState = mutableStateOf("حقوق"),
        selectedTypeState = mutableStateOf(CategoryType.UNKNOWN)
      )

    assertTrue(state.isValid)
    assertEquals(CategoryType.INCOME, state.resolvedType)
    assertEquals(CategoryType.INCOME, state.typeToSave)
  }

  @Test
  fun unknownSelectedAndInitialTypeAutoRecoversToExpense() {
    val state =
      CategoryFormState(
        isEditing = true,
        initialType = CategoryType.UNKNOWN,
        nameState = mutableStateOf("دسته‌بندی قدیمی"),
        selectedTypeState = mutableStateOf(CategoryType.UNKNOWN)
      )

    assertTrue("Corrupted category auto-recovers to EXPENSE and is valid to save", state.isValid)
    assertEquals(CategoryType.EXPENSE, state.resolvedType)
    assertEquals(CategoryType.EXPENSE, state.typeToSave)

    // User can still explicitly select another valid type
    state.selectedType = CategoryType.INCOME
    assertTrue(state.isValid)
    assertEquals(CategoryType.INCOME, state.resolvedType)
    assertEquals(CategoryType.INCOME, state.typeToSave)
  }

  @Test
  fun explicitKeyIsTrimmedAndUsed() {
    val state =
      CategoryFormState(
        isEditing = false,
        nameState = mutableStateOf("پزشکی"),
        keyState = mutableStateOf("  Medical  "),
        selectedTypeState = mutableStateOf(CategoryType.EXPENSE)
      )

    assertTrue(state.isValid)
    assertEquals("Medical", state.finalKey)
  }

  @Test
  fun blankKeyDerivesFromTrimmedNameWithoutSpaces() {
    val state =
      CategoryFormState(
        isEditing = false,
        nameState = mutableStateOf("  قبض آب و برق  "),
        keyState = mutableStateOf(""),
        selectedTypeState = mutableStateOf(CategoryType.EXPENSE)
      )

    assertTrue(state.isValid)
    assertEquals("قبضآبوبرق", state.finalKey)
  }

  @Test
  fun editingDefaultCategoryPreservesInitialKey() {
    val state =
      CategoryFormState(
        isEditing = true,
        initialKey = "Food",
        isKeyEnabled = false,
        nameState = mutableStateOf("غذا و نوشیدنی"),
        keyState = mutableStateOf("ModifiedKey"),
        selectedTypeState = mutableStateOf(CategoryType.EXPENSE)
      )

    assertTrue(state.isValid)
    assertEquals("Food", state.finalKey)
  }

  @Test
  fun selectingBothTypeUpdatesTypeToSave() {
    val state =
      CategoryFormState(
        isEditing = false,
        nameState = mutableStateOf("قرض و بدهی"),
        selectedTypeState = mutableStateOf(CategoryType.BOTH)
      )

    assertTrue(state.isValid)
    assertEquals(CategoryType.BOTH, state.typeToSave)
  }
}
