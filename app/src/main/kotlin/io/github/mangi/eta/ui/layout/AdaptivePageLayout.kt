package io.github.mangi.eta.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.app.LocalPlatformDensity
import io.github.mangi.eta.ui.components.EtaDesignTokens

private val WideScreenMinWidth = EtaDesignTokens.CompactBreakpoint

/**
 * Responsive reading width grows continuously on tablets/desktop. The fixed
 * 800.dp cap made content excessively narrow on wide screens; jumping from
 * 800 to 1040 at a breakpoint creates a distracting discontinuity.
 */
internal fun adaptiveContentWidth(viewport: Dp): Dp {
    val extra = (viewport - 1040.dp)
        .coerceIn(0.dp, EtaDesignTokens.MaxWidePageWidth - EtaDesignTokens.MaxReadablePageWidth)
    return EtaDesignTokens.MaxReadablePageWidth + extra
}

internal fun adaptiveSidePadding(viewport: Dp): Dp =
    ((viewport - adaptiveContentWidth(viewport)) / 2).coerceAtLeast(0.dp)

@Composable
fun rememberIsWideScreen(): Boolean {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalPlatformDensity.current ?: LocalDensity.current
    return isWideScreen(containerSize.width, density)
}

internal fun isWideScreen(containerWidthPx: Int, density: androidx.compose.ui.unit.Density): Boolean =
    with(density) { containerWidthPx.toDp() >= WideScreenMinWidth }

/**
 * 列表本身保持全宽，只把内容限制在居中的最大宽度内，避免宽屏两侧形成滚动死区。
 */
@Composable
fun WidePageContent(
    modifier: Modifier = Modifier,
    content: @Composable (sidePadding: Dp) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        content(adaptiveSidePadding(maxWidth))
    }
}

/**
 * 二级页内容只补水平方向的屏幕缺口与手势区；顶部由 TopAppBar、底部由页面末尾留白负责。
 */
@Composable
fun Modifier.horizontalCutoutPadding(): Modifier = windowInsetsPadding(
    WindowInsets.displayCutout
        .union(WindowInsets.navigationBars)
        .only(WindowInsetsSides.Horizontal),
)
