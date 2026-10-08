package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.ui.screens.DebtSection
import io.github.mojri.hesabyar.ui.screens.LoanDirectionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

  @Test
  fun dashboardDebtorCardNavigationClearsQueryAndSwitchesToDebtors() {
    var searchCleared = false
    var currentTab = TAB_DASHBOARD
    var debtsState = DebtsTabState(DebtSection.INSTALLMENTS, LoanDirectionFilter.ALL)

    val debtsNav =
      createDebtsNavActions(
        currentTabProvider = { currentTab },
        onCurrentTabChange = { currentTab = it },
        onDebtsStateChange = { debtsState = it },
        onResetPersonSearch = { searchCleared = true }
      )

    debtsNav.onShowDebtors()

    assertEquals("Search query reset", true, searchCleared)
    assertEquals("Navigated to DEBTS tab", TAB_DEBTS, currentTab)
    assertEquals("Debts section set to PERSONS", DebtSection.PERSONS, debtsState.section)
    assertEquals("Filter set to DEBTOR", LoanDirectionFilter.DEBTOR, debtsState.filter)
  }

  @Test
  fun dashboardCreditorCardNavigationClearsQueryAndSwitchesToCreditors() {
    var searchCleared = false
    var currentTab = TAB_DASHBOARD
    var debtsState = DebtsTabState(DebtSection.INSTALLMENTS, LoanDirectionFilter.ALL)

    val debtsNav =
      createDebtsNavActions(
        currentTabProvider = { currentTab },
        onCurrentTabChange = { currentTab = it },
        onDebtsStateChange = { debtsState = it },
        onResetPersonSearch = { searchCleared = true }
      )

    debtsNav.onShowCreditors()

    assertEquals("Search query reset", true, searchCleared)
    assertEquals("Navigated to DEBTS tab", TAB_DEBTS, currentTab)
    assertEquals("Debts section set to PERSONS", DebtSection.PERSONS, debtsState.section)
    assertEquals("Filter set to CREDITOR", LoanDirectionFilter.CREDITOR, debtsState.filter)
  }

  @Test
  fun tabSelectedEnteringDebtsFromOtherTabResetsPersonSearch() {
    var searchCleared = false
    var currentTab = TAB_DASHBOARD
    var debtsState = DebtsTabState(DebtSection.INSTALLMENTS, LoanDirectionFilter.ALL)

    val debtsNav =
      createDebtsNavActions(
        currentTabProvider = { currentTab },
        onCurrentTabChange = { currentTab = it },
        onDebtsStateChange = { debtsState = it },
        onResetPersonSearch = { searchCleared = true }
      )

    debtsNav.onTabSelected(TAB_DEBTS)

    assertEquals("Search query reset on entering DEBTS", true, searchCleared)
    assertEquals("Current tab updated to DEBTS", TAB_DEBTS, currentTab)
  }

  @Test
  fun backWalksVisitedTabsThenHomeThenAsksToExit() {
    var history = listOf(TAB_DASHBOARD).pushTab(TAB_DEBTS).pushTab(TAB_REPORTS)
    assertEquals(listOf(TAB_DASHBOARD, TAB_DEBTS, TAB_REPORTS), history)

    history = history.popTab(TAB_DASHBOARD)!!
    assertEquals("Back returns to the previous tab", TAB_DEBTS, history.last())
    history = history.popTab(TAB_DASHBOARD)!!
    assertEquals("Back reaches the dashboard", TAB_DASHBOARD, history.last())
    assertNull("Back on the dashboard asks to exit", history.popTab(TAB_DASHBOARD))
  }

  @Test
  fun deepLinkStartFallsBackToDashboardBeforeExit() {
    val history = listOf(TAB_DEBTS).popTab(TAB_DASHBOARD)
    assertEquals(listOf(TAB_DASHBOARD), history)
  }

  @Test
  fun revisitingTabMovesItToTopWithoutDuplicates() {
    val history =
      listOf(TAB_DASHBOARD)
        .pushTab(TAB_DEBTS)
        .pushTab(TAB_REPORTS)
        .pushTab(TAB_DEBTS)
        .pushTab(TAB_DEBTS)
    assertEquals(listOf(TAB_DASHBOARD, TAB_REPORTS, TAB_DEBTS), history)
  }
}
