package io.github.mojri.hesabyar.ui.components

import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import org.junit.Assert.assertEquals
import org.junit.Test

class HesabyarFabTokenTest {
  @Test
  fun fabDimensionsTokensMatchSpec() {
    assertEquals("FAB size must be 56dp", 56.dp, Dimens.FABSize)
    assertEquals("FAB icon size must be 24dp", 24.dp, Dimens.FABIconSize)
    assertEquals("FAB clearance must be 80dp", 80.dp, Dimens.FabClearance)
  }
}
