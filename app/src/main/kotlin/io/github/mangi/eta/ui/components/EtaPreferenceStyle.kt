package io.github.mangi.eta.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ImportContacts
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import io.github.mangi.eta.R
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import io.github.mangi.eta.ui.app.readableForeground
import io.github.mangi.eta.ui.app.readableTextRoles
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object EtaPreferenceColors {
    val Blue = Color(0xFF0080FF)
    val Green = StatusSuccess
    val Orange = Color(0xFFFF7700)
    val Yellow = StatusWarning
}

internal object EtaPreferenceDefaults {
    val SidePadding = 16.dp
    val GroupSpacing = 16.dp
    val IconSize = 24.dp
    val IconTextGap = 16.dp
    val ContentStart = SidePadding + IconSize + IconTextGap
    fun contentStart(hasLeading: Boolean) = if (hasLeading) ContentStart else SidePadding
    val RowMinHeight = 52.dp
}

@Composable
internal fun EtaPreferenceTheme(content: @Composable () -> Unit) {
    // Do not override the wallpaper palette or user-chosen accent when entering
    // Settings/Providers. Every page consumes the exact same app-root palette.
    // The root already applies the surface ladder and final text contrast.
    content()
}

@Composable
internal fun EtaPreferenceGroup(
    modifier: Modifier = Modifier.padding(horizontal = EtaPreferenceDefaults.SidePadding).padding(bottom = EtaPreferenceDefaults.GroupSpacing),
    insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    EtaCard(modifier = modifier, insideMargin = insideMargin, colors = colors, content = content)
}

@Composable
internal fun EtaPreferenceGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
internal fun EtaPreferenceIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.onBackground,
    enabled: Boolean = true,
) {
    // 满幅轮廓稍作光学校正，但所有图标都占相同宽度，保证正文和分割线对齐。
    val glyphSize = when (icon) {
        Icons.Rounded.Extension, Icons.Rounded.TheaterComedy, Icons.AutoMirrored.Rounded.MenuBook -> 22.dp
        else -> EtaPreferenceDefaults.IconSize
    }
    val replacement = when (icon) {
        Icons.Rounded.Cloud -> R.drawable.ic_eta_lucide_cloud
        Icons.Rounded.Psychology -> R.drawable.ic_eta_lucide_brain
        Icons.Rounded.Layers -> R.drawable.ic_eta_lucide_layers
        Icons.AutoMirrored.Rounded.MenuBook, Icons.Rounded.ImportContacts -> R.drawable.ic_eta_lucide_book_open
        Icons.Rounded.AccountTree -> R.drawable.ic_eta_lucide_network
        Icons.Rounded.Dashboard -> R.drawable.ic_eta_lucide_layout_dashboard
        Icons.Rounded.Palette -> R.drawable.ic_eta_lucide_palette
        Icons.Rounded.Security -> R.drawable.ic_eta_lucide_shield_check
        Icons.Rounded.Terminal -> R.drawable.ic_eta_lucide_terminal
        else -> null
    }
    if (replacement != null) {
        EtaPreferenceLucideIcon(drawableRes = replacement, modifier = modifier, tint = tint, enabled = enabled)
        return
    }
    Box(modifier.size(EtaPreferenceDefaults.IconSize), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(glyphSize),
            tint = if (enabled) readableForeground(tint, MiuixTheme.colorScheme.surfaceVariant, minimum = 3.0)
                else MiuixTheme.colorScheme.disabledOnSurface,
        )
    }
}

@Composable
internal fun EtaPreferenceDivider(hasLeading: Boolean = true, modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = EtaPreferenceDefaults.contentStart(hasLeading), end = EtaPreferenceDefaults.SidePadding),
        thickness = 0.33.dp,
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.1f),
    )
}

/**
 * Unified 24dp Lucide outline icon slot. Vector drawables remain screen-density
 * independent; tint is chosen from the current dynamic palette with 3:1 contrast.
 */
@Composable
internal fun EtaPreferenceLucideIcon(
    @DrawableRes drawableRes: Int,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    val foreground = if (enabled) {
        readableForeground(tint, colors.surfaceVariant, minimum = 3.0)
    } else colors.disabledOnSurface
    Box(modifier.size(EtaPreferenceDefaults.IconSize), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            modifier = Modifier.size(EtaPreferenceDefaults.IconSize),
            colorFilter = ColorFilter.tint(foreground),
        )
    }
}
