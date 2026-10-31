package io.github.mojri.hesabyar.ui.designsystem

import androidx.compose.ui.unit.dp

object Dimens {
  val IconSmall = 16.dp
  val IconMedium = 24.dp
  val IconLarge = 32.dp

  /** Large standalone icon size for error states and assistant banners (44dp). */
  val IconXLarge = 44.dp

  /** Compact icon size for menu/dropdown leading icons (Material 18dp spec). */
  val IconMenu = 18.dp

  /** Icon size used inside medium avatar circles (40dp container). */
  val IconAvatarMedium = 20.dp

  /** Extra small container size for circular icon badges (e.g. KPI cards, 28dp). */
  val ContainerXSmall = 28.dp

  /** Small container size for icon badges and compact action boxes (36dp). */
  val ContainerSmall = 36.dp

  /** Medium container size for prominent header icon badges (44dp). */
  val ContainerMedium = 44.dp

  val AvatarSmall = 32.dp
  val AvatarMedium = 40.dp
  val AvatarLarge = 56.dp

  val ButtonHeight = 48.dp

  /** Minimum touch target size for interactive elements (48dp, Material accessibility guideline). */
  val MinTouchTarget = 48.dp

  val FABSize = 56.dp
  val FABIconSize = 24.dp

  val DividerThickness = 1.dp

  /** Clearance at the bottom of scrollable lists so content is not obscured by a FAB. */
  val FabClearance = 80.dp

  val TabHeight = 48.dp

  /** Maximum width for standard alert/confirm dialogs per Material 3 specification (560dp). */
  val DialogMaxWidth = 560.dp

  /** Subtle brand card background tint (8%). */
  const val SUBTLE_BRAND_ALPHA = 0.08f

  /** Alpha for status/confidence background cards (10%). */
  const val CARD_CONTAINER_ALPHA = 0.10f

  /** Alpha for compact badges and pill indicators on surfaces (12%). */
  const val CHIP_CONTAINER_ALPHA = 0.12f

  /** Default alpha for tinted icon-circle backgrounds (0–1). */
  const val ICON_CIRCLE_BACKGROUND_ALPHA = 0.15f

  /** Alpha for subtle surface tints (e.g. selected card backgrounds). */
  const val SURFACE_TINT_ALPHA = 0.2f

  /** Alpha for selected category-filter cards (25%). */
  const val SELECTED_SURFACE_ALPHA = 0.25f

  /** Alpha for highlighted banners and alert containers (30%). */
  const val BANNER_CONTAINER_ALPHA = 0.30f

  /** Alpha for muted foreground icons and inactive indicators (60%). */
  const val MUTED_FOREGROUND_ALPHA = 0.60f

  /** Alpha for secondary/body text on surfaces. */
  const val SECONDARY_TEXT_ALPHA = 0.7f
}
