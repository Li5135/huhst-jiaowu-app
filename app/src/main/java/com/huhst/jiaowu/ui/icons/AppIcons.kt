package com.huhst.jiaowu.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自定义矢量图标。
 *
 * 原因：`material-icons-core` 只带少量图标，而 `School` / `Visibility` /
 * `VisibilityOff` 都在 `material-icons-extended` 里 —— 为三个图标引入几十 MB 的
 * 图标库不划算。这里直接内联 Material Symbols 的路径数据，零额外依赖。
 *
 * 实际着色由 `Icon(tint = ...)` 覆盖，所以填充色写黑即可。
 */
object AppIcons {

    /** 学士帽，用于登录页标题。 */
    val School: ImageVector by lazy {
        build(
            "School",
            "M5,13.18v4L12,21l7,-3.82v-4L12,17l-7,-3.82zM12,3L1,9l11,6 9,-4.91V17h2V9L12,3z",
        )
    }

    /** 睁眼，用于「显示密码」。 */
    val Visibility: ImageVector by lazy {
        build(
            "Visibility",
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5" +
                "c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5" +
                " -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }

    /** 划掉的眼睛，用于「隐藏密码」。 */
    val VisibilityOff: ImageVector by lazy {
        build(
            "VisibilityOff",
            "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92" +
                "c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5" +
                " -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
                "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5" +
                " 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
                "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3" +
                " 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53" +
                " -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2z" +
                "M11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
        )
    }

    /** 图片，用于「自定义背景」。 */
    val Image: ImageVector by lazy {
        build(
            "Image",
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14" +
                "c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
        )
    }

    private fun build(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
}
