package com.huhst.jiaowu.ui.screens.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huhst.jiaowu.ui.nav.Routes
import com.huhst.jiaowu.ui.vm.AppViewModel

/**
 * 我的：个人信息 + 设置入口 + 退出登录。
 */
@Composable
fun ProfileScreen(vm: AppViewModel, navController: NavController) {
    val data by vm.data.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        )

        // 个人信息
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = data.profile.name.ifBlank { "未获取到姓名" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = data.profile.studentId.ifBlank { "同步后可显示学号" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!data.profile.isEmpty) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    ProfileRow("学院", data.profile.college)
                    ProfileRow("专业", data.profile.major)
                    ProfileRow("班级", data.profile.className)
                    ProfileRow("年级", data.profile.grade)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 设置：外观与课表偏好
        Button(
            onClick = { navController.navigate(Routes.SETTINGS) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { navController.navigate(Routes.ABOUT) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("关于", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        FilledTonalButton(
            onClick = { confirmLogout = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("退出登录", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(24.dp))
    }

    if (confirmLogout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？") },
            text = {
                Text(
                    "将清除本机保存的账号、密码、Cookie 与已缓存的课表数据。" +
                        "下次需要重新登录。",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        confirmLogout = false
                        vm.logout()
                    },
                ) {
                    Text(
                        text = "退出登录",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLogout = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
