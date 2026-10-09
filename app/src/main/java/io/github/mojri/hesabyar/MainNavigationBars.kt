package io.github.mojri.hesabyar

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens

@Composable
internal fun CompactMainContent(
  innerPadding: PaddingValues,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(innerPadding),
    contentAlignment = Alignment.TopCenter
  ) {
    content()
  }
}

@Composable
internal fun ExpandedMainContent(
  innerPadding: PaddingValues,
  currentTab: String,
  onTabSelected: (String) -> Unit,
  onMoreClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  Row(modifier = Modifier.fillMaxSize()) {
    MainNavigationRail(
      currentTab = currentTab,
      tabs = MAIN_TABS,
      onTabSelected = onTabSelected,
      onMoreClick = onMoreClick
    )
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding).weight(1f),
      contentAlignment = Alignment.TopCenter
    ) {
      content()
    }
  }
}

@Composable
internal fun MainBottomNavigation(
  currentTab: String,
  tabs: List<NavigationTabItem> = MAIN_TABS,
  onTabSelected: (String) -> Unit,
  onMoreClick: () -> Unit,
) {
  val moreLabel = stringResource(R.string.nav_tab_more)
  val tabLabels = tabs.associate { it.id to stringResource(it.labelRes) }
  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = ElevationTokens.Level4
  ) {
    tabs.forEach { item ->
      val label = tabLabels.getValue(item.id)
      NavigationBarItem(
        selected = currentTab == item.id,
        onClick = { onTabSelected(item.id) },
        icon = { Icon(imageVector = item.icon, contentDescription = label) },
        label = {
          Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
          )
        },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
          )
      )
    }
    NavigationBarItem(
      selected = currentTab in MORE_MENU_TABS,
      onClick = onMoreClick,
      icon = { Icon(imageVector = Icons.Filled.MoreHoriz, contentDescription = moreLabel) },
      label = {
        Text(
          moreLabel,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      },
      colors =
        NavigationBarItemDefaults.colors(
          selectedIconColor = MaterialTheme.colorScheme.primary,
          selectedTextColor = MaterialTheme.colorScheme.primary,
          indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    )
  }
}

@Composable
internal fun MainNavigationRail(
  currentTab: String,
  tabs: List<NavigationTabItem> = MAIN_TABS,
  onTabSelected: (String) -> Unit,
  onMoreClick: () -> Unit,
) {
  val moreLabel = stringResource(R.string.nav_tab_more)
  val tabLabels = tabs.associate { it.id to stringResource(it.labelRes) }
  NavigationRail(
    modifier = Modifier.fillMaxHeight(),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    tabs.forEach { item ->
      val label = tabLabels.getValue(item.id)
      NavigationRailItem(
        selected = currentTab == item.id,
        onClick = { onTabSelected(item.id) },
        icon = { Icon(imageVector = item.icon, contentDescription = label) },
        label = {
          Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
          )
        },
        colors =
          NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
          )
      )
    }
    NavigationRailItem(
      selected = currentTab in MORE_MENU_TABS,
      onClick = onMoreClick,
      icon = { Icon(imageVector = Icons.Filled.MoreHoriz, contentDescription = moreLabel) },
      label = {
        Text(
          moreLabel,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold
        )
      },
      colors =
        NavigationRailItemDefaults.colors(
          selectedIconColor = MaterialTheme.colorScheme.primary,
          selectedTextColor = MaterialTheme.colorScheme.primary,
          indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    )
  }
}

internal data class NavigationTabItem(
  val id: String,
  @StringRes val labelRes: Int,
  val icon: ImageVector,
)

internal val MORE_MENU_TABS = listOf(TAB_ANALYTICS, TAB_REPORTS, TAB_SETTINGS)

internal val MAIN_TABS =
  listOf(
    NavigationTabItem(TAB_DASHBOARD, R.string.nav_tab_dashboard, Icons.Filled.Dashboard),
    NavigationTabItem(TAB_ASSISTANT, R.string.nav_tab_assistant, Icons.Filled.AutoAwesome),
    NavigationTabItem(TAB_DEBTS, R.string.nav_tab_debts, Icons.Filled.AccountBalance)
  )
