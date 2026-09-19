package com.huhst.jiaowu.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huhst.jiaowu.data.model.CourseSession
import com.huhst.jiaowu.data.repo.currentWeekOf
import com.huhst.jiaowu.data.repo.isSameDay
import com.huhst.jiaowu.data.repo.weekDayDates
import com.huhst.jiaowu.ui.theme.rememberCourseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.huhst.jiaowu.ui.components.EmptyState
import com.huhst.jiaowu.ui.components.LoadingPane
import com.huhst.jiaowu.ui.nav.Routes
import com.huhst.jiaowu.ui.vm.AppViewModel
import com.huhst.jiaowu.ui.vm.SyncUi

private val PeriodColumnWidth = 42.dp
private val HeaderHeight = 46.dp
private val PeriodHeight = 60.dp
private val MondayFirstLabels = listOf("一", "二", "三", "四", "五", "六", "日")
private val SundayFirstLabels = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 星期几（1=周一 … 7=周日）落在第几列。
 * 周日开头时周日排在最左，周一开头时周一位于最左。
 */
private fun columnIndexOf(weekday: Int, startsSunday: Boolean): Int =
    if (startsSunday) {
        if (weekday == 7) 0 else weekday.coerceIn(1, 6)
    } else {
        (weekday - 1).coerceIn(0, 6)
    }

/**
 * 课程表：按周查看，支持跨节次的连堂课。
 */
@Composable
fun ScheduleScreen(vm: AppViewModel, navController: NavController) {
    val data by vm.data.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val startsSunday = settings.weekStartsSunday

    val maxWeek = remember(data.sessions) {
        (data.sessions.flatMap { it.weeks }.maxOrNull() ?: 20).coerceIn(1, 30)
    }
    val firstUsedWeek = remember(data.sessions) {
        data.sessions.flatMap { it.weeks }.minOrNull() ?: 1
    }
    val maxPeriod = remember(data.sessions) {
        (data.sessions.maxOfOrNull { it.endPeriod } ?: 10).coerceIn(1, 14)
    }

    // 当前周由教学周历推算（第 1 周的周一日期 + 今天），会随日期自行推进。
    // 推算不出来（还没同步过周历）时才退回「第一门课所在的周」。
    val todayWeek = remember(data.weekCalendar, data.lastSyncAt, startsSunday) {
        currentWeekOf(data.weekCalendar, startsSunday)
    }
    val defaultWeek = if (todayWeek > 0) todayWeek.coerceIn(1, maxWeek) else firstUsedWeek

    var week by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(defaultWeek, data.sessions.size) {
        if (week == 0 && data.sessions.isNotEmpty()) week = defaultWeek
    }
    val displayWeek = if (week == 0) defaultWeek else week
    val isCurrentWeek = todayWeek > 0 && displayWeek == todayWeek

    val weekSessions = remember(data.sessions, displayWeek) {
        data.sessions.filter { it.weeks.isEmpty() || displayWeek in it.weeks }
    }

    // 这一周 7 天的具体日期 + 今天在第几列（用于表头显示几号、并高亮今天）
    val weekDates = remember(data.weekCalendar, displayWeek, startsSunday) {
        weekDayDates(data.weekCalendar, displayWeek, startsSunday)
    }
    val today = remember { Date() }
    val todayIndex = remember(weekDates, data.lastSyncAt) {
        weekDates.indexOfFirst { isSameDay(it, today) }
    }

    // 不同课程不同颜色，方便一眼分辨
    val courseColors = rememberCourseColors(data.sessions.map { it.name })

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "课程表",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = data.terms.firstOrNull { it.id == data.currentTermId }?.name
                        ?: "尚未获取学期信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { vm.syncNow() },
                enabled = sync !is SyncUi.Running,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "从教务系统同步",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // 周次切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(
                onClick = { week = (displayWeek - 1).coerceAtLeast(1) },
                enabled = displayWeek > 1,
            ) {
                Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "上一周")
            }
            Row(
                modifier = Modifier
                    .width(150.dp)
                    .clickable(enabled = todayWeek > 0) { week = todayWeek },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "第 $displayWeek 周",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isCurrentWeek) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "本周",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            IconButton(
                onClick = { week = (displayWeek + 1).coerceAtMost(maxWeek) },
                enabled = displayWeek < maxWeek,
            ) {
                Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "下一周")
            }
        }

        when {
            sync is SyncUi.Running && data.sessions.isEmpty() -> {
                LoadingPane(label = "正在从教务系统读取…")
            }

            data.sessions.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.DateRange,
                    title = "暂无课表",
                    description = "点右上角刷新按钮从教务系统同步课表。",
                    actionLabel = "立即同步",
                    onAction = { vm.syncNow() },
                )
            }

            else -> {
                ScheduleGrid(
                    sessions = weekSessions,
                    maxPeriod = maxPeriod,
                    weekDates = weekDates,
                    todayIndex = todayIndex,
                    startsSunday = startsSunday,
                    courseColors = courseColors,
                    onCourseClick = { session ->
                        navController.navigate(Routes.courseDetail(session.id))
                    },
                )
            }
        }
    }
}

