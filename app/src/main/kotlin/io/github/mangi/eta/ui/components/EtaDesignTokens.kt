package io.github.mangi.eta.ui.components

import androidx.compose.ui.unit.dp

/**
 * Shared ETA layout/motion tokens. Inspired by Kelivo's design-token discipline,
 * independently authored for ETA's Miuix/Compose components.
 *
 * Changes here affect home, conversation surfaces, provider/skills/settings
 * groups, tool cards and permissions alike.
 */
internal object EtaDesignTokens {
    val SpaceXxs = 4.dp
    val SpaceXs = 8.dp
    val SpaceSm = 12.dp
    val SpaceMd = 16.dp
    val SpaceLg = 20.dp
    val SpaceXl = 24.dp
    val SpaceXxl = 32.dp

    val CardRadius = 24.dp
    val EmptyStateIconRadius = 16.dp
    val ChatComposerRadius = 20.dp
    val SmallControlRadius = 12.dp

    val IconSize = 24.dp
    val StatusIndicatorSize = 12.dp
    val MinimumTapTarget = 48.dp
    val PreferenceRowHeight = 56.dp
    val FeatureCardHeight = 136.dp

    const val MotionFastMillis = 140
    const val MotionNormalMillis = 230
    const val MotionLongMillis = 340
}
