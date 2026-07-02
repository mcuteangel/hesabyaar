package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import io.github.mojri.hesabyar.ui.theme.DarkBackground
import io.github.mojri.hesabyar.ui.theme.DarkError
import io.github.mojri.hesabyar.ui.theme.DarkErrorContainer
import io.github.mojri.hesabyar.ui.theme.DarkInversePrimary
import io.github.mojri.hesabyar.ui.theme.DarkInverseSurface
import io.github.mojri.hesabyar.ui.theme.DarkOnBackground
import io.github.mojri.hesabyar.ui.theme.DarkOnError
import io.github.mojri.hesabyar.ui.theme.DarkOnErrorContainer
import io.github.mojri.hesabyar.ui.theme.DarkOnPrimary
import io.github.mojri.hesabyar.ui.theme.DarkOnPrimaryContainer
import io.github.mojri.hesabyar.ui.theme.DarkOnSecondary
import io.github.mojri.hesabyar.ui.theme.DarkOnSurface
import io.github.mojri.hesabyar.ui.theme.DarkOnSurfaceVariant
import io.github.mojri.hesabyar.ui.theme.DarkOnTertiary
import io.github.mojri.hesabyar.ui.theme.DarkOutline
import io.github.mojri.hesabyar.ui.theme.DarkOutlineVariant
import io.github.mojri.hesabyar.ui.theme.DarkPrimary
import io.github.mojri.hesabyar.ui.theme.DarkPrimaryContainer
import io.github.mojri.hesabyar.ui.theme.DarkScrim
import io.github.mojri.hesabyar.ui.theme.DarkSecondary
import io.github.mojri.hesabyar.ui.theme.DarkSurface
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceBright
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainer
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainerHigh
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainerHighest
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainerLow
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainerLowest
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceDim
import io.github.mojri.hesabyar.ui.theme.DarkTertiary
import io.github.mojri.hesabyar.ui.theme.LightBackground
import io.github.mojri.hesabyar.ui.theme.LightError
import io.github.mojri.hesabyar.ui.theme.LightErrorContainer
import io.github.mojri.hesabyar.ui.theme.LightInversePrimary
import io.github.mojri.hesabyar.ui.theme.LightInverseSurface
import io.github.mojri.hesabyar.ui.theme.LightOnBackground
import io.github.mojri.hesabyar.ui.theme.LightOnError
import io.github.mojri.hesabyar.ui.theme.LightOnErrorContainer
import io.github.mojri.hesabyar.ui.theme.LightOnPrimary
import io.github.mojri.hesabyar.ui.theme.LightOnPrimaryContainer
import io.github.mojri.hesabyar.ui.theme.LightOnSecondary
import io.github.mojri.hesabyar.ui.theme.LightOnSurface
import io.github.mojri.hesabyar.ui.theme.LightOnSurfaceVariant
import io.github.mojri.hesabyar.ui.theme.LightOnTertiary
import io.github.mojri.hesabyar.ui.theme.LightOutline
import io.github.mojri.hesabyar.ui.theme.LightOutlineVariant
import io.github.mojri.hesabyar.ui.theme.LightPrimary
import io.github.mojri.hesabyar.ui.theme.LightPrimaryContainer
import io.github.mojri.hesabyar.ui.theme.LightScrim
import io.github.mojri.hesabyar.ui.theme.LightSecondary
import io.github.mojri.hesabyar.ui.theme.LightSurface
import io.github.mojri.hesabyar.ui.theme.LightSurfaceBright
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainer
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainerHigh
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainerHighest
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainerLow
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainerLowest
import io.github.mojri.hesabyar.ui.theme.LightSurfaceDim
import io.github.mojri.hesabyar.ui.theme.LightTertiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.pow

/**
 * Verifies that the static dark/light color schemes used by HesabyarTheme
 * have distinct background and surface colors, ensuring visual separation
 * between light and dark modes.
 *
 * Skipped: dynamic color scheme on Android 12+ — that path calls
 * dynamicDarkColorScheme(context)/dynamicLightColorScheme(context) which
 * requires a real Context with system resource access. Compose UI test
 * on device covers that; these are pure constant checks.
 */
class ThemeTest {

    @Test
    fun `dark and light background colors are distinct`() {
        assertNotEquals("Dark and light backgrounds must differ", DarkBackground, LightBackground)
    }

    @Test
    fun `dark background is visually dark`() {
        // DarkBackground = Color(0xFF111315) — very low luminance
        assert(DarkBackground.luminance() < 0.1f) {
            "DarkBackground luminance ${DarkBackground.luminance()} should be < 0.1"
        }
    }

