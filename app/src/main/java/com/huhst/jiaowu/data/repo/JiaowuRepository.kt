package com.huhst.jiaowu.data.repo

import android.util.Log
import com.huhst.jiaowu.data.local.JiaowuStore
import com.huhst.jiaowu.data.model.AppData
import com.huhst.jiaowu.data.model.AppSettings
import com.huhst.jiaowu.data.model.TeachingProgress
import com.huhst.jiaowu.data.model.WeekCalendar
import com.huhst.jiaowu.data.net.FetchResult
import com.huhst.jiaowu.data.net.LoginKind
import com.huhst.jiaowu.data.net.QzClient
import com.huhst.jiaowu.data.net.QzParser
import com.huhst.jiaowu.data.net.QzPaths
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 一次同步的结果。 */
sealed interface SyncOutcome {
    data class Success(
        val terms: Int,
        val sessions: Int,
        val progresses: Int,
        val grades: Int,
        val profileFilled: Boolean,
    ) : SyncOutcome

    /** 会话失效；[kind] 说明是哪一层失效。 */
    data class SessionExpired(val kind: LoginKind) : SyncOutcome

    data class Failure(val reason: String) : SyncOutcome
}

/**
 * 教务数据仓库。
 *
 * 职责：把 [QzClient] 取回的 HTML 交给 [QzParser] 解析，写进本机持久化，
 * 界面只观察 [data] 这个 Flow。失败时保留上一次的数据，不破坏本地缓存。
 */
class JiaowuRepository(
    private val store: JiaowuStore,
    private val client: QzClient,
    private val parser: QzParser,
) {

    val data: Flow<AppData> = store.data

    suspend fun currentData(): AppData = store.currentData()

    suspend fun setCurrentTerm(termId: String) {
        store.updateData { it.copy(currentTermId = termId) }
    }

    suspend fun clearCache() = store.clearData()

    /**
     * 全量同步。
     *
     * 会话失效会立即中止并上报是哪一层；单个页面解析不出内容
     * （例如「教学进度」路径尚未确认）不算失败，只是该部分保持为空。
     */
    suspend fun sync(settings: AppSettings): SyncOutcome {
        val existing = store.currentData()

        // 1. 课表页 —— 它同时带学期下拉框（xsMain.htmlx 只是 frameset，没有学期列表）
        val first = when (val r = client.fetch(settings, QzPaths.SCHEDULE)) {
            is FetchResult.Ok -> r
            is FetchResult.SessionExpired -> return SyncOutcome.SessionExpired(r.kind)
            is FetchResult.Failure -> return SyncOutcome.Failure(r.reason)
        }
        val terms = parser.parseTerms(first.body)
        val termId = existing.currentTermId.takeIf { id -> terms.any { it.id == id } }
            ?: terms.firstOrNull()?.id
            ?: existing.currentTermId
        Log.d(TAG, "学期解析: terms=${terms.size} current=$termId")

        // 2. 课表。选中的若不是默认学期，带上学期参数重取一次
        val defaultTermId = terms.firstOrNull()?.id.orEmpty()
        val scheduleBody = if (termId.isNotBlank() && termId != defaultTermId) {
            when (val r = client.fetch(settings, QzPaths.SCHEDULE, mapOf("xnxq01id" to termId))) {
                is FetchResult.Ok -> r.body
                is FetchResult.SessionExpired -> return SyncOutcome.SessionExpired(r.kind)
                is FetchResult.Failure -> first.body
            }
        } else {
            first.body
        }
        val sessions = parser.parseSchedule(scheduleBody)
        Log.d(TAG, "课表解析: ${sessions.size} 条")

        // 3. 个人信息
        val profile = when (val r = client.fetch(settings, QzPaths.PROFILE)) {
            is FetchResult.Ok -> parser.parseProfile(r.body)
            is FetchResult.SessionExpired -> return SyncOutcome.SessionExpired(r.kind)
            is FetchResult.Failure -> existing.profile
        }

        // 4. 教学进度（路径待确认，候选逐个试）
        var progresses: List<TeachingProgress> = emptyList()
        for (path in QzPaths.PROGRESS_CANDIDATES) {
            when (val r = client.fetch(settings, path)) {
                is FetchResult.Ok -> {
                    val parsed = parser.parseProgress(r.body)
                    Log.d(TAG, "教学进度候选 $path -> ${parsed.size} 条")
                    if (parsed.isNotEmpty()) {
                        progresses = parsed
                        break
                    }
                }

                is FetchResult.SessionExpired -> return SyncOutcome.SessionExpired(r.kind)
                is FetchResult.Failure -> Log.d(TAG, "教学进度候选 $path 失败: ${r.reason}")
            }
        }

        // 5. 成绩（路径待确认，候选逐个试）
        var grades = existing.grades
        val termName = terms.firstOrNull { it.id == termId }?.name.orEmpty()
        for (path in QzPaths.GRADE_CANDIDATES) {
            when (val r = client.fetch(settings, path)) {
                is FetchResult.Ok -> {
                    val parsed = parser.parseGrades(r.body, termId, termName)
                    Log.d(TAG, "成绩候选 $path -> ${parsed.size} 条")
                    if (parsed.isNotEmpty()) {
                        grades = parsed
                        break
                    }
                }

                is FetchResult.SessionExpired -> return SyncOutcome.SessionExpired(r.kind)
                is FetchResult.Failure -> Log.d(TAG, "成绩候选 $path 失败: ${r.reason}")
            }
        }

        // 2.5 教学周历（当前周）。数据藏在「我的桌面」主框架的周次下拉里，
        //     拿到第 1 周的日期后就能自己算出当前周，不必每次靠同步。
        var weekCalendar = existing.weekCalendar
        when (val r = client.fetch(settings, QzPaths.MAIN_FRAME)) {
            is FetchResult.Ok -> parser.parseWeekCalendar(r.body)?.let {
                weekCalendar = it
                Log.d(TAG, "周历解析: 第1周周一=${it.termStart} 当前周=${it.currentWeek}")
            }

            is FetchResult.SessionExpired -> return SyncOutcome.SessionExpired(r.kind)
            is FetchResult.Failure -> Log.d(TAG, "周历取不到: ${r.reason}")
        }

        store.updateData {
            it.copy(
                terms = terms.ifEmpty { it.terms },
                currentTermId = termId,
                weekCalendar = weekCalendar,
                sessions = sessions,
                progresses = progresses,
                grades = grades,
                profile = if (profile.isEmpty) it.profile else profile,
                lastSyncAt = System.currentTimeMillis(),
            )
        }

        return SyncOutcome.Success(
            terms = terms.size,
            sessions = sessions.size,
            progresses = progresses.size,
            grades = grades.size,
            profileFilled = !profile.isEmpty,
        )
    }

    private companion object {
        const val TAG = "JiaowuSync"
    }
}

