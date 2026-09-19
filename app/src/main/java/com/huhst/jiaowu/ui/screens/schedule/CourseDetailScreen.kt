package com.huhst.jiaowu.ui.screens.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huhst.jiaowu.ui.components.EmptyState
import com.huhst.jiaowu.ui.vm.AppViewModel

/** 课程详情：某一节课的完整信息。 */
@Composable
fun CourseDetailScreen(
    vm: AppViewModel,
    sessionId: String,
    navController: NavController,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val session = data.sessions.firstOrNull { it.id == sessionId }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = session?.name ?: "课程",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        if (session == null) {
            EmptyState(
                icon = Icons.Rounded.DateRange,
                title = "找不到这节课",
                description = "课表可能已更新，请返回后重新同步。",
                actionLabel = "重新同步",
                onAction = { vm.syncNow() },
            )
            return@Column
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                DetailRow("课程", session.name)
                DetailRow("教师", session.teacher.ifBlank { "未提供" })
                DetailRow("教室", session.classroom.ifBlank { "未提供" })
                DetailRow("时间", "${weekdayText(session.weekday)} 第 ${periodText(session.startPeriod, session.endPeriod)} 节")
                DetailRow("周次", weekText(session.weeks))
                if (session.credit.isNotBlank()) DetailRow("学分", session.credit)
                if (session.courseType.isNotBlank()) DetailRow("课程性质", session.courseType)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun weekdayText(weekday: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(weekday - 1) { "未知" }

private fun periodText(start: Int, end: Int): String =
    if (start == end) start.toString() else "$start-$end"

private fun weekText(weeks: List<Int>): String {
    if (weeks.isEmpty()) return "未提供"
    val sorted = weeks.sorted()
    val sb = StringBuilder()
    var i = 0
    while (i < sorted.size) {
        var j = i
        while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
        if (sb.isNotEmpty()) sb.append('、')
        if (j - i >= 2) sb.append("${sorted[i]}-${sorted[j]}周") else {
            for (k in i..j) {
                if (k > i) sb.append('、')
                sb.append("${sorted[k]}周")
            }
        }
        i = j + 1
    }
    return sb.toString()
}
