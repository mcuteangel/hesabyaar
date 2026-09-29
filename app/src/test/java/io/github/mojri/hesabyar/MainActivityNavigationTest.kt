package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.ui.screens.DebtSection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for [resolveInitialNavigation] (plans/011 Phase 3).
 *
 * Runs without Robolectric so the jacoco report records the mapping coverage.
 */
class MainActivityNavigationTest {
  @Test
  fun deepLinksOpenDebtsTab() {
    val deepLinks = listOf("LOANS", "INSTALLMENTS", "BANK_LOANS", "DEBTS", "PERSONS")
    for (link in deepLinks) {
      val (tab, _) = resolveInitialNavigation(link)
      assertEquals("Deep link $link opens the DEBTS tab", "DEBTS", tab)
    }
  }

  @Test
  fun missingOrUnknownDeepLinkOpensDashboard() {
    assertEquals("No deep link opens DASHBOARD", "DASHBOARD", resolveInitialNavigation(null).first)
    assertEquals(
      "Unknown deep link opens DASHBOARD",
      "DASHBOARD",
      resolveInitialNavigation("NOPE").first
    )
  }

  @Test
  fun deepLinksMapToDebtSections() {
    assertEquals(DebtSection.PERSONS, resolveInitialNavigation("LOANS").second)
    assertEquals(DebtSection.PERSONS, resolveInitialNavigation("PERSONS").second)
    assertEquals(DebtSection.BANK_LOANS, resolveInitialNavigation("BANK_LOANS").second)
    assertEquals(DebtSection.INSTALLMENTS, resolveInitialNavigation("INSTALLMENTS").second)
    assertEquals(DebtSection.INSTALLMENTS, resolveInitialNavigation(null).second)
  }
}
