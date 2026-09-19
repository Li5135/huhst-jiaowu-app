package com.huhst.jiaowu.data.net

import com.huhst.jiaowu.data.model.Chapter
import com.huhst.jiaowu.data.model.CourseSession
import com.huhst.jiaowu.data.model.Grade
import com.huhst.jiaowu.data.model.TeachingProgress
import com.huhst.jiaowu.data.model.Term
import com.huhst.jiaowu.data.model.UserProfile
import com.huhst.jiaowu.data.model.WeekCalendar
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 强智教务系统 HTML 解析器。
 *
 * 选择器**已按本校真实页面校准**（样例取自实机抓取，见 `_recon/jwx/`）：
 *
 * 课表 `/jsxsd/xskb/xskb_list.do`：
 *  - 主表 `table#timetable`；表头是「星期一…星期日」共 7 列
 *  - 数据行是**大节**（第一大节…第五大节，行内标注 `(01,02小节)`）
 *  - 单元格内每个 `div.kbcontent` 是一节课，字段靠 `font[title]` 区分：
 *    `周次(节次)` / `教学楼` / `教室` / `教师` / `班级` / `备注`；
 *    没有 title 的第一个 font 是课程名
 *  - 周次文本形如 `1-16(周)`、`2,4,6,8(周)`、`2-11(周)[01-02节]`
 *
 * 所有解析函数都设计为「匹配不到就返回空」而不是抛异常 ——
 * 页面改版时 App 会退化为空状态，而不会崩溃。
 */
class QzParser {

    private val weekRange = Regex("""(\d{1,2})\s*[-~—]\s*(\d{1,2})""")
    private val weekSingle = Regex("""\d{1,2}""")
    private val weekdayToken = Regex("""(?:星期|周)\s*([一二三四五六日天]|[1-7])""")
    private val termId = Regex("""\d{4}-\d{4}-\d""")

    /**
     * 周次文本里的节次后缀，如 `[01-02节]`。
     *
     * ⚠️ 必须先剥离：里面的 `01`、`02` 会被按周次误读成第 1、2 周。
     */
    private val periodSuffix = Regex("""\[\s*(\d{1,2})\s*-\s*(\d{1,2})\s*节\s*]""")

    // ---------------- 学期 ----------------

    fun parseTerms(html: String): List<Term> {
        val doc = safeDoc(html)
        val terms = linkedMapOf<String, String>()
        // 学期下拉框 name="xnxq01id"
        doc.select("select[name=xnxq01id] option").forEach { option ->
            val value = option.attr("value").trim()
            val label = option.text().trim()
            if (termId.matches(value) && label.isNotBlank()) terms[value] = label
        }
        if (terms.isNotEmpty()) return terms.map { Term(it.key, it.value) }

        // 兜底：学期列表也可能出现在脚本变量里，形如 2025-2026-1 足够特征化
        termId.findAll(html).forEach { m -> terms.putIfAbsent(m.value, m.value) }
        return terms.map { Term(it.key, it.value) }
    }

    // ---------------- 课表 ----------------

    fun parseSchedule(html: String): List<CourseSession> {
        val doc = safeDoc(html)
        val table = doc.selectFirst("table#timetable") ?: return emptyList()

        val rows = table.select("tr")
        if (rows.size < 2) return emptyList()

        // 表头：第一个 th 是空的，其后依次是 星期一…星期日
        val weekdayOfHeaderIndex = HashMap<Int, Int>()
        rows.firstOrNull()?.select("th, td")?.forEachIndexed { idx, cell ->
            parseWeekday(cell.text())?.let { weekdayOfHeaderIndex[idx] = it }
        }
        if (weekdayOfHeaderIndex.isEmpty()) return emptyList()

        val result = mutableListOf<CourseSession>()
        rows.drop(1).forEachIndexed { rowIndex, row ->
            val rowLabel = row.selectFirst("th")?.text().orEmpty()
            // 行 → 节次。优先读行内标注 `(01,02小节)`，读不到按行序推算
            val rowPeriods = parseBigPeriod(rowLabel) ?: (rowIndex * 2 + 1 to rowIndex * 2 + 2)

            row.select("td").forEachIndexed { tdIndex, cell ->
                // 数据行首列是 th，所以第 i 个 td 对应表头第 i+1 列
                val weekday = weekdayOfHeaderIndex[tdIndex + 1] ?: return@forEachIndexed
                cell.select("div.kbcontent, div.kbcontent1").forEach { block ->
                    parseBlock(block, weekday, rowPeriods)?.let { result += it }
                }
            }
        }

        return result.distinctBy { "${it.name}|${it.weekday}|${it.startPeriod}|${it.classroom}" }
    }

