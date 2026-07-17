package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
fun HesabyarCard(
  modifier: Modifier = Modifier,
  shape: Shape = ShapeTokens.Large,
  elevation: Dp = ElevationTokens.lg,
  border: BorderStroke? = null,
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  cardColors: androidx.compose.material3.CardColors =
    CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
  contentPadding: PaddingValues = PaddingValues(SpacingTokens.lg),
  content: @Composable ColumnScope.() -> Unit
) {
  Card(
    modifier = modifier,
    shape = shape,
    elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    border = border,
    colors = cardColors
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(contentPadding),
      horizontalAlignment = horizontalAlignment,
      verticalArrangement = verticalArrangement,
      content = content
    )
  }
}
