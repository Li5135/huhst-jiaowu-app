package com.huhst.jiaowu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * M3 Expressive 形状：
 *  - 卡片 20dp  → shapes.medium
 *  - 对话框 28dp → shapes.extraLarge
 *  - 按钮胶囊形 → 由 Button 的默认形状提供（ButtonDefaults.shape）
 */
val JiaowuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