/**
 * 由教学周历推算「今天是第几周」。
 *
 * 有了第 1 周的周一日期，就能随日期自行推进——不需要重新同步，
 * 即使 App 隔几周才打开一次也能算对。
 *
 * [startsSunday] 为真时按「周日开头」的一周计算，
 * 也就是把起点前移到第 1 周周一的前一天。
 * 返回 0 表示无法判断（还没同步过周历）。
 */
fun currentWeekOf(calendar: WeekCalendar, startsSunday: Boolean = false): Int {
    if (calendar.termStart.isBlank()) return 0
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val monday = runCatching { fmt.parse(calendar.termStart)?.time }.getOrNull() ?: return 0
    val origin = if (startsSunday) monday - 86_400_000L else monday
    val days = (System.currentTimeMillis() - origin) / 86_400_000L
    if (days < 0) return 1
    return (days / 7).toInt() + 1
}

/**
 * 由教学周历推算某一周 7 天的日期（按 [startsSunday] 决定从周日还是周一起）。
 * 学期开始日期未知时返回空列表。
 */
fun weekDayDates(
    calendar: WeekCalendar,
    week: Int,
    startsSunday: Boolean = false,
): List<Date> {
    if (calendar.termStart.isBlank() || week < 1) return emptyList()
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val start = runCatching { fmt.parse(calendar.termStart) }.getOrNull() ?: return emptyList()
    val first = Calendar.getInstance().apply {
        time = start
        add(Calendar.DAY_OF_YEAR, (week - 1) * 7)
        if (startsSunday) add(Calendar.DAY_OF_YEAR, -1)
    }
    return (0..6).map { offset ->
        Calendar.getInstance().apply {
            time = first.time
            add(Calendar.DAY_OF_YEAR, offset)
        }.time
    }
}

/** 两个日期是否落在同一天。 */
fun isSameDay(a: Date, b: Date): Boolean {
    val ca = Calendar.getInstance().apply { time = a }
    val cb = Calendar.getInstance().apply { time = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
