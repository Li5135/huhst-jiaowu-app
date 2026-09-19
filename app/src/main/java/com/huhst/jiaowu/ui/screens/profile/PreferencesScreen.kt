package com.huhst.jiaowu.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.huhst.jiaowu.ui.components.rememberBackgroundBitmap
import com.huhst.jiaowu.ui.icons.AppIcons
import com.huhst.jiaowu.ui.vm.AppViewModel

/**
 * 设置：外观与课表偏好。
 *
 * 两项都真实生效并持久化：
 *  - 自定义背景：从系统相册选图，拷进应用私有目录后作为整个应用的背景
 *  - 周日起始：切换课表第一列是周日还是周一
 */
@Composable
fun PreferencesScreen(vm: AppViewModel, navController: NavController) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showTip by rememberSaveable { mutableStateOf(false) }

    // 系统相册选择器（Android 13+ 是照片选择器，低版本回落到文档选择器）
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) vm.importBackground(uri)
    }

    val hasBackground = settings.scheduleBackground.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        // 顶部：返回 + 「设置」标签片（M 档 56dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            FilterChip(
                selected = true,
                onClick = { showTip = !showTip },
                label = {
                    Text("设置", style = MaterialTheme.typography.titleMedium)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.height(56.dp),
            )
        }

        if (showTip) {
            Text(
                text = "背景会应用到整个应用；周日起始只影响课表第一列的顺序。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        // 自定义背景
        Button(
            onClick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = AppIcons.Image,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("自定义背景", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (hasBackground) "已设置，点此可换一张" else "从手机图库选一张图片作为背景",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (hasBackground) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val preview = rememberBackgroundBitmap(settings.scheduleBackground)
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = "当前背景",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.clearBackground() }) {
                    Text("移除背景", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 周日起始
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("周日起始", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (settings.weekStartsSunday) {
                        "课表第一列是周日"
                    } else {
                        "课表第一列是周一"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.weekStartsSunday,
                onCheckedChange = { vm.setWeekStartsSunday(it) },
            )
        }
    }
}
