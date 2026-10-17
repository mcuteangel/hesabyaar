package io.github.mojri.hesabyar.ui.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensTokenTest {
  @Test
  fun iconSizeTokensMatchDesignSpec() {
    assertEquals("IconSmall must be 16dp", 16.dp, Dimens.IconSmall)
    assertEquals("IconMenu must be 18dp", 18.dp, Dimens.IconMenu)
    assertEquals("IconAvatarMedium must be 20dp", 20.dp, Dimens.IconAvatarMedium)
    assertEquals("IconMedium must be 24dp", 24.dp, Dimens.IconMedium)
    assertEquals("IconLarge must be 32dp", 32.dp, Dimens.IconLarge)
    assertEquals("IconXLarge must be 44dp", 44.dp, Dimens.IconXLarge)
  }

  @Test
  fun containerSizeTokensMatchDesignSpec() {
    assertEquals("ContainerXSmall must be 28dp", 28.dp, Dimens.ContainerXSmall)
    assertEquals("ContainerSmall must be 36dp", 36.dp, Dimens.ContainerSmall)
    assertEquals("ContainerMedium must be 44dp", 44.dp, Dimens.ContainerMedium)
    assertEquals("AvatarSmall must be 32dp", 32.dp, Dimens.AvatarSmall)
    assertEquals("AvatarMedium must be 40dp", 40.dp, Dimens.AvatarMedium)
    assertEquals("AvatarLarge must be 56dp", 56.dp, Dimens.AvatarLarge)
  }

  @Test
  fun alphaTokensMatchDesignSpec() {
    assertEquals("SUBTLE_BRAND_ALPHA must be 0.08f", 0.08f, Dimens.SUBTLE_BRAND_ALPHA, 0.0001f)
    assertEquals("CARD_CONTAINER_ALPHA must be 0.10f", 0.10f, Dimens.CARD_CONTAINER_ALPHA, 0.0001f)
    assertEquals("CHIP_CONTAINER_ALPHA must be 0.12f", 0.12f, Dimens.CHIP_CONTAINER_ALPHA, 0.0001f)
    assertEquals("ICON_CIRCLE_BACKGROUND_ALPHA must be 0.15f", 0.15f, Dimens.ICON_CIRCLE_BACKGROUND_ALPHA, 0.0001f)
    assertEquals("SURFACE_TINT_ALPHA must be 0.20f", 0.20f, Dimens.SURFACE_TINT_ALPHA, 0.0001f)
    assertEquals("BANNER_CONTAINER_ALPHA must be 0.30f", 0.30f, Dimens.BANNER_CONTAINER_ALPHA, 0.0001f)
    assertEquals("MUTED_FOREGROUND_ALPHA must be 0.60f", 0.60f, Dimens.MUTED_FOREGROUND_ALPHA, 0.0001f)
    assertEquals("SECONDARY_TEXT_ALPHA must be 0.70f", 0.70f, Dimens.SECONDARY_TEXT_ALPHA, 0.0001f)
  }

  @Test
  fun allAlphaTokensAreValidProbabilities() {
    val alphas =
      listOf(
        Dimens.SUBTLE_BRAND_ALPHA,
        Dimens.CARD_CONTAINER_ALPHA,
        Dimens.CHIP_CONTAINER_ALPHA,
        Dimens.ICON_CIRCLE_BACKGROUND_ALPHA,
        Dimens.SURFACE_TINT_ALPHA,
        Dimens.BANNER_CONTAINER_ALPHA,
        Dimens.MUTED_FOREGROUND_ALPHA,
        Dimens.SECONDARY_TEXT_ALPHA,
      )

    for (alpha in alphas) {
      assertTrue("Alpha value $alpha must be strictly positive", alpha > 0f)
      assertTrue("Alpha value $alpha must not exceed 1.0f", alpha <= 1.0f)
    }
  }
}
