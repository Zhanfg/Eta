package io.github.mangi.eta.ui.layout

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePageLayoutTest {
    @Test
    fun pageWidthHasNoAbruptJumpAtDesktopBreakpoints() {
        assertEquals(800.dp, adaptiveContentWidth(600.dp))
        assertEquals(800.dp, adaptiveContentWidth(1040.dp))
        assertEquals(960.dp, adaptiveContentWidth(1200.dp))
        assertEquals(1040.dp, adaptiveContentWidth(1280.dp))
        assertEquals(1040.dp, adaptiveContentWidth(2000.dp))
    }

    @Test
    fun sidePaddingNeverBecomesNegativeOnPhoneOrTablet() {
        assertEquals(0.dp, adaptiveSidePadding(320.dp))
        assertEquals(0.dp, adaptiveSidePadding(600.dp))
        assertEquals(120.dp, adaptiveSidePadding(1040.dp))
        assertEquals(120.dp, adaptiveSidePadding(1200.dp))
        assertEquals(280.dp, adaptiveSidePadding(1600.dp))
    }

    @Test
    fun wideScreenDecisionUsesTheProvidedUnscaledDensity() {
        val widthPx = 1200

        assertTrue(isWideScreen(widthPx, Density(density = 2f)))
        assertFalse(isWideScreen(widthPx, Density(density = 2.2f)))
    }
}
