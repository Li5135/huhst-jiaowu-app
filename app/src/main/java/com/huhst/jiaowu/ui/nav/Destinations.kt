package com.huhst.jiaowu.ui.nav

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.huhst.jiaowu.ui.icons.AppIcons

/**
 * 底部导航的三个目的地。
 *
 * 图标照规格采用：其他功能 = logout、课程表 = search、我的 = school。
 * 三项在所有屏幕上保持一致（导航栏图标不应随页面变化）。
 */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    More("more", "其他功能", Icons.Rounded.ExitToApp),
    Schedule("schedule", "课程表", Icons.Rounded.Search),
    Profile("profile", "我的", AppIcons.School),
}

object Routes {
    const val LOGIN = "login"

    /** 「关于」页（网络通道 / 教务地址 / 缓存 / 版本） */
    const val ABOUT = "about"

    /** 「设置」页（外观与课表偏好） */
    const val SETTINGS = "settings"

    /** 顶部导航的三个目的地 */
    const val MORE = "more"
    const val SCHEDULE = "schedule"
    const val PROFILE = "profile"

    /** 「其他功能」里的二级页面 */
    const val GRADES = "grades"
    const val PROGRESS = "progress"

    const val PROGRESS_DETAIL = "progress_detail/{courseId}"
    const val COURSE_DETAIL = "course_detail/{sessionId}"

    fun progressDetail(courseId: String) = "progress_detail/${Uri.encode(courseId)}"
    fun courseDetail(sessionId: String) = "course_detail/${Uri.encode(sessionId)}"

    val topLevel: Set<String> = TopDestination.entries.map { it.route }.toSet()
}
