package io.github.mangi.eta.ui.components

import androidx.compose.ui.graphics.Color
import io.github.mangi.eta.ui.app.wcagContrastAgainst
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaDesignTokensTest {
    @Test
    fun semanticStatusesAreLegibleOnLightSurface() {
        assertLegible(
            Color(0xFFF0F1F2),
            Color(0xFFD33333),
            Color(0xFF999999),
        )
    }

    @Test
    fun semanticStatusesAreLegibleOnPureBlackSurface() {
        assertLegible(
            Color.Black,
            Color(0xFFD33333),
            Color(0xFF777777),
        )
    }

    @Test
    fun semanticStatusesAreLegibleOnMonetLikeColoredSurfaces() {
        assertLegible(
            Color(0xFFF1ECFA),
            Color(0xFFB01F5B),
            Color(0xFF968B9E),
        )
        assertLegible(
            Color(0xFF24232B),
            Color(0xFFF16A8B),
            Color(0xFF756E86),
        )
    }

    private fun assertLegible(surface: Color, error: Color, secondary: Color) {
        val tones = semanticToneForSurface(surface, error, secondary)
        for (role in listOf(tones.success, tones.warning, tones.danger, tones.quiet)) {
            assertTrue(
                "Insufficient state contrast on $surface: $role",
                role.wcagContrastAgainst(surface) >= 4.49,
            )
        }
    }
}
