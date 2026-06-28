package io.github.mojri.hesabyar.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExcelExporter(private val context: Context) {

    data class ExportResult(
        val file: File,
        val transactionCount: Int,
        val incomeCount: Int,
        val expenseCount: Int,
        val loanCount: Int,
        val installmentCount: Int
    )

    private val categoryMap = mutableMapOf<Long, Category>()
    private val sharedStrings = mutableListOf<String>()

    private fun internString(s: String): Int {
        val idx = sharedStrings.indexOf(s)
        if (idx >= 0) return idx
        sharedStrings.add(s)
        return sharedStrings.size - 1
    }

    suspend fun export(
        transactions: List<Transaction>,
        loans: List<Loan>,
        installments: List<Installment>,
        categories: List<Category>,
        getPaymentsForLoan: suspend (Long) -> List<PaymentHistory>
    ): ExportResult {
        categoryMap.clear()
        categoryMap.putAll(categories.associateBy { it.id })
        sharedStrings.clear()

        val incomeTransactions = transactions.filter { it.type == "INCOME" }
        val expenseTransactions = transactions.filter { it.type == "EXPENSE" }

        val sheets = listOf(
            createTransactionsSheet(transactions),
            createIncomeSheet(incomeTransactions),
            createExpensesSheet(expenseTransactions),
            createLoansSheet(loans),
            createInstallmentsSheet(installments)
        )

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "hesabyar_report_$timestamp.xlsx")

        writeXlsx(file, sheets)

        return ExportResult(
            file = file,
            transactionCount = transactions.size,
            incomeCount = incomeTransactions.size,
            expenseCount = expenseTransactions.size,
            loanCount = loans.size,
            installmentCount = installments.size
        )
    }

    // ─── Sheet data classes ──────────────────────────────────────────

    private data class SheetDef(val name: String, val headers: List<String>, val dataRows: List<List<String>>, val summaryRow: List<String>?)

    // ─── Sheet builders ──────────────────────────────────────────────

    private const val OTHER_CATEGORY_NAME = "سایر"

    private fun createTransactionsSheet(transactions: List<Transaction>): SheetDef {
        val headers = listOf("ردیف", "نوع", "دسته‌بندی", "مبلغ", "توضیحات", "تاریخ")
        val rows = transactions.map { tx ->
            listOf(
                "",
                if (tx.type == "INCOME") "دریافتی" else "پرداختی",
                categoryMap[tx.categoryId]?.name ?: OTHER_CATEGORY_NAME,
                formatAmount(tx.amount),
                tx.description,
                formatDate(tx.date)
            )
        }
        return SheetDef("همه تراکنش‌ها", headers, rows, null)
    }

    private fun createIncomeSheet(incomeTransactions: List<Transaction>): SheetDef {
        val headers = listOf("ردیف", "دسته‌بندی", "مبلغ", "توضیحات", "تاریخ")
        val rows = incomeTransactions.map { tx ->
            listOf(
                "",
                categoryMap[tx.categoryId]?.name ?: "سایر",
                formatAmount(tx.amount),
                tx.description,
                formatDate(tx.date)
            )
        }
        val total = incomeTransactions.sumOf { it.amount }
        val summary = listOf("", "مجموع:", formatAmount(total), "", "")
        return SheetDef("دریافتی‌ها", headers, rows, summary)
    }

    private fun createExpensesSheet(expenseTransactions: List<Transaction>): SheetDef {
        val headers = listOf("ردیف", "دسته‌بندی", "مبلغ", "توضیحات", "تاریخ")
        val rows = expenseTransactions.map { tx ->
            listOf(
                "",
                categoryMap[tx.categoryId]?.name ?: "سایر",
                formatAmount(tx.amount),
                tx.description,
                formatDate(tx.date)
            )
        }
        val total = expenseTransactions.sumOf { it.amount }
        val summary = listOf("", "مجموع:", formatAmount(total), "", "")
        return SheetDef("پرداختی‌ها", headers, rows, summary)
    }

    private fun createLoansSheet(loans: List<Loan>): SheetDef {
        val headers = listOf("ردیف", "نام شخص", "نوع", "مبلغ اولیه", "مبلغ باقیمانده", "توضیحات", "تاریخ", "وضعیت")
        val rows = loans.map { loan ->
            listOf(
                "",
                loan.personName,
                if (loan.type == "DEBTOR") "طلبکار" else "بدهکار",
                formatAmount(loan.originalAmount),
                formatAmount(loan.remainingAmount),
                loan.description,
                formatDate(loan.date),
                if (loan.isSettled) "تسویه شده" else "باز"
            )
        }
        return SheetDef("وام‌ها و قرض‌ها", headers, rows, null)
    }

    private fun createInstallmentsSheet(installments: List<Installment>): SheetDef {
        val headers = listOf("ردیف", "عنوان", "مبلغ", "تاریخ سررسید", "وضعیت", "یادداشت")
        val rows = installments.map { inst ->
            listOf(
                "",
                inst.title,
                formatAmount(inst.amount),
                formatDate(inst.dueDate),
                if (inst.isPaid) "پرداخت شده" else "پرداخت نشده",
                inst.notes
            )
        }
        return SheetDef("اقساط", headers, rows, null)
    }

    // ─── XLSX zip writer ─────────────────────────────────────────────

    private fun writeXlsx(file: File, sheets: List<SheetDef>) {
        sheets.forEach { sheet ->
            sheet.headers.forEach { internString(it) }
            sheet.dataRows.forEach { row -> row.forEach { internString(it) } }
            sheet.summaryRow?.forEach { internString(it) }
        }

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml(sheets.size))
            writeEntry(zip, "_rels/.rels", relsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml(sheets))
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
            writeEntry(zip, "xl/styles.xml", stylesXml())
            writeEntry(zip, "xl/sharedStrings.xml", sharedStringsXml())

            sheets.forEachIndexed { index, sheet ->
                writeEntry(zip, "xl/worksheets/sheet${index + 1}.xml", worksheetXml(sheet, index))
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    // ─── XML generators ──────────────────────────────────────────────

    private fun contentTypesXml(sheetCount: Int): String {
        val overrides = (1..sheetCount).joinToString("\n") {
            "  <Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
$overrides
</Types>"""
    }

    private fun relsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml(sheets: List<SheetDef>): String {
        val refs = sheets.mapIndexed { i, sheet ->
            "    <sheet name=\"${esc(sheet.name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
$refs
  </sheets>
</workbook>"""
    }

    private fun workbookRelsXml(sheetCount: Int): String {
        val rels = (0 until sheetCount).map { i ->
            "  <Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>"
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$rels
  <Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId${sheetCount + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""
    }

    private fun stylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/></font>
    <font><b/><sz val="12"/></font>
    <font><b/><sz val="11"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1F3864"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFDCE6F1"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color auto="1"/></left>
      <right style="thin"><color auto="1"/></right>
      <top style="thin"><color auto="1"/></top>
      <bottom style="thin"><color auto="1"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>
  </cellXfs>
</styleSheet>"""

    private fun sharedStringsXml(): String {
        val items = sharedStrings.joinToString("\n") { "  <si><t>${esc(it)}</t></si>" }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">
$items
</sst>"""
    }

    private fun worksheetXml(sheet: SheetDef, sheetIndex: Int): String {
        val colCount = sheet.headers.size

        val colWidths = (0 until colCount).map { colIdx ->
            val headerLen = sheet.headers[colIdx].length
            val dataMax = sheet.dataRows.maxOfOrNull { row -> row.getOrNull(colIdx)?.length ?: 0 } ?: 0
            val summaryMax = sheet.summaryRow?.getOrNull(colIdx)?.length ?: 0
            maxOf(headerLen, dataMax, summaryMax, 8) + 4
        }
        val colsXml = colWidths.mapIndexed { idx, width ->
            "    <col min=\"${idx + 1}\" max=\"${idx + 1}\" width=\"$width\" customWidth=\"1\"/>"
        }.joinToString("\n")

        val headerCells = sheet.headers.mapIndexed { colIdx, header ->
            val ref = "${columnLetter(colIdx)}1"
            val si = sharedStrings.indexOf(header)
            "<c r=\"$ref\" t=\"s\" s=\"1\"><v>$si</v></c>"
        }.joinToString("")
        val headerRow = "    <row r=\"1\" ht=\"22\" customHeight=\"1\">$headerCells</row>"

        var rowNum = 2
        val dataRows = sheet.dataRows.map { row ->
            val rowIdx = rowNum
            val cells = row.mapIndexed { colIdx, value ->
                val ref = "${columnLetter(colIdx)}$rowIdx"
                if (colIdx == 0) {
                    val rowNumberValue = rowIdx - 1
                    "<c r=\"$ref\" t=\"n\" s=\"2\"><v>$rowNumberValue</v></c>"
                } else {
                    val si = sharedStrings.indexOf(value)
                    if (si >= 0) {
                        "<c r=\"$ref\" t=\"s\" s=\"2\"><v>$si</v></c>"
                    } else {
                        "<c r=\"$ref\" s=\"2\"/>"
                    }
                }
            }.joinToString("")
            val xml = "    <row r=\"$rowIdx\">$cells</row>"
            rowNum++
            xml
        }

        val summaryRowXml = sheet.summaryRow?.let { summary ->
            val cells = summary.mapIndexed { colIdx, value ->
                val ref = "${columnLetter(colIdx)}$rowNum"
                val si = sharedStrings.indexOf(value)
                val style = if (colIdx == 1 || colIdx == 2) "3" else "2"
                if (si >= 0) {
                    "<c r=\"$ref\" t=\"s\" s=\"$style\"><v>$si</v></c>"
                } else {
                    "<c r=\"$ref\" s=\"$style\"/>"
                }
            }.joinToString("")
            "    <row r=\"$rowNum\">$cells</row>"
        }

        val allRows = listOf(headerRow) + dataRows + listOfNotNull(summaryRowXml)

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
           xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheetViews>
    <sheetView tabSelected="${if (sheetIndex == 0) 1 else 0}" rightToLeft="1">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <cols>
$colsXml
  </cols>
  <sheetData>
${allRows.joinToString("\n")}
  </sheetData>
</worksheet>"""
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun columnLetter(index: Int): String {
        val sb = StringBuilder()
        var i = index
        while (i >= 0) {
            sb.append('A' + (i % 26))
            i = i / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun formatAmount(value: Long): String = "${value / 1000} تومان"

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.US).format(Date(timestamp))
}
