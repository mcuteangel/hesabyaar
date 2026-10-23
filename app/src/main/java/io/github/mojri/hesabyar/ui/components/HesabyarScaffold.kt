package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.WindowSizeTokens

/**
 * Standard page scaffold for Hesabyar screens.
 *
 * Provides consistent top app bar navigation, floating action button placement,
 * bottom FAB clearance in [contentPadding], and adaptive content centering constrained
 * to [WindowSizeTokens.ContentMaxWidth] on medium and expanded displays.
 */
@Composable
fun HesabyarScaffold(
  modifier: Modifier = Modifier,
  title: String? = null,
  onBack: (() -> Unit)? = null,
  topBar: (@Composable () -> Unit)? = null,
  floatingActionButton: (@Composable () -> Unit)? = null,
  floatingActionButtonPosition: FabPosition = FabPosition.End,
  bottomBar: @Composable () -> Unit = {},
  snackbarHost: @Composable () -> Unit = {},
  containerColor: Color = MaterialTheme.colorScheme.background,
  contentMaxWidth: Dp = WindowSizeTokens.ContentMaxWidth,
  applyContentMaxWidth: Boolean = true,
  content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
  val resolvedTopBar = resolveTopBar(topBar = topBar, title = title, onBack = onBack)

  Scaffold(
    modifier = modifier,
    topBar = resolvedTopBar,
    bottomBar = bottomBar,
    snackbarHost = snackbarHost,
    floatingActionButton = floatingActionButton ?: {},
    floatingActionButtonPosition = floatingActionButtonPosition,
    containerColor = containerColor,
  ) { innerPadding ->
    AdaptiveContentContainer(
      modifier = Modifier.padding(innerPadding),
      contentMaxWidth = contentMaxWidth,
      applyContentMaxWidth = applyContentMaxWidth,
    ) {
      val scaffoldContentPadding =
        PaddingValues(
          bottom = if (floatingActionButton != null) Dimens.FabClearance else 0.dp,
        )
      content(scaffoldContentPadding)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun resolveTopBar(
  topBar: (@Composable () -> Unit)?,
  title: String?,
  onBack: (() -> Unit)?,
): @Composable () -> Unit =
  when {
    topBar != null -> topBar
    title != null -> {
      {
        TopAppBar(
          title = { Text(text = title, fontWeight = FontWeight.Bold) },
          navigationIcon = {
            if (onBack != null) {
              IconButton(onClick = onBack) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = stringResource(R.string.back_navigation_description),
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
      }
    }
    else -> {
      {}
    }
  }
