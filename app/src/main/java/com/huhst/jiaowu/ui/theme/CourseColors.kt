package com.huhst.jiaowu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * 课表专用分类色板。
 *
 * ## 为什么这里写了字面色值
 *
 * 界面本身（导航栏、按钮、卡片、文字）的颜色**一律走 `MaterialTheme.colorScheme` 的角色**，
 * 这一点没有变。
 *
 * 但课表需要「按课程区分颜色」，而配色方案是**单色紫系**：
 * 容器类角色只有 primary / secondary / tertiary 三个近邻色相，
 * 从它们派生的颜色彼此太像（实测评价是「太丑」），
 * 靠明暗硬拉又会把课表变成深浅不一的紫色块。
 *
 * 所以课程颜色单独用一套**分类色板**：这是数据可视化用的分类色，
 * 与「UI 主题色」是两件事 —— M3 自己也是这么分工的
 * （主题色管界面，数据可视化另有 categorical palette）。
 *
 * ## 取值原则
 *
 * 明度都落在浅色区间，保证深色文字始终可读（对比度均 > 7:1）。
 * **顺序按「互相最不像」排布**：实测本校一学期 8~9 门课，只用到前 9 个颜色，
 * 所以把紫/橙/绿/蓝/玫红/琥珀/青/红/橄榄这 9 个差异最大的排在前面，
 * 偏中性的灰、以及容易和紫/蓝混的靛与天蓝沉到最后。
 */
private val CourseColorPairs: List<Pair<Color, Color>> = listOf(
    Color(0xFFEADDFF) to Color(0xFF21005D), // 紫
    Color(0xFFFFDBC8) to Color(0xFF341100), // 橙
    Color(0xFFC8E6C9) to Color(0xFF072711), // 绿
    Color(0xFFD6E3FF) to Color(0xFF001B3F), // 蓝
    Color(0xFFFFD8E4) to Color(0xFF3E001D), // 玫红
    Color(0xFFFBE7A8) to Color(0xFF2A1D00), // 琥珀
    Color(0xFFC2E7E1) to Color(0xFF00201C), // 青
    Color(0xFFFFDAD6) to Color(0xFF410002), // 红
    Color(0xFFE3E8C8) to Color(0xFF1B1F05), // 橄榄
    Color(0xFFE0E0FF) to Color(0xFF1B1B3A), // 靛
    Color(0xFFDCDCDC) to Color(0xFF1F1F1F), // 灰
    Color(0xFFCFE9F7) to Color(0xFF001E2C), // 天蓝
)

/** 课程色板：12 组「底色 + 文字色」。 */
fun coursePalette(): List<Pair<Color, Color>> = CourseColorPairs

/**
 * 课程名 → 颜色。
 *
 * 按课程名**排序后依次分配**，因此：
 *  - 同一门课每次拿到的颜色都相同（排序稳定，重启/同步都不变色）
 *  - 课程数不超过色板容量时不会撞色
 */
@Composable
fun rememberCourseColors(names: List<String>): Map<String, Pair<Color, Color>> =
    remember(names) {
        names.distinct().sorted().withIndex()
            .associate { (index, name) -> name to CourseColorPairs[index % CourseColorPairs.size] }
    }
