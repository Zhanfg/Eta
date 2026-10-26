package io.github.mangi.eta.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.mangi.eta.ui.app.etaContrastAgainst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaSemanticPaletteTest {
    @Test fun lightAndDarkRolesUseTheirOwnPageColor() {
        val light = deriveEtaSemanticPalette(Color.White, Color.Black, Color.Blue, Color(0xFFF4F4F4))
        val dark = deriveEtaSemanticPalette(Color.Black, Color.White, Color.Cyan, Color(0xFF161616))
        assertEquals(Color.White, light.page)
        assertEquals(Color.Black, dark.page)
        assertTrue(light.card.luminance() > dark.card.luminance())
    }

    @Test fun statusesKeepContrastOnLightAndAmoledPages() {
        for ((background, foreground) in listOf(Color.White to Color.Black, Color.Black to Color.White)) {
            val roles = deriveEtaSemanticPalette(background, foreground, Color(0xFF3482FF), background)
            assertTrue(roles.success.etaContrastAgainst(background) >= 3.0 - 0.001)
            assertTrue(roles.warning.etaContrastAgainst(background) >= 3.0 - 0.001)
            assertTrue(roles.running.etaContrastAgainst(background) >= 3.0 - 0.001)
        }
    }

    @Test fun surfaceRolesAdaptToWallpaperColors() {
        val mint = deriveEtaSemanticPalette(Color(0xFFA5DCCB), Color.Black, Color(0xFF007C67), Color(0xFFC3ECE0))
        val violet = deriveEtaSemanticPalette(Color(0xFFD2C0E5), Color.Black, Color(0xFF6748A4), Color(0xFFE1D4EF))
        assertTrue(mint.card != violet.card)
        assertTrue(mint.raisedFill != violet.raisedFill)
    }

    @Test fun sharedTouchTargetsAreNotTiny() {
        assertTrue(EtaDesignTokens.MinimumTapTarget.value >= 48f)
        assertTrue(EtaDesignTokens.PreferenceRowHeight.value >= 48f)
    }
}
