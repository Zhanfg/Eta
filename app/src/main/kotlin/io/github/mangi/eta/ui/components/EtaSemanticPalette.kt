package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.mangi.eta.ui.app.etaReadableForeground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared semantics for chat, preferences, status, lists and overlay surfaces. */
internal data class EtaSemanticPalette(
    val page: Color,
    val card: Color,
    val softFill: Color,
    val raisedFill: Color,
    val divider: Color,
    val success: Color,
    val warning: Color,
    val running: Color,
)

/**
 * ETA-native color derivation inspired by Kelivo's separation of semantic and
 * raw palette roles, not a port or copy of Kelivo (AGPL) implementation.
 *
 * All derived roles depend on the active wallpaper/custom/pure-black palette.
 */
internal fun deriveEtaSemanticPalette(
    background: Color,
    foreground: Color,
    primary: Color,
    surface: Color,
): EtaSemanticPalette {
    val dark = background.luminance() < 0.5f
    val white = Color.White
    val black = Color.Black
    val page = background
    val card = if (dark) lerp(surface, white, 0.07f) else lerp(surface, white, 0.26f)
    val fill = lerp(surface, if (dark) white else black, if (dark) 0.09f else 0.045f)
    val raised = lerp(card, if (dark) white else black, if (dark) 0.07f else 0.03f)
    val line = foreground.copy(alpha = if (dark) 0.16f else 0.12f)
    val success = etaReadableForeground(
        if (dark) Color(0xFF81C784) else Color(0xFF26783C),
        page, 3.0,
    )
    val warning = etaReadableForeground(
        if (dark) Color(0xFFFFCC80) else Color(0xFF9C5700),
        page, 3.0,
    )
    return EtaSemanticPalette(
        page = page,
        card = card,
        softFill = fill,
        raisedFill = raised,
        divider = line,
        success = success,
        warning = warning,
        running = etaReadableForeground(primary, page, 3.0),
    )
}

@Composable
internal fun etaSemanticPalette(): EtaSemanticPalette {
    val scheme = MiuixTheme.colorScheme
    return deriveEtaSemanticPalette(
        background = scheme.background,
        foreground = scheme.onBackground,
        primary = scheme.primary,
        surface = scheme.surface,
    )
}
