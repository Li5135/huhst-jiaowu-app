package com.huhst.jiaowu.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Material 3 浅色配色方案 —— Purple 系。
 *
 * 约定：UI 中所有颜色都必须通过 MaterialTheme.colorScheme 的角色名引用，
 *      不得在界面代码里出现字面颜色值。本文件是这些角色的唯一来源。
 */

// Primary
val primary = Color(0xFF6750A4)
val onPrimary = Color(0xFFFFFFFF)
val primaryContainer = Color(0xFFEADDFF)
val onPrimaryContainer = Color(0xFF21005D)

// Secondary
val secondary = Color(0xFF635A75)
val onSecondary = Color(0xFFFFFFFF)
val secondaryContainer = Color(0xFFE8DEF8)
val onSecondaryContainer = Color(0xFF1D192B)

// Tertiary
val tertiaryContainer = Color(0xFFFFD8E4)
val onTertiaryContainer = Color(0xFF31111D)

// Surface
val surface = Color(0xFFFEF7FF)
val onSurface = Color(0xFF1D1B20)
val surfaceVariant = Color(0xFFE7E0EC)
val onSurfaceVariant = Color(0xFF49454F)
val surfaceContainerLowest = Color(0xFFFFFFFF)
val surfaceContainerLow = Color(0xFFF7F2FA)
val surfaceContainer = Color(0xFFF3EDF7)
val surfaceContainerHigh = Color(0xFFECE6F0)
val surfaceContainerHighest = Color(0xFFE6E0E9)

// Outline
val outline = Color(0xFF79747E)
val outlineVariant = Color(0xFFCAC4D0)

// Inverse
val inverseSurface = Color(0xFF322F35)
val inverseOnSurface = Color(0xFFF5EFF7)
val inversePrimary = Color(0xFFD0BCFF)

// Error
val error = Color(0xFFB3261E)
val onError = Color(0xFFFFFFFF)
val errorContainer = Color(0xFFF9DEDC)
val onErrorContainer = Color(0xFF410E0B)

// 其他 M3 角色（方案未指定，取 Purple 系协调值，保证 lightColorScheme 完整）
val tertiary = Color(0xFF7D5260)
val onTertiary = Color(0xFFFFFFFF)
val secondaryFixedDim = Color(0xFFCBC2DB)
val surfaceTint = primary
val surfaceBright = Color(0xFFFEF7FF)
val surfaceDim = Color(0xFFDED8E1)
val scrim = Color(0xFF000000)
