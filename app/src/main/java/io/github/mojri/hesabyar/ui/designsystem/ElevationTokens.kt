package io.github.mojri.hesabyar.ui.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Elevation levels follow the canonical Material 3 elevation scale (dp).
// Level1 (1dp) and Level5 (12dp) were removed — they had zero consumers.
// Only levels that components actually use are kept.
object ElevationTokens {
  val Level0: Dp = 0.dp
  val Level2: Dp = 3.dp
  val Level3: Dp = 6.dp
  val Level4: Dp = 8.dp
}
