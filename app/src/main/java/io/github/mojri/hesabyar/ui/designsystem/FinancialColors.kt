package io.github.mojri.hesabyar.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class FinancialPalette(
  val incomeGreen: Color,
  val expenseRed: Color,
  val warningOrange: Color,
  val infoBlue: Color,
  val purpleAccent: Color
)

private val LightFinancialPalette =
  FinancialPalette(
    incomeGreen = Color(0xFF2ECC71),
    expenseRed = Color(0xFFE74C3C),
    warningOrange = Color(0xFFF39C12),
    infoBlue = Color(0xFF3498DB),
    purpleAccent = Color(0xFF9B59B6)
  )

private val DarkFinancialPalette =
  FinancialPalette(
    incomeGreen = Color(0xFF4CAF50),
    expenseRed = Color(0xFFEF5350),
    warningOrange = Color(0xFFFFB300),
    infoBlue = Color(0xFF4FC3F7),
    purpleAccent = Color(0xFFCE93D8)
  )

object FinancialColors {
  private val incomeGreenState = mutableStateOf(LightFinancialPalette.incomeGreen)
  private val expenseRedState = mutableStateOf(LightFinancialPalette.expenseRed)
  private val warningOrangeState = mutableStateOf(LightFinancialPalette.warningOrange)
  private val infoBlueState = mutableStateOf(LightFinancialPalette.infoBlue)
  private val purpleAccentState = mutableStateOf(LightFinancialPalette.purpleAccent)

  val IncomeGreen: Color get() = incomeGreenState.value
  val ExpenseRed: Color get() = expenseRedState.value
  val WarningOrange: Color get() = warningOrangeState.value
  val InfoBlue: Color get() = infoBlueState.value
  val PurpleAccent: Color get() = purpleAccentState.value

  internal fun setPalette(palette: FinancialPalette) {
    incomeGreenState.value = palette.incomeGreen
    expenseRedState.value = palette.expenseRed
    warningOrangeState.value = palette.warningOrange
    infoBlueState.value = palette.infoBlue
    purpleAccentState.value = palette.purpleAccent
  }
}

internal fun applyFinancialPalette(darkTheme: Boolean) {
  FinancialColors.setPalette(if (darkTheme) DarkFinancialPalette else LightFinancialPalette)
}
