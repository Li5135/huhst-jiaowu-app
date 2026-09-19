package com.huhst.jiaowu.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huhst.jiaowu.R
import com.huhst.jiaowu.data.model.NetChannel
import com.huhst.jiaowu.ui.components.SectionHeader
import com.huhst.jiaowu.ui.vm.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 关于：网络通道、教务系统地址、本地缓存、版本信息。 */
@Composable
fun AboutScreen(vm: AppViewModel, navController: NavController) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var confirmClear by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

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
                text = "关于",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("网络通道")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .selectableGroup(),
            ) {
                ChannelOption(
                    title = "自动（推荐）",
                    description = "校园网内直连，校外自动经 WebVPN",
                    selected = settings.channel == NetChannel.AUTO,
                    onSelect = { vm.setChannel(NetChannel.AUTO) },
                )
                ChannelOption(
                    title = "仅校园网直连",
                    description = "只在连接校园网时使用，速度最快",
                    selected = settings.channel == NetChannel.CAMPUS,
                    onSelect = { vm.setChannel(NetChannel.CAMPUS) },
                )
                ChannelOption(
                    title = "仅经 WebVPN",
                    description = "任何网络都走校外门户",
                    selected = settings.channel == NetChannel.VPN,
                    onSelect = { vm.setChannel(NetChannel.VPN) },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionHeader("教务系统地址")
            InfoCard {
                InfoRow("校内直连", settings.campusBase)
                InfoRow("WebVPN 门户", settings.vpnBase)
                InfoRow(
                    "宿主码",
                    settings.hostCode.ifBlank { "登录后自动获取" },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionHeader("本地缓存")
            InfoCard {
                InfoRow(
                    "上次同步",
                    if (data.lastSyncAt > 0) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                            .format(Date(data.lastSyncAt))
                    } else {
                        "从未同步"
                    },
                )
                InfoRow("课表条目", "${data.sessions.size} 条")
                InfoRow("教学进度", "${data.progresses.size} 门")
                InfoRow("学期", "${data.terms.size} 个")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(
                            text = "清除缓存",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionHeader("关于")
            InfoCard {
                InfoRow("应用", stringResource(R.string.app_name))
                InfoRow("版本", versionName)
                InfoRow("学校", "湖南人文科技学院")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "本应用仅读取本人账号的教务数据，不绕过任何认证环节，" +
                        "账号密码经 Android Keystore 加密后保存在本机，不会上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除本地缓存？") },
            text = { Text("将删除已同步的课表与教学进度，账号密码与登录状态会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        vm.clearCache()
                    },
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ChannelOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(88.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
