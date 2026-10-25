package io.github.mangi.eta.ui.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test fun darkTextOnLightSurfaceIsPreserved() {
        val fg = Color(0xFF202124)
        assertEquals(fg, readableForeground(fg, Color.White))
    }

    @Test fun mutedLightTextOnLightSurfaceBecomesReadable() {
        val result = readableForeground(Color(0x66999999), Color(0xFFF7F7F7))
        assertTrue(result.wcagContrastAgainst(Color(0xFFF7F7F7)) >= 4.5 - 0.001)
    }

    @Test fun nearBlackSurfaceHasReadableText() {
        val result = readableForeground(Color(0xFF555555), Color.Black)
        assertTrue(result.wcagContrastAgainst(Color.Black) >= 4.5 - 0.001)
    }

    @Test fun saturatedDynamicSurfaceHasAccessibleForeground() {
        val background = Color(0xFF8FB4E8)
        val result = readableForeground(Color(0xFFE8F4FF), background)
        assertTrue(result.wcagContrastAgainst(background) >= 4.5 - 0.001)
    }

    @Test fun alphaCompositingIsMeasuredRatherThanRawRgb() {
        val ghost = Color(0x66000000)
        assertTrue(ghost.wcagContrastAgainst(Color.White) < 4.5)
        assertTrue(readableForeground(ghost, Color.White).wcagContrastAgainst(Color.White) >= 4.5 - 0.001)
    }
}
