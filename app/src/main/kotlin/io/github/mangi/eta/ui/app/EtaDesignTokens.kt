package io.github.mangi.eta.ui.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.Colors

/**
 * ETA-owned application-wide design system.
 *
 * The ideas of semantic tokens and a page-to-card surface ladder are informed
 * by Kelivo, but no Kelivo (AGPL) source or graphical assets are incorporated.
 * This is implemented independently for Compose + Miuix.
 */
internal object EtaDesignTokens {
    val SpaceXxs = 4.dp
    val SpaceXs = 8.dp
    val SpaceSm = 12.dp
    val SpaceMd = 16.dp
    val SpaceLg = 20.dp
    val SpaceXl = 24.dp
    val CardRadius = 24.dp
    val ButtonRadius = 22.dp
    val DialogRadius = 28.dp
    val MinTouchHeight = 48.dp
    val PreferenceMinHeight = 56.dp
    val IconSize = 24.dp
}

/**
 * Derive elevation containers from the *active* wallpaper/user theme. Do not
 * re-seed an already-resolved Monet scheme or override custom primary hues.
 */
internal fun Colors.withEtaSurfaceLadder(): Colors {
    val page = background
    val dark = page.luminance() < 0.45f
    val low = lerp(page, if (dark) Color.White else Color.White, if (dark) 0.035f else 0.35f)
    val card = lerp(page, Color.White, if (dark) 0.085f else 0.65f)
    val high = lerp(page, Color.White, if (dark) 0.125f else 0.82f)
    val highest = if (dark) lerp(page, Color.White, 0.17f) else lerp(page, onBackground, 0.055f)
    return copy(
        surfaceContainerLowest = page,
        surfaceContainerLow = low,
        surfaceContainer = card,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
    )
}

/** Color roles are resolved at the root so ALL destinations use one palette. */
internal data class EtaSemanticPalette(
    val success: Color,
    val warning: Color,
    val error: Color,
    val running: Color,
    val idle: Color,
)

internal fun Colors.etaSemanticPalette(): EtaSemanticPalette {
    val dark = background.luminance() < 0.45f
    val successBase = if (dark) Color(0xFF80CFA1) else Color(0xFF16683E)
    val warningBase = if (dark) Color(0xFFFFCB78) else Color(0xFF875100)
    return EtaSemanticPalette(
        success = readableForeground(lerp(successBase, primary, 0.08f), surface, minimum = 3.0),
        warning = readableForeground(lerp(warningBase, primary, 0.08f), surface, minimum = 3.0),
        error = readableForeground(error, surface, minimum = 3.0),
        running = readableForeground(primary, surface, minimum = 3.0),
        idle = readableForeground(onSurfaceVariantSummary, surface, minimum = 3.0),
    )
}
