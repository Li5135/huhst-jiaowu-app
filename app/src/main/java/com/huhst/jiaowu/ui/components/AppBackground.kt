package com.huhst.jiaowu.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把本机图片文件解码成 [ImageBitmap]。
 *
 * 先读一次尺寸算出降采样倍率再解码 —— 相册原图动辄四五千像素，
 * 整张读进内存很容易 OOM。上限按手机竖屏留足余量。
 */
@Composable
fun rememberBackgroundBitmap(path: String): ImageBitmap? =
    produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)

                    var sample = 1
                    while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2560) {
                        sample *= 2
                    }

                    BitmapFactory.decodeFile(
                        path,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )?.asImageBitmap()
                }.getOrNull()
            }
        }
    }.value

/**
 * 应用背景。
 *
 * 没设置自定义背景时就是普通的 surface 底色；
 * 设置后铺满图片，并在上面叠一层 surface 蒙版 ——
 * 否则照片上的正文会看不清。蒙版浓度是"看得见图、也读得清字"的折中。
 */
@Composable
fun AppBackground(path: String) {
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface),
    ) {
        val bitmap = rememberBackgroundBitmap(path)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(surface.copy(alpha = 0.82f)),
            )
        }
    }
}
