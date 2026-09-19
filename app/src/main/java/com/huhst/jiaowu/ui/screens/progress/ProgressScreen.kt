package com.huhst.jiaowu.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huhst.jiaowu.data.model.TeachingProgress
import com.huhst.jiaowu.ui.components.EmptyState
import com.huhst.jiaowu.ui.components.LoadingPane
import com.huhst.jiaowu.ui.nav.Routes
import com.huhst.jiaowu.ui.vm.AppViewModel
import com.huhst.jiaowu.ui.vm.SyncUi

/**
 * 教学进度（学生视角）：各门课老师讲到哪、章节完成情况。
 */
@Composable
fun ProgressScreen(vm: AppViewModel, navController: NavController) {
    val data by vm.data.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()

    val currentTerm = data.terms.firstOrNull { it.id == data.currentTermId }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "教学进度",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentTerm?.name ?: "尚未获取学期信息",
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

        if (data.terms.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data.terms, key = { it.id }) { term ->
                    FilterChip(
                        selected = term.id == data.currentTermId,
                        onClick = { vm.setCurrentTerm(term.id) },
                        label = { Text(term.name, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        }

        when {
            sync is SyncUi.Running && data.progresses.isEmpty() -> {
                LoadingPane(label = "正在从教务系统读取…")
            }

            data.progresses.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.List,
                    title = "暂无教学进度",
                    description = "点右下角刷新按钮从教务系统同步。若已同步仍为空，" +
                        "可能是教务系统尚未发布进度，或该模块路径需要适配。",
                    actionLabel = "立即同步",
                    onAction = { vm.syncNow() },
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(data.progresses, key = { it.courseId }) { progress ->
                        ProgressCard(progress) {
                            navController.navigate(Routes.progressDetail(progress.courseId))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(progress: TeachingProgress, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = progress.courseName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress.teacher.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progress.teacher,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))

            if (progress.totalChapters > 0) {
                LinearProgressIndicator(
                    progress = { progress.ratio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "已完成 ${progress.finishedChapters} / ${progress.totalChapters} 章",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "进度数据待教务系统提供",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