@Composable
private fun ScheduleGrid(
    sessions: List<CourseSession>,
    maxPeriod: Int,
    weekDates: List<Date>,
    todayIndex: Int,
    startsSunday: Boolean,
    courseColors: Map<String, Pair<Color, Color>>,
    onCourseClick: (CourseSession) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        val dayWidth: Dp = (maxWidth - PeriodColumnWidth) / 7

        val monthLabel = remember(weekDates) {
            weekDates.firstOrNull()
                ?.let { SimpleDateFormat("M月", Locale.CHINA).format(it) }
                .orEmpty()
        }
        val dayOfMonth = remember(weekDates) {
            weekDates.map { SimpleDateFormat("d", Locale.CHINA).format(it) }
        }
        val highlight = MaterialTheme.colorScheme.primary
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val weekdayLabels = if (startsSunday) SundayFirstLabels else MondayFirstLabels

        Column {
            // 表头：左上角写这一周所在的月份，星期下面写具体几号
            Row(Modifier.height(HeaderHeight)) {
                Box(
                    modifier = Modifier
                        .width(PeriodColumnWidth)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (monthLabel.isNotEmpty()) {
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = muted,
                        )
                    }
                }
                weekdayLabels.forEachIndexed { index, label ->
                    val isToday = index == todayIndex
                    Column(
                        modifier = Modifier
                            .width(dayWidth)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isToday) highlight else muted,
                        )
                        if (dayOfMonth.size == 7) {
                            Text(
                                text = dayOfMonth[index],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) highlight else muted,
                            )
                        }
                    }
                }
            }

            // 节次行（作为网格背景）
            for (period in 1..maxPeriod) {
                Row(
                    modifier = Modifier.height(PeriodHeight),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .width(PeriodColumnWidth)
                            .height(PeriodHeight),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(
                            text = period.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    for (day in 1..7) {
                        Box(
                            modifier = Modifier
                                .width(dayWidth)
                                .height(PeriodHeight)
                                .padding(1.dp)
                                .background(
                                    // 所有格子一律同色。
                                    // 「今天」的变化只留在表头那一格（周几 + 几号变色），
                                    // 整列加深会把课表压得发闷，也让课程块的颜色变浑。
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(6.dp),
                                ),
                        )
                    }
                }
            }
        }

        // 课程块（叠在网格之上，跨节次的课按高度拉伸）
        sessions.forEach { session ->
            val dayIndex = columnIndexOf(session.weekday, startsSunday)
            val span = (session.endPeriod - session.startPeriod + 1).coerceAtLeast(1)
            // 按课程名取色：同一门课永远同一个颜色
            val (container, onContainer) = courseColors[session.name]
                ?: (MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer)

            Box(
                modifier = Modifier
                    .offset(
                        x = PeriodColumnWidth + dayWidth * dayIndex,
                        y = HeaderHeight + PeriodHeight * (session.startPeriod - 1),
                    )
                    .width(dayWidth)
                    .height(PeriodHeight * span)
                    .padding(1.dp)
                    .background(
                        color = container,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onCourseClick(session) }
                    .padding(4.dp),
            ) {
                Text(
                    text = buildString {
                        append(session.name)
                        if (session.classroom.isNotBlank()) {
                            append('\n')
                            append(session.classroom)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
