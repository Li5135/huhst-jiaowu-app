package com.huhst.jiaowu.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** 浅色方案。本 App 只做浅色模式，不提供深色变体。 */
private val LightColors = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,

    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,

    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,

    background = surface,
    onBackground = onSurface,

    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = surfaceTint,

    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,

    surfaceBright = surfaceBright,
    surfaceDim = surfaceDim,

    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,

    outline = outline,
    outlineVariant = outlineVariant,

    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,

    scrim = scrim,
)

/**
 * 应用主题。
 *
 * 动效统一使用 [MotionScheme.standard]：屏幕过渡与状态变化平滑、不回弹。
 */
@Composable
fun JiaowuTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = LightColors,
        motionScheme = MotionScheme.standard(),
        typography = JiaowuTypography,
        shapes = JiaowuShapes,
        content = content,
    )
}
