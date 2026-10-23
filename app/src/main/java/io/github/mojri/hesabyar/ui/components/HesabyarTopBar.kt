package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.R

/**
 * Standard top app bar for Hesabyar screens and dialog sheets.
 *
 * Provides consistent typography, RTL navigation icon, and marks the title
 * with [heading] semantics for TalkBack accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabyarTopBar(
  title: String,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  colors: TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.surface,
      titleContentColor = MaterialTheme.colorScheme.onSurface,
    ),
  scrollBehavior: TopAppBarScrollBehavior? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  TopAppBar(
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
      )
    },
    modifier = modifier,
    navigationIcon = {
      if (onBack != null) {
        IconButton(onClick = onBack) {
          Icon(
            imageVector = Icons.Filled.ArrowForward,
            contentDescription = stringResource(R.string.action_back),
          )
        }
      }
    },
    actions = actions,
    colors = colors,
    scrollBehavior = scrollBehavior,
  )
}
