package com.huhst.jiaowu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/*
 * 排版：Roboto + M3 标准字重。
 *
 * Android 上 FontFamily.SansSerif 即 Roboto。注意 Roboto 不含中文字形，
 * 中文会由系统字体回退渲染（FontFamily 的 fallback 机制），这是预期行为。
 */
private val Roboto = FontFamily.SansSerif

val JiaowuTypography: Typography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Roboto),
        displayMedium = t.displayMedium.copy(fontFamily = Roboto),
        displaySmall = t.displaySmall.copy(fontFamily = Roboto),

        headlineLarge = t.headlineLarge.copy(fontFamily = Roboto),
        headlineMedium = t.headlineMedium.copy(fontFamily = Roboto),
        headlineSmall = t.headlineSmall.copy(fontFamily = Roboto),

        titleLarge = t.titleLarge.copy(fontFamily = Roboto),
        titleMedium = t.titleMedium.copy(fontFamily = Roboto),
        titleSmall = t.titleSmall.copy(fontFamily = Roboto),

        bodyLarge = t.bodyLarge.copy(fontFamily = Roboto),
        bodyMedium = t.bodyMedium.copy(fontFamily = Roboto),
        bodySmall = t.bodySmall.copy(fontFamily = Roboto),

        labelLarge = t.labelLarge.copy(fontFamily = Roboto),
        labelMedium = t.labelMedium.copy(fontFamily = Roboto),
        labelSmall = t.labelSmall.copy(fontFamily = Roboto),
    )
}
