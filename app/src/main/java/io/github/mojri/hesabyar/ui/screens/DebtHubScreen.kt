package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.ui.BankLoanViewModel
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.PersonViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
private fun sectionTint(selected: Boolean): Color =
  if (selected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

@Composable
fun DebtHubScreen(
  initialSection: DebtSection = DebtSection.INSTALLMENTS,
  initialPersonsDirectionFilter: LoanDirectionFilter = LoanDirectionFilter.ALL,
  installmentViewModel: InstallmentViewModel,
  bankLoanViewModel: BankLoanViewModel,
  personViewModel: PersonViewModel,
  settingsViewModel: SettingsViewModel,
  modifier: Modifier = Modifier
) {
  var section by remember { mutableStateOf(initialSection) }
  var selectedPerson by remember { mutableStateOf<Pair<Long, String>?>(null) }

  Column(modifier = modifier.fillMaxSize()) {
    DebtHubTabBar(section = section, onSectionChange = { section = it })

    when (section) {
      DebtSection.INSTALLMENTS ->
        InstallmentScreen(
          installmentViewModel = installmentViewModel,
          settingsViewModel = settingsViewModel,
          bankLoanViewModel = bankLoanViewModel,
          modifier = Modifier.fillMaxSize()
        )
      DebtSection.BANK_LOANS ->
        BankLoanScreen(
          bankLoanViewModel = bankLoanViewModel,
          modifier = Modifier.fillMaxSize()
        )
      DebtSection.PERSONS ->
        PersonsScreen(
          personViewModel = personViewModel,
          initialDirectionFilter = initialPersonsDirectionFilter,
          onPersonClick = { personId, personName ->
            selectedPerson = personId to personName
          },
          modifier = Modifier.fillMaxSize()
        )
    }

    selectedPerson?.let { (id, name) ->
      PersonDetailSheet(
        personId = id,
        personName = name,
        personViewModel = personViewModel,
        onDismiss = { selectedPerson = null }
      )
    }
  }
}

@Composable
private fun DebtHubTabBar(
  section: DebtSection,
  onSectionChange: (DebtSection) -> Unit
) {
  SecondaryScrollableTabRow(
    selectedTabIndex = DebtSection.entries.indexOf(section),
    edgePadding = SpacingTokens.md,
    containerColor = MaterialTheme.colorScheme.surface,
    divider = {}
  ) {
    DebtSection.entries.forEach { s ->
      val selected = s == section
      LeadingIconTab(
        selected = selected,
        onClick = { onSectionChange(s) },
        text = {
          Text(
            s.label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
          )
        },
        icon = {
          Icon(
            imageVector = s.icon,
            contentDescription = null,
            tint = sectionTint(selected)
          )
        },
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = sectionTint(false)
      )
    }
  }
}
