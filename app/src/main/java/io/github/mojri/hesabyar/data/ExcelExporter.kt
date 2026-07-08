package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.rust.Cell
import io.github.mojri.hesabyar.rust.RustBridge
import io.github.mojri.hesabyar.rust.SheetData
import io.github.mojri.hesabyar.rust.WorkbookData
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import java.util.Calendar

class ExcelExporter {
  data class ExportResult(
    val bytes: ByteArray,
    val filename: String,
    val transactionCount: Int,
    val incomeCount: Int,
    val expenseCount: Int,
    val loanCount: Int,
    val installmentCount: Int
  )

  suspend fun export(
    transactions: List<Transaction>,
    loans: List<Loan>,
    installments: List<Installment>,
    categories: List<Category>,
  ): ExportResult {
    val categoryMap = categories.associateBy { it.id }
    val incomeTransactions = transactions.filter { it.type == "INCOME" }
    val expenseTransactions = transactions.filter { it.type == "EXPENSE" }

    val sheets =
      listOf(
        buildTransactionsSheet(transactions, categoryMap),
        buildIncomeSheet(incomeTransactions, categoryMap),
        buildExpensesSheet(expenseTransactions, categoryMap),
        buildLoansSheet(loans),
        buildInstallmentsSheet(installments)
      )

    val bytes =
      RustBridge.generateExcel(WorkbookData(sheets))
        ?: throw IllegalStateException("Rust Excel generation failed or unavailable")

    return ExportResult(
      bytes = bytes,
      filename = generateFilename(),
      transactionCount = transactions.size,
      incomeCount = incomeTransactions.size,
      expenseCount = expenseTransactions.size,
      loanCount = loans.size,
      installmentCount = installments.size
    )
  }

  // ─── Sheet builders ──────────────────────────────────────────────

  private companion object {
    const val HEADER_CATEGORY = "دسته\u200Cبندی"
    const val HEADER_DESCRIPTION = "توضیحات"
  }

  private fun buildTransactionsSheet(
    transactions: List<Transaction>,
    categoryMap: Map<Long, Category>
  ): SheetData {
    val headers = listOf("ردیف", "نوع", HEADER_CATEGORY, "مبلغ", HEADER_DESCRIPTION, "تاریخ")
    val rows = buildTxRows(transactions, categoryMap, includeType = true)
    return SheetData(name = "همه تراکنش\u200Cها", headers = headers, rows = rows, summaryRow = null)
  }

  private fun buildIncomeSheet(
    incomeTransactions: List<Transaction>,
    categoryMap: Map<Long, Category>
  ): SheetData {
    val headers = listOf("ردیف", HEADER_CATEGORY, "مبلغ", HEADER_DESCRIPTION, "تاریخ")
    val rows = buildTxRows(incomeTransactions, categoryMap, includeType = false)
    val total = incomeTransactions.sumOf { it.amount }
    val summary = buildTotalRow(total)
    return SheetData(name = "دریافتی\u200Cها", headers = headers, rows = rows, summaryRow = summary)
  }

  private fun buildExpensesSheet(
    expenseTransactions: List<Transaction>,
    categoryMap: Map<Long, Category>
  ): SheetData {
    val headers = listOf("ردیف", HEADER_CATEGORY, "مبلغ", HEADER_DESCRIPTION, "تاریخ")
    val rows = buildTxRows(expenseTransactions, categoryMap, includeType = false)
    val total = expenseTransactions.sumOf { it.amount }
    val summary = buildTotalRow(total)
    return SheetData(name = "پرداختی\u200Cها", headers = headers, rows = rows, summaryRow = summary)
  }

  private fun buildTxRows(
    transactions: List<Transaction>,
    categoryMap: Map<Long, Category>,
    includeType: Boolean
  ): List<List<Cell>> =
    transactions.mapIndexed { index, tx ->
      buildList {
        add(Cell(value = (index + 1).toString(), bold = false))
        if (includeType) {
          add(Cell(value = if (tx.type == "INCOME") "دریافتی" else "پرداختی", bold = false))
        }
        add(Cell(value = categoryMap[tx.categoryId]?.name ?: "سایر", bold = false))
        add(Cell(value = formatAmount(tx.amount), bold = false))
        add(Cell(value = tx.description, bold = false))
        add(Cell(value = formatDate(tx.date), bold = false))
      }
    }

  private fun buildTotalRow(total: Long): List<Cell> =
    listOf(
      Cell(value = "", bold = false),
      Cell(value = "مجموع:", bold = true),
      Cell(value = formatAmount(total), bold = true),
      Cell(value = "", bold = false),
      Cell(value = "", bold = false)
    )

  private fun buildLoansSheet(loans: List<Loan>): SheetData {
    val headers = listOf("ردیف", "نام شخص", "نوع", "مبلغ اولیه", "مبلغ باقیمانده", "توضیحات", "تاریخ", "وضعیت")
    val rows =
      loans.mapIndexed { index, loan ->
        listOf(
          Cell(value = (index + 1).toString(), bold = false),
          Cell(value = loan.personName, bold = false),
          Cell(value = if (loan.type == "DEBTOR") "طلبکار" else "بدهکار", bold = false),
          Cell(value = formatAmount(loan.originalAmount), bold = false),
          Cell(value = formatAmount(loan.remainingAmount), bold = false),
          Cell(value = loan.description, bold = false),
          Cell(value = formatDate(loan.date), bold = false),
          Cell(value = if (loan.isSettled) "تسویه شده" else "باز", bold = false)
        )
      }
    return SheetData(name = "وام\u200Cها و قرض\u200Cها", headers = headers, rows = rows, summaryRow = null)
  }

  private fun buildInstallmentsSheet(installments: List<Installment>): SheetData {
    val headers = listOf("ردیف", "عنوان", "مبلغ", "تاریخ سررسید", "وضعیت", "یادداشت")
    val rows =
      installments.mapIndexed { index, inst ->
        listOf(
          Cell(value = (index + 1).toString(), bold = false),
          Cell(value = inst.title, bold = false),
          Cell(value = formatAmount(inst.amount), bold = false),
          Cell(value = formatDate(inst.dueDate), bold = false),
          Cell(value = if (inst.isPaid) "پرداخت شده" else "پرداخت نشده", bold = false),
          Cell(value = inst.notes, bold = false)
        )
      }
    return SheetData(name = "اقساط", headers = headers, rows = rows, summaryRow = null)
  }

  // ─── Helpers ─────────────────────────────────────────────────────

  private fun formatAmount(value: Long): String = CurrencyFormatter.format(value)

  private fun formatDate(timestamp: Long): String {
    val jDate = JalaliCalendarHelper.gregorianToJalali(timestamp)
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    return "${jDate.year}/${jDate.month.toString().padStart(
      2,
      '0'
    )}/${jDate.day.toString().padStart(
      2,
      '0'
    )} - ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
  }

  private fun generateFilename(): String {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val d = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val min = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    val s = cal.get(Calendar.SECOND).toString().padStart(2, '0')
    return "hesabyar_report_${y}${m}${d}_${h}${min}$s.xlsx"
  }
}