    private fun parseBlock(
        block: Element,
        weekday: Int,
        rowPeriods: Pair<Int, Int>,
    ): CourseSession? {
        var name = ""
        var weeksText = ""
        var teacher = ""
        var classroom = ""
        var building = ""

        block.select("font").forEach { font ->
            if (font.attr("name") == "wkxx") return@forEach // 隐藏字段
            val title = font.attr("title").trim()
            val text = font.text().trim()
            if (text.isEmpty()) return@forEach
            when {
                title.startsWith("周次") -> weeksText = text
                title == "教师" -> teacher = text
                title == "教室" -> classroom = text
                title == "教学楼" -> building = text
                title.isEmpty() && name.isEmpty() -> name = text
            }
        }

        if (name.isBlank()) return null

        // 周次里可能带节次后缀，如 `2-11(周)[01-02节]`
        var periods = rowPeriods
        var weeksOnly = weeksText
        periodSuffix.find(weeksText)?.let { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && a <= b) periods = a to b
            weeksOnly = weeksText.replace(m.value, " ")
        }

        val room = listOf(building, classroom).filter { it.isNotBlank() }.joinToString(" ")

        return CourseSession(
            id = "$name|$weekday|${periods.first}|$room|$weeksText",
            name = name,
            teacher = teacher,
            classroom = room,
            weekday = weekday,
            startPeriod = periods.first,
            endPeriod = periods.second,
            weeks = parseWeeks(weeksOnly),
        )
    }

    /** 从「第一大节 (01,02小节)」里取出 (1, 2)。 */
    private fun parseBigPeriod(label: String): Pair<Int, Int>? {
        val nums = Regex("""\d{1,2}""").findAll(label)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..20 }
            .toList()
        if (nums.size < 2) return null
        return nums.min() to nums.max()
    }

    /** 解析「1-16周」「2,4,6,8周」「1-8周(单)」等文案。 */
    fun parseWeeks(text: String): List<Int> {
        val result = sortedSetOf<Int>()
        weekRange.findAll(text).forEach { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@forEach
            val b = m.groupValues[2].toIntOrNull() ?: return@forEach
            if (a in 1..30 && b in a..30) for (w in a..b) result += w
        }
        weekSingle.findAll(weekRange.replace(text, " ")).forEach {
            it.value.toIntOrNull()?.let { w -> if (w in 1..30) result += w }
        }
        val odd = text.contains("单")
        val even = text.contains("双")
        return result.filter { w ->
            when {
                odd -> w % 2 == 1
                even -> w % 2 == 0
                else -> true
            }
        }
    }

    // ---------------- 成绩 ----------------

    /**
     * 成绩列表。
     *
     * 按**表头文字**定位列，因此对列顺序不敏感；找不到成绩表就返回空。
     * 已按实机成绩页校准（本校一学期 24 条，含「良好」这类等级制成绩）。
     */
    fun parseGrades(html: String, termId: String, termName: String): List<Grade> {
        val doc = safeDoc(html)
        val table = doc.select("table").firstOrNull { t ->
            val head = t.select("tr").firstOrNull()?.text().orEmpty()
            head.contains("课程") &&
                (head.contains("成绩") || head.contains("分数") || head.contains("绩点"))
        } ?: return emptyList()

        val rows = table.select("tr")
        val header = rows.firstOrNull()?.select("td, th")?.map { it.text().trim() }
            ?: return emptyList()

        fun columnOf(vararg keys: String): Int =
            header.indexOfFirst { cell -> keys.any { cell.contains(it) } }

        val cName = columnOf("课程名称", "课程名")
        if (cName < 0) return emptyList()
        val cCredit = columnOf("学分")
        val cScore = columnOf("成绩", "分数", "总评")
        val cPoint = columnOf("绩点")
        val cType = columnOf("课程性质", "课程属性", "课程类别")

        return rows.drop(1).mapNotNull { row ->
            val cells = row.select("td, th").map { it.text().trim() }
            val name = cells.getOrNull(cName).orEmpty()
            if (name.isBlank()) return@mapNotNull null
            Grade(
                termId = termId,
                termName = termName,
                courseName = name,
                credit = cells.getOrNull(cCredit).orEmpty(),
                score = cells.getOrNull(cScore).orEmpty(),
                point = cells.getOrNull(cPoint).orEmpty(),
                courseType = cells.getOrNull(cType).orEmpty(),
            )
        }
    }

    // ---------------- 教学周历（当前周） ----------------

    /**
     * 从「我的桌面」页解析教学周历。
     *
     * 页面里有个周次下拉：
     * ```html
     * <select name="week" id="week">
     *   <option value="2026-09-07">第一周</option>
     *   <option value="2026-09-14" selected>第二周</option>
     * </select>
     * ```
     * 第一个日期 = 第 1 周的周一；带 `selected` 的那项 = 当前周。
     */
    fun parseWeekCalendar(html: String): WeekCalendar? {
        val doc = safeDoc(html)
        val weekSelect = doc.selectFirst("select#week")
            ?: doc.selectFirst("select[name=week]")
            ?: return null

        val dateOnly = Regex("""\d{4}-\d{2}-\d{2}""")
        val options = weekSelect.select("option")
            .filter { dateOnly.matches(it.attr("value").trim()) }
        if (options.isEmpty()) return null

        val selectedIndex = options.indexOfFirst { it.hasAttr("selected") }
        return WeekCalendar(
            termId = doc.selectFirst("select[name=semester] option")?.text()?.trim().orEmpty(),
            termStart = options.first().attr("value").trim(),
            currentWeek = if (selectedIndex >= 0) selectedIndex + 1 else 0,
        )
    }

    // ---------------- 个人信息 ----------------

    fun parseProfile(html: String): UserProfile {
        val doc = safeDoc(html)
        return UserProfile(
            name = labelValue(doc, "姓名"),
            studentId = labelValue(doc, "学号"),
            college = labelValue(doc, "院系").ifBlank { labelValue(doc, "学院") },
            major = labelValue(doc, "专业"),
            className = labelValue(doc, "班级"),
            grade = labelValue(doc, "年级"),
        )
    }

    // ---------------- 教学进度 ----------------

    /**
     * 教学进度列表。找含「课程 + 进度 / 章节 / 学时」字样的表格，按行抽取；
     * 认不出来就返回空列表，页面显示空状态。
     */
    fun parseProgress(html: String): List<TeachingProgress> {
        val doc = safeDoc(html)
        val tables = doc.select("table").filter { t ->
            val txt = t.text()
            (txt.contains("课程") && (txt.contains("进度") || txt.contains("章节"))) ||
                (txt.contains("章节") && txt.contains("学时"))
        }
        val out = mutableListOf<TeachingProgress>()
        tables.forEach { table ->
            table.select("tr").drop(1).forEach { row ->
                val cells = row.select("td, th").map { it.text().trim() }
                if (cells.size < 2 || cells[0].isBlank()) return@forEach
                val name = cells[0]
                val ratio = cells.firstOrNull { it.contains("/") }
                val finished = ratio?.substringBefore("/")?.firstIntOrNull()
                    ?: cells.getOrNull(2)?.firstIntOrNull()
                    ?: 0
                val total = ratio?.substringAfter("/")?.firstIntOrNull()
                    ?: cells.getOrNull(3)?.firstIntOrNull()
                    ?: 0
                out += TeachingProgress(
                    courseId = name,
                    courseName = name,
                    teacher = cells.getOrNull(1).orEmpty(),
                    totalChapters = total,
                    finishedChapters = finished,
                )
            }
        }
        return out
    }

    /** 章节目录（教学进度详情）。 */
    fun parseChapters(html: String): List<Chapter> {
        val doc = safeDoc(html)
        val table = doc.select("table").firstOrNull { it.text().contains("章节") } ?: return emptyList()
        val out = mutableListOf<Chapter>()
        table.select("tr").drop(1).forEachIndexed { index, row ->
            val cells = row.select("td, th").map { it.text().trim() }
            if (cells.isEmpty() || cells.all { it.isBlank() }) return@forEachIndexed
            out += Chapter(
                index = index + 1,
                title = cells.getOrNull(1)?.ifBlank { cells[0] } ?: cells[0],
                hours = cells.lastOrNull { it.matches(Regex("""\d+(\.\d+)?""")) }.orEmpty(),
                done = row.text().contains("已") || row.select(".done, .finish").isNotEmpty(),
            )
        }
        return out
    }

    // ---------------- 内部工具 ----------------

    private fun parseWeekday(text: String): Int? {
        val token = weekdayToken.find(text)?.groupValues?.get(1) ?: return null
        return token.toIntOrNull() ?: when (token) {
            "一" -> 1
            "二" -> 2
            "三" -> 3
            "四" -> 4
            "五" -> 5
            "六" -> 6
            "日", "天" -> 7
            else -> null
        }
    }

    private fun labelValue(doc: Document, label: String): String {
        val node = doc.select("th, td, label, span")
            .firstOrNull { it.text().trim().trimEnd('：', ':') == label }
        if (node != null) {
            node.nextElementSibling()?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
            val parentText = node.parent()?.text().orEmpty()
            val idx = parentText.indexOf(label)
            if (idx >= 0) {
                return parentText.substring(idx + label.length)
                    .trimStart('：', ':', ' ')
                    .split(' ')
                    .firstOrNull()
                    .orEmpty()
            }
        }
        return Regex("""$label\s*[：:]\s*([^\s|，,]+)""").find(doc.text())?.groupValues?.get(1).orEmpty()
    }

    private fun safeDoc(html: String): Document =
        runCatching { Jsoup.parse(html) }.getOrElse { Jsoup.parse("") }

    private fun String.firstIntOrNull(): Int? = Regex("""\d+""").find(this)?.value?.toIntOrNull()
}
