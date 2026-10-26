package io.github.mangi.eta.ui.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.Colors

/**
 * App-wide text accessibility. The final Monet / custom / pure-black palette
 * gets contrast checked *after* all wallpaper and user transformations.
 *
 * Kelivo-inspired principle, independently implemented for ETA: the theme
 * system is the only typography color authority, not each individual screen.
 */
internal fun Color.etaContrastAgainst(background: Color): Double {
    val opaqueBackground = background.compositeOver(Color.White)
    val opaqueForeground = compositeOver(opaqueBackground)
    val top = maxOf(opaqueBackground.luminance(), opaqueForeground.luminance()).toDouble()
    val bottom = minOf(opaqueBackground.luminance(), opaqueForeground.luminance()).toDouble()
    return (top + 0.05) / (bottom + 0.05)
}

internal fun etaReadableForeground(foreground: Color, background: Color, minimum: Double = 4.5): Color {
    val bg = background.compositeOver(Color.White)
    val fg = foreground.compositeOver(bg)
    if (fg.etaContrastAgainst(bg) >= minimum) return foreground
    val blackContrast = Color.Black.etaContrastAgainst(bg)
    val whiteContrast = Color.White.etaContrastAgainst(bg)
    val target = if (blackContrast >= whiteContrast) Color.Black else Color.White
    var low = 0f
    var high = 1f
    repeat(20) {
        val mid = (low + high) / 2f
        if (lerp(fg, target, mid).etaContrastAgainst(bg) >= minimum) high = mid else low = mid
    }
    return lerp(fg, target, high).copy(alpha = 1f)
}

internal fun Colors.etaAccessibleText(): Colors = copy(
    onBackground = etaReadableForeground(onBackground, background),
    onBackgroundVariant = etaReadableForeground(onBackgroundVariant, background),
    onSurface = etaReadableForeground(onSurface, surface),
    onSurfaceSecondary = etaReadableForeground(onSurfaceSecondary, surface),
    onSurfaceVariantSummary = etaReadableForeground(onSurfaceVariantSummary, surfaceVariant),
    onSurfaceVariantActions = etaReadableForeground(onSurfaceVariantActions, surfaceVariant),
    onSurfaceContainer = etaReadableForeground(onSurfaceContainer, surfaceContainer),
    onSurfaceContainerVariant = etaReadableForeground(onSurfaceContainerVariant, surfaceContainer),
    onSurfaceContainerHigh = etaReadableForeground(onSurfaceContainerHigh, surfaceContainerHigh),
    onSurfaceContainerHighest = etaReadableForeground(onSurfaceContainerHighest, surfaceContainerHighest),
    onPrimary = etaReadableForeground(onPrimary, primary),
    onPrimaryVariant = etaReadableForeground(onPrimaryVariant, primaryVariant),
    onPrimaryContainer = etaReadableForeground(onPrimaryContainer, primaryContainer),
    onSecondary = etaReadableForeground(onSecondary, secondary),
    onSecondaryVariant = etaReadableForeground(onSecondaryVariant, secondaryVariant),
    onSecondaryContainer = etaReadableForeground(onSecondaryContainer, secondaryContainer),
    onSecondaryContainerVariant = etaReadableForeground(onSecondaryContainerVariant, secondaryContainerVariant),
    onError = etaReadableForeground(onError, error),
    onErrorContainer = etaReadableForeground(onErrorContainer, errorContainer),
)
