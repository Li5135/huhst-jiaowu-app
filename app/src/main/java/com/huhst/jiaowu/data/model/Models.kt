package com.huhst.jiaowu.data.model

import kotlinx.serialization.Serializable

/** 学期。id 对应强智的 xnxq01id，如 "2025-2026-1"。 */
@Serializable
data class Term(
    val id: String,
    val name: String,
)

/** 一节课（课表中的一个格子）。 */
@Serializable
data class CourseSession(
    val id: String,
    val name: String,
    val teacher: String = "",
    val classroom: String = "",
    /** 1 = 周一 … 7 = 周日 */
    val weekday: Int = 1,
    /** 起始节次，1 起 */
    val startPeriod: Int = 1,
    /** 结束节次，含 */
    val endPeriod: Int = 1,
    /** 出现的周次，如 [1,2,3,5,7] */
    val weeks: List<Int> = emptyList(),
    val credit: String = "",
    val courseType: String = "",
)

/** 教学进度中的一章。 */
@Serializable
data class Chapter(
    val index: Int,
    val title: String,
    val done: Boolean = false,
    val hours: String = "",
)

/** 一门课的教学进度。 */
@Serializable
data class TeachingProgress(
    val courseId: String,
    val courseName: String,
    val teacher: String = "",
    val className: String = "",
    val totalChapters: Int = 0,
    val finishedChapters: Int = 0,
    val chapters: List<Chapter> = emptyList(),
) {
    val ratio: Float
        get() = if (totalChapters <= 0) 0f else finishedChapters.toFloat() / totalChapters
}

/** 学生个人信息。 */
@Serializable
data class UserProfile(
    val name: String = "",
    val studentId: String = "",
    val college: String = "",
    val major: String = "",
    val className: String = "",
    val grade: String = "",
) {
    val isEmpty: Boolean
        get() = name.isBlank() && studentId.isBlank()
}

/** 网络通道。 */
@Serializable
enum class NetChannel {
    /** 自动探测：先试校内直连，失败再走 WebVPN */
    AUTO,
    /** 强制校园网直连 */
    CAMPUS,
    /** 强制经 WebVPN */
    VPN,
}

/** 应用设置。 */
@Serializable
data class AppSettings(
    val channel: NetChannel = NetChannel.AUTO,
    /** 校内教务系统根地址 */
    val campusBase: String = "http://10.1.1.149",
    /** WebVPN 门户根地址 */
    val vpnBase: String = "http://vpn.huhst.edu.cn:9900",
    /** 教务系统在 WebVPN 中的宿主码（登录后自动发现） */
    val hostCode: String = "",
    /** 课表背景图（本机私有目录内的文件路径，空串表示用默认背景） */
    val scheduleBackground: String = "",
    /** 课表第一列是不是周日。true = 周日开头（默认），false = 周一开头。 */
    val weekStartsSunday: Boolean = true,
)

/** 落盘的全部业务数据。 */
/** 一条成绩记录。 */
@Serializable
data class Grade(
    val termId: String = "",
    val termName: String = "",
    val courseName: String = "",
    val credit: String = "",
    val score: String = "",
    val point: String = "",
    val courseType: String = "",
) {
    /** 数字成绩，用于算平均分与学分绩点；等级制成绩（优/良/合格）返回 null。 */
    val scoreValue: Double? get() = score.trim().toDoubleOrNull()

    val creditValue: Double? get() = credit.trim().toDoubleOrNull()
}

/**
 * 教学周历：把「第几周」和真实日期对应起来。
 *
 * 来源是教务系统「我的桌面」页里的周次下拉框（`select#week`）：
 * 它把每一周映射到该周周一的日期，并用 `selected` 标出**当前周**。
 * 拿到 [termStart] 后，App 就能自己算出任意一天的当前周，不必每次同步。
 */
@Serializable
data class WeekCalendar(
    val termId: String = "",
    /** 第 1 周的周一，格式 yyyy-MM-dd */
    val termStart: String = "",
    /** 同步时教务系统认为的当前周；0 表示页面没标 */
    val currentWeek: Int = 0,
)

@Serializable
data class AppData(
    val terms: List<Term> = emptyList(),
    val currentTermId: String = "",
    val weekCalendar: WeekCalendar = WeekCalendar(),
    val sessions: List<CourseSession> = emptyList(),
    val progresses: List<TeachingProgress> = emptyList(),
    val grades: List<Grade> = emptyList(),
    val profile: UserProfile = UserProfile(),
    val lastSyncAt: Long = 0L,
)

/**
 * 两套账号密码。
 *
 * 学校的结构决定必须存两套：统一身份认证（正方 CAS）与教务系统（强智 jsxsd）
 * 是两台内网主机上的两个独立系统，密码互不相同。
 * 用户名实测为同一个学号，但仍分开存，留出各自可改的余地。
 *
 * 整块用 Android Keystore 加密后才落盘。
 */
@Serializable
data class SavedCredentials(
    val ssoUser: String = "",
    val ssoPassword: String = "",
    val jwUser: String = "",
    val jwPassword: String = "",
    /** 用户是否勾选了记住密码；不记住时只保留学号，便于下次少敲字。 */
    val remember: Boolean = true,
) {
    val hasSso: Boolean get() = ssoUser.isNotBlank()
    val hasJw: Boolean get() = jwUser.isNotBlank()

    /** 只保留学号、抹掉密码，用于关闭「记住密码」后落盘。 */
    fun withoutPasswords(): SavedCredentials = copy(
        ssoPassword = "",
        jwPassword = "",
        remember = false,
    )
}
