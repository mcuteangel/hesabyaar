package io.github.mojri.hesabyar.ui.designsystem

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
  private var incomeGreen: Color = LightFinancialPalette.incomeGreen
  private var expenseRed: Color = LightFinancialPalette.expenseRed
  private var warningOrange: Color = LightFinancialPalette.warningOrange
  private var infoBlue: Color = LightFinancialPalette.infoBlue
  private var purpleAccent: Color = LightFinancialPalette.purpleAccent

  val IncomeGreen: Color get() = incomeGreen
  val ExpenseRed: Color get() = expenseRed
  val WarningOrange: Color get() = warningOrange
  val InfoBlue: Color get() = infoBlue
  val PurpleAccent: Color get() = purpleAccent

  internal fun setPalette(palette: FinancialPalette) {
    incomeGreen = palette.incomeGreen
    expenseRed = palette.expenseRed
    warningOrange = palette.warningOrange
    infoBlue = palette.infoBlue
    purpleAccent = palette.purpleAccent
  }
}

internal fun applyFinancialPalette(darkTheme: Boolean) {
  FinancialColors.setPalette(if (darkTheme) DarkFinancialPalette else LightFinancialPalette)
}
