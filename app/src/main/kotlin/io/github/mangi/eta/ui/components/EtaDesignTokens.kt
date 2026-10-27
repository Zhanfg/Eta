package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.app.readableForeground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * ETA-wide visual tokens, informed by the design-system practices used in
 * Kelivo. Written independently for Compose/Miuix, not copied from Flutter.
 *
 * Shared by chat, Agent, settings, provider, tool and workspace screens.
 */
internal object EtaDesignTokens {
    val SpacingXxs = 4.dp
    val SpacingXs = 8.dp
    val SpacingSm = 12.dp
    val SpacingMd = 16.dp
    val SpacingLg = 20.dp
    val SpacingXl = 24.dp
    val SpacingXxl = 32.dp
    val MinimumTouchTarget = 48.dp
    val MinimumRowHeight = 52.dp
    val RoundedCardRadius = 24.dp
    val EmptyStateIconSize = 48.dp
    val CompactBreakpoint = 600.dp
    val ExpandedBreakpoint = 840.dp
    val MaxReadablePageWidth = 800.dp
    val MaxWidePageWidth = 1040.dp
}

internal data class EtaSemanticTone(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val quiet: Color,
)

/**
 * Source semantics from the active wallpaper/custom/pure-black theme.
 * Foregrounds are contrast repaired against actual Miuix surfaces rather
 * than using one fixed green or yellow across dark and light themes.
 */
@Composable
internal fun etaSemanticTone(): EtaSemanticTone {
    val colors = MiuixTheme.colorScheme
    return semanticToneForSurface(
        surface = colors.surface,
        error = colors.error,
        secondary = colors.onSurfaceVariantSummary,
    )
}

/** Pure contrast policy suitable for JVM regression tests and future themes. */
internal fun semanticToneForSurface(surface: Color, error: Color, secondary: Color): EtaSemanticTone {
    val dark = surface.luminance() < 0.48f
    return EtaSemanticTone(
        success = readableForeground(
            if (dark) Color(0xFF76D5A0) else Color(0xFF147443), surface, 4.5
        ),
        warning = readableForeground(
            if (dark) Color(0xFFFFCD70) else Color(0xFF885400), surface, 4.5
        ),
        danger = readableForeground(error, surface, 4.5),
        quiet = readableForeground(secondary, surface, 4.5),
    )
}
