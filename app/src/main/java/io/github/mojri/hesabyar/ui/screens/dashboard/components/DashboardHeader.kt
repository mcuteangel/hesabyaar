package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
internal fun DashboardHeader(settingsViewModel: SettingsViewModel) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = SpacingTokens.md),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      IconCircle(
        icon = Icons.Filled.AccountBalanceWallet,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        backgroundAlpha = 1.0f,
        iconSize = Dimens.IconMedium,
        containerSize = 44.dp
      )
      Column {
        Text(
          text = "حسابیار هوشمند",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = "دستیار مالی هوشمند شما",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        )
      }
    }

    IconButton(
      onClick = { settingsViewModel.toggleDarkMode() },
      modifier =
        Modifier
          .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
          .size(Dimens.ButtonHeight)
    ) {
      Icon(
        imageVector = if (settingsViewModel.isDarkMode.value) Icons.Filled.LightMode else Icons.Filled.DarkMode,
        contentDescription = "تغییر تم",
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary
      )
    }
  }
}
