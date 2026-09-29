package io.github.mojri.hesabyar.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for [DebtSection] enum metadata (plans/011 Phase 3).
 *
 * Runs without Robolectric to ensure jacoco registers enum coverage.
 */
class DebtSectionTest {
  @Test
  fun allSectionsHaveIdentifiersAndLabels() {
    assertEquals("Three debt sections registered", 3, DebtSection.entries.size)
    for (section in DebtSection.entries) {
      assertNotNull("ID defined", section.id)
      assertNotNull("Label defined", section.label)
      assertNotNull("Icon defined", section.icon)
    }
  }

  @Test
  fun personsSectionPropertiesMatchSpecification() {
    val persons = DebtSection.PERSONS
    assertEquals("ID matches tab key", "PERSONS", persons.id)
    assertEquals("Persian label", "اشخاص", persons.label)
  }
}
