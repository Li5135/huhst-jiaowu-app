package com.huhst.jiaowu.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huhst.jiaowu.ui.nav.Routes
import com.huhst.jiaowu.ui.vm.AppViewModel
import com.huhst.jiaowu.ui.vm.SyncUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 其他功能 —— 放「课程表」和「我的」之外的功能入口。
 *
 * 课程表、个人信息已经在顶部导航里，这里收拢其余功能：
 * 成绩查询（主入口）、教学进度。
 */
@Composable
fun MoreScreen(vm: AppViewModel, navController: NavController) {
    val data by vm.data.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    var showHint by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // 上部靠左：标签片（M 档 56dp，选中态用 secondaryContainer + 勾选图标）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = true,
                onClick = { showHint = !showHint },
                label = {
                    Text(
                        text = "更多功能",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                // 规格：选中态前面显示勾选图标
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.height(56.dp),
            )
            Spacer(Modifier.weight(1f))
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

        if (showHint) {
            Text(
                text = "课程表与个人信息已在底部导航；成绩查询与教学进度放在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        // 中部：功能入口
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = { navController.navigate(Routes.GRADES) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
            ) {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "成绩查询",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (data.grades.isEmpty()) {
                            "尚未同步，点右上角刷新"
                        } else {
                            "已收录 ${data.grades.size} 条成绩"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { navController.navigate(Routes.PROGRESS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                Icon(Icons.Rounded.List, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("教学进度", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (data.lastSyncAt > 0) {
                    "上次同步：" + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                        .format(Date(data.lastSyncAt)) +
                        " · 课表 ${data.sessions.size} 条"
                } else {
                    "尚未同步过数据"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
