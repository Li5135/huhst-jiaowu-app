package com.huhst.jiaowu.ui.screens.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huhst.jiaowu.data.model.Grade
import com.huhst.jiaowu.ui.components.EmptyState
import com.huhst.jiaowu.ui.vm.AppViewModel
import com.huhst.jiaowu.ui.vm.SyncUi
import java.util.Locale

/**
 * 成绩查询。
 *
 * 数据来自教务系统的成绩页；解析不出来时显示空状态而不是假数据。
 * 统计（平均分 / 学分绩点 / 已修学分）在本机按已解析的成绩算出。
 */
@Composable
fun GradesScreen(vm: AppViewModel, navController: NavController) {
    val data by vm.data.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    var termFilter by rememberSaveable { mutableStateOf("") }

    val termPairs = data.grades
        .map { it.termId to it.termName.ifBlank { it.termId } }
        .distinct()
        .filter { it.first.isNotBlank() }

    val shown = if (termFilter.isBlank()) {
        data.grades
    } else {
        data.grades.filter { it.termId == termFilter }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
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
                text = "成绩查询",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
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

        if (data.grades.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Star,
                title = "暂无成绩数据",
                description = "点右上角刷新从教务系统同步。若已同步仍为空，" +
                    "可能是成绩尚未发布，或成绩页路径需要适配。",
                actionLabel = "立即同步",
                onAction = { vm.syncNow() },
            )
            return@Column
        }

        GradeSummary(shown)

        if (termPairs.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = termFilter.isBlank(),
                        onClick = { termFilter = "" },
                        label = { Text("全部", style = MaterialTheme.typography.labelLarge) },
                    )
                }
                items(termPairs, key = { it.first }) { (id, name) ->
                    FilterChip(
                        selected = termFilter == id,
                        onClick = { termFilter = id },
                        label = { Text(name, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { "${it.termId}|${it.courseName}|${it.score}" }) { grade ->
                GradeCard(grade)
            }
        }
    }
}

@Composable
private fun GradeSummary(grades: List<Grade>) {
    val scored = grades.mapNotNull { it.scoreValue }
    val average = if (scored.isEmpty()) null else scored.average()

    val withPoint = grades.filter { it.creditValue != null && it.point.trim().toDoubleOrNull() != null }
    val totalCredit = withPoint.sumOf { it.creditValue ?: 0.0 }
    val gpa = if (totalCredit > 0) {
        withPoint.sumOf { (it.creditValue ?: 0.0) * (it.point.trim().toDoubleOrNull() ?: 0.0) } / totalCredit
    } else {
        null
    }
    val earnedCredit = grades.mapNotNull { it.creditValue }.sum()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryCell("平均分", average?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "—", Modifier.weight(1f))
            SummaryCell("学分绩点", gpa?.let { String.format(Locale.CHINA, "%.2f", it) } ?: "—", Modifier.weight(1f))
            SummaryCell("已修学分", trimNumber(earnedCredit), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GradeCard(grade: Grade) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = grade.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        grade.termName.takeIf { it.isNotBlank() },
                        grade.credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
                        grade.courseType.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = grade.score.ifBlank { "—" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if ((grade.scoreValue ?: 100.0) < 60) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (grade.point.isNotBlank()) {
                    Text(
                        text = "绩点 ${grade.point}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (grade.courseType.isNotBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.CHINA, "%.1f", value)
