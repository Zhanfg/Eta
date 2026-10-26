package io.github.mangi.eta.ui.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaAccessibilityPaletteTest {
    @Test fun alreadyLegibleTextIsNotRepainted() {
        assertEquals(Color(0xFF202124), etaReadableForeground(Color(0xFF202124), Color.White))
    }

    @Test fun wallpaperAccentWithLowContrastIsRepaired() {
        val page = Color(0xFF8FB4E8)
        val text = etaReadableForeground(Color(0xFFE8F4FF), page)
        assertTrue(text.etaContrastAgainst(page) >= 4.5 - 0.001)
    }

    @Test fun pureBlackAndAmoledRemainAccessible() {
        val text = etaReadableForeground(Color(0xFF555555), Color.Black)
        assertTrue(text.etaContrastAgainst(Color.Black) >= 4.5 - 0.001)
    }

    @Test fun translucentLabelsAreCheckedAfterCompositing() {
        val ghost = Color(0x66000000)
        assertTrue(ghost.etaContrastAgainst(Color.White) < 4.5)
        assertTrue(etaReadableForeground(ghost, Color.White).etaContrastAgainst(Color.White) >= 4.5 - 0.001)
    }

    @Test fun semanticControlContrastUsesThreeToOneWhenSpecified() {
        val buttonColor = etaReadableForeground(Color(0xFFCCCCCC), Color.White, 3.0)
        assertTrue(buttonColor.etaContrastAgainst(Color.White) >= 3.0 - 0.001)
    }
}