    @Test
    fun `light background is visually light`() {
        // LightBackground = Color(0xFFFDFBFF) — very high luminance
        assert(LightBackground.luminance() > 0.9f) {
            "LightBackground luminance ${LightBackground.luminance()} should be > 0.9"
        }
    }

    @Test
    fun `dark onSurface has sufficient contrast against dark background`() {
        // WCAG contrast ratio >= 4.5:1 for body text
        val ratio = contrastRatio(DarkOnSurface, DarkBackground)
        assert(ratio >= 4.5) {
            "Dark onSurface contrast $ratio should be >= 4.5"
        }
    }

    @Test
    fun `light onSurface has sufficient contrast against light background`() {
        val ratio = contrastRatio(LightOnSurface, LightBackground)
        assert(ratio >= 4.5) {
            "Light onSurface contrast $ratio should be >= 4.5"
        }
    }

    @Test
    fun `dark and light color schemes define all required surface container levels`() {
        // Surface container hierarchy must be ordered: lowest < low < default < high < highest
        val darkLevels = listOf(
            DarkSurfaceContainerLowest.luminance(),
            DarkSurfaceContainerLow.luminance(),
            DarkSurfaceContainer.luminance(),
            DarkSurfaceContainerHigh.luminance(),
            DarkSurfaceContainerHighest.luminance()
        )
        // In dark theme, container levels should be ordered low-to-high brightness
        for (i in 0 until darkLevels.size - 1) {
            assert(darkLevels[i] <= darkLevels[i + 1]) {
                "Dark surface container level $i luminance ${darkLevels[i]} should be <= level ${i + 1} ${darkLevels[i + 1]}"
            }
        }

        val lightLevels = listOf(
            LightSurfaceContainerLowest.luminance(),
            LightSurfaceContainerLow.luminance(),
            LightSurfaceContainer.luminance(),
            LightSurfaceContainerHigh.luminance(),
            LightSurfaceContainerHighest.luminance()
        )
        for (i in 0 until lightLevels.size - 1) {
            assert(lightLevels[i] >= lightLevels[i + 1]) {
                "Light surface container level $i luminance ${lightLevels[i]} should be >= level ${i + 1} ${lightLevels[i + 1]}"
            }
        }
    }

    @Test
    fun `dark primary is visually light for contrast on dark surfaces`() {
        assert(DarkPrimary.luminance() > 0.5f) {
            "DarkPrimary luminance ${DarkPrimary.luminance()} should be > 0.5"
        }
    }

    @Test
    fun `light primary is visually dark for contrast on light surfaces`() {
        assert(LightPrimary.luminance() < 0.4f) {
            "LightPrimary luminance ${LightPrimary.luminance()} should be < 0.4"
        }
    }

    @Test
    fun `error colors exist in both themes`() {
        assertNotEquals("Error must differ between themes", DarkError, LightError)
        assertNotEquals("ErrorContainer must differ", DarkErrorContainer, LightErrorContainer)
    }

    @Test
    fun `inverse surface flips foreground-background roles`() {
        // DarkInverseSurface should be light (like a light background)
        assert(DarkInverseSurface.luminance() > 0.5f) {
            "DarkInverseSurface should be light"
        }
        // LightInverseSurface should be dark
        assert(LightInverseSurface.luminance() < 0.5f) {
            "LightInverseSurface should be dark"
        }
    }

    @Test
    fun `outline colors have moderate visibility in both themes`() {
        val darkOutlineLum = DarkOutline.luminance()
        val lightOutlineLum = LightOutline.luminance()
        // Outlines should be mid-range, not invisible
        assert(darkOutlineLum > 0.1f && darkOutlineLum < 0.8f) {
            "DarkOutline luminance $darkOutlineLum out of range"
        }
        assert(lightOutlineLum > 0.1f && lightOutlineLum < 0.8f) {
            "LightOutline luminance $lightOutlineLum out of range"
        }
    }

    @Test
    fun `scrim is black in both themes`() {
        assertEquals("Dark scrim should be black", 0f, DarkScrim.red, 0.001f)
        assertEquals("Dark scrim should be black", 0f, DarkScrim.green, 0.001f)
        assertEquals("Dark scrim should be black", 0f, DarkScrim.blue, 0.001f)
        assertEquals("Light scrim should be black", 0f, LightScrim.red, 0.001f)
    }

    // Relative luminance per WCAG 2.0
    private fun Color.luminance(): Float {
        fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble().coerceAtLeast(bg.luminance().toDouble())
        val l2 = fg.luminance().toDouble().coerceAtMost(bg.luminance().toDouble())
        return (l1 + 0.05) / (l2 + 0.05)
    }
}
