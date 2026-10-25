package io.github.mangi.eta.ui.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.Colors

/**
 * Accessibility repair applied *after* Monet, user accent and pure-black overrides.
 * Keep original hue where possible; nudge only foreground when its composited
 * WCAG contrast is too low. Do not globally force the same black/white typography.
 */
internal fun Color.wcagContrastAgainst(background: Color): Double {
    val bg = background.compositeOver(Color.White)
    val fg = compositeOver(bg)
    val a = fg.luminance().toDouble()
    val b = bg.luminance().toDouble()
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

internal fun readableForeground(foreground: Color, background: Color, minimum: Double = 4.5): Color {
    val bg = background.compositeOver(Color.White)
    val effective = foreground.compositeOver(bg)
    if (effective.wcagContrastAgainst(bg) >= minimum) return foreground
    val target = if (Color.Black.wcagContrastAgainst(bg) > Color.White.wcagContrastAgainst(bg)) {
        Color.Black
    } else {
        Color.White
    }
    var lo = 0f
    var hi = 1f
    repeat(20) {
        val midpoint = (lo + hi) / 2f
        if (lerp(effective, target, midpoint).wcagContrastAgainst(bg) >= minimum) hi = midpoint else lo = midpoint
    }
    return lerp(effective, target, hi).copy(alpha = 1f)
}

internal fun Colors.readableTextRoles(): Colors = copy(
    onBackground = readableForeground(onBackground, background),
    onBackgroundVariant = readableForeground(onBackgroundVariant, background),
    onSurface = readableForeground(onSurface, surface),
    onSurfaceSecondary = readableForeground(onSurfaceSecondary, surface),
    onSurfaceVariantSummary = readableForeground(onSurfaceVariantSummary, surfaceVariant),
    onSurfaceVariantActions = readableForeground(onSurfaceVariantActions, surfaceVariant),
    onSurfaceContainer = readableForeground(onSurfaceContainer, surfaceContainer),
    onSurfaceContainerVariant = readableForeground(onSurfaceContainerVariant, surfaceContainer),
    onSurfaceContainerHigh = readableForeground(onSurfaceContainerHigh, surfaceContainerHigh),
    onSurfaceContainerHighest = readableForeground(onSurfaceContainerHighest, surfaceContainerHighest),
    onPrimary = readableForeground(onPrimary, primary),
    onPrimaryVariant = readableForeground(onPrimaryVariant, primaryVariant),
    onPrimaryContainer = readableForeground(onPrimaryContainer, primaryContainer),
    onSecondary = readableForeground(onSecondary, secondary),
    onSecondaryVariant = readableForeground(onSecondaryVariant, secondaryVariant),
    onSecondaryContainer = readableForeground(onSecondaryContainer, secondaryContainer),
    onSecondaryContainerVariant = readableForeground(onSecondaryContainerVariant, secondaryContainerVariant),
    onError = readableForeground(onError, error),
    onErrorContainer = readableForeground(onErrorContainer, errorContainer),
)
