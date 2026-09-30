package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.ui.screens.DebtSection
import io.github.mojri.hesabyar.ui.screens.LoanDirectionFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for [resolveInitialNavigation] and [DebtsTabState] (plans/011 Phase 3).
 *
 * Runs without Robolectric so the jacoco report records the mapping coverage.
 */
class MainActivityNavigationTest {
  @Test
  fun deepLinksOpenDebtsTab() {
    val deepLinks =
      listOf(
        DEEP_LINK_LOANS,
        DEEP_LINK_INSTALLMENTS,
        DEEP_LINK_BANK_LOANS,
        DEEP_LINK_DEBTS,
        DEEP_LINK_PERSONS
      )
    for (link in deepLinks) {
      val (tab, _) = resolveInitialNavigation(link)
      assertEquals("Deep link $link opens the DEBTS tab", TAB_DEBTS, tab)
    }
  }

  @Test
  fun missingOrUnknownDeepLinkOpensDashboard() {
    assertEquals("No deep link opens DASHBOARD", TAB_DASHBOARD, resolveInitialNavigation(null).first)
    assertEquals(
      "Unknown deep link opens DASHBOARD",
      TAB_DASHBOARD,
      resolveInitialNavigation("NOPE").first
    )
  }

  @Test
  fun deepLinksMapToDebtSections() {
    assertEquals(DebtSection.PERSONS, resolveInitialNavigation(DEEP_LINK_LOANS).second)
    assertEquals(DebtSection.PERSONS, resolveInitialNavigation(DEEP_LINK_PERSONS).second)
    assertEquals(DebtSection.BANK_LOANS, resolveInitialNavigation(DEEP_LINK_BANK_LOANS).second)
    assertEquals(DebtSection.INSTALLMENTS, resolveInitialNavigation(DEEP_LINK_INSTALLMENTS).second)
    assertEquals(DebtSection.INSTALLMENTS, resolveInitialNavigation(null).second)
  }

  @Test
  fun debtsTabStateCopiesSectionAndFilter() {
    val initial = DebtsTabState(DebtSection.INSTALLMENTS, LoanDirectionFilter.ALL)
    val withDebtors = initial.copy(filter = LoanDirectionFilter.DEBTOR)
    assertEquals("Section unchanged on filter copy", DebtSection.INSTALLMENTS, withDebtors.section)
    assertEquals("Filter updated to DEBTOR", LoanDirectionFilter.DEBTOR, withDebtors.filter)

    val onPersons = withDebtors.copy(section = DebtSection.PERSONS)
    assertEquals("Section updated to PERSONS", DebtSection.PERSONS, onPersons.section)
    assertEquals("Filter persists across section change", LoanDirectionFilter.DEBTOR, onPersons.filter)
  }
}
