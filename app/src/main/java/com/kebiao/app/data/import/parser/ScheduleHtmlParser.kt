package com.kebiao.app.data.import.parser

import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.core.ParseStage
import com.kebiao.app.data.import.RawCourse
import com.kebiao.app.data.import.RawSchedule
import com.kebiao.app.data.import.RawSession
import com.kebiao.app.data.import.SemesterDraft
import com.kebiao.app.domain.model.ImportSourceKind
import org.jsoup.nodes.Document
import java.time.DayOfWeek

/**
 * 课表 HTML 解析器（jsoup）—— **全项目唯一承担"教务页面结构"风险的类**。
 *
 * Spec 第 4.3 节：把解析风险压缩到单一类，教务改版时真人只需自适应登录页，
 * 代码只需改这一个文件。因此本类刻意把"结构假设"全部集中到 [FieldPatterns]，
 * 页面改版时按下面三步校准：
 *
 * 1. 用 `payload_ref` 指向的原始 HTML 打开页面（导入失败时已自动留在本地）；
 * 2. 比对本类 [FieldPatterns] 里的表头关键字 / 字段正则；
 * 3. 只改这一个类的模式表，其余代码一行不动。
 *
 * ## 校验纪律（AC-04）
 *
 * 解析**绝不"猜"**：任何一个课程块缺少周次信息、或整表没有可识别的课程，
 * 都返回 [ParseStage.TABLE_STRUCTURE] / [ParseStage.FIELD_MISSING] 错误并**带上原始文本**，
 * 让核对视图能直接告诉用户"哪一格没看懂"。静默跳过一格 = 那门课凭空消失 = 竞品头号差评。
 *
 * ## 已知局限（如实登记，见 OPEN-DECISIONS OD-005）
 *
 * 真实教务页面（iframe 内的"我的课表"）尚未在真机/桌面实测过，因此：
 *  - 单元格内多门课的分隔方式、合并单元格的展开方式均未校准；
 *  - [FieldPatterns] 的字段正则是按 URP 系课表常见形态写的，需要一次真实页面校准。
 * 在这些校准完成前，本类的**失败路径是可信的**（它会明确报错而不是给出错误课表），
 * 但**成功路径未经验证**。
 */
class ScheduleHtmlParser {

    fun parse(
        html: String,
        semester: SemesterDraft,
        payloadRef: String?,
    ): AppResult<RawSchedule> {
        if (html.isBlank()) {
            return fail(ParseStage.TABLE_STRUCTURE, "页面内容为空，未取到课表 HTML")
        }
        val document = org.jsoup.Jsoup.parse(html)
        val tables = document.select("table").filter { it.select("tr").size >= 3 }
        if (tables.isEmpty()) {
            return fail(ParseStage.TABLE_STRUCTURE, "页面中没有任何含 3 行以上的表格，教务页面结构可能已改版")
        }

        val table = tables.maxByOrNull { it.select("td,th").size }
            ?: return fail(ParseStage.TABLE_STRUCTURE, "无法从页面中选出课表表格")

        val rows = table.select("tr")
        val headerIndex = rows.indexOfFirst { FieldPatterns.WEEKDAY_HINT.containsMatchIn(it.text()) }
        if (headerIndex < 0) {
            return fail(ParseStage.TABLE_STRUCTURE, "课表表格中没有找到含「星期一..星期日」的表头行")
        }

        val headerCells = rows[headerIndex].select("td,th")
        val columnToWeekday: Map<Int, DayOfWeek> = headerCells.mapIndexedNotNull { index, cell ->
            matchWeekday(cell.text())?.let { index to it }
        }.toMap()
        if (columnToWeekday.isEmpty()) {
            return fail(ParseStage.TABLE_STRUCTURE, "表头行存在但无法识别出任何星期列")
        }

        val sessionsByCourse = linkedMapOf<String, MutableList<RawSession>>()
        var scannedBlocks = 0

        for (row in rows.drop(headerIndex + 1)) {
            val cells = row.select("td,th")
            if (cells.isEmpty()) continue
            // 用下标访问而非 first()：jsoup 的 Elements.first() 声明为可空，而此处已保证非空。
            val periodLabel = cells[0].text()
            val period = parsePeriod(periodLabel)
            val timeRange = parseTimeRange(periodLabel)

            for ((columnIndex, weekday) in columnToWeekday) {
                val cell = cells.getOrNull(columnIndex) ?: continue
                for (block in splitBlocks(cell.html())) {
                    if (block.isBlank()) continue
                    scannedBlocks++
                    val session = parseBlock(block, weekday, period, timeRange)
                        ?: return fail(
                            ParseStage.FIELD_MISSING,
                            "第 ${weekday.value} 天「$periodLabel」节的课程块无法识别（原文：$block）",
                        )
                    sessionsByCourse.getOrPut(session.first) { mutableListOf() } += session.second
                }
            }
        }

        if (scannedBlocks == 0 || sessionsByCourse.isEmpty()) {
            return fail(ParseStage.TABLE_STRUCTURE, "课表表格里没有解析出任何课程块，教务页面结构可能已改版")
        }

        val courseList = sessionsByCourse.map { (name, sessions) ->
            RawCourse(
                name = name,
                code = null, // 课表页通常不给课程代码；缺失即 null，不臆造
                totalHours = null,
                isOnline = sessions.all { it.room == null } && name.looksOnline(),
                sessions = sessions,
            )
        }

        return AppResult.success(
            RawSchedule(
                kind = ImportSourceKind.WEBVIEW,
                semester = semester.copy(
                    name = semester.name.ifBlank { extractSemesterName(document) ?: "" },
                ),
                courses = courseList,
                payloadRef = payloadRef,
            )
        )
    }

    /** 把单元格 HTML 按 `<br>` 切成课程块；没有 `<br>` 时整格为一块。 */
    private fun splitBlocks(cellHtml: String): List<String> =
        cellHtml.split(FieldPatterns.BLOCK_SEPARATOR)
            .map { fragment -> org.jsoup.Jsoup.parse(fragment).text().trim() }
            .filter { it.isNotEmpty() }

    /**
     * 解析一个课程块。
     *
     * @return `课程名 to RawSession`；无法识别时返回 null（调用方据此显式报错）
     */
    private fun parseBlock(
        block: String,
        weekday: DayOfWeek,
        period: IntRange?,
        timeRange: Pair<String, String>?,
    ): Pair<String, RawSession>? {
        val lines = block.split(FieldPatterns.LINE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val name = lines.first()
        val weeksRaw = lines.firstOrNull { FieldPatterns.WEEKS.containsMatchIn(it) } ?: return null
        val room = lines.firstOrNull { !FieldPatterns.WEEKS.containsMatchIn(it) && FieldPatterns.NON_NAME_HINT.containsMatchIn(it) && it != name }
        val teacher = lines.firstOrNull {
            it != name && it != weeksRaw && it != room && FieldPatterns.TEACHER_NAME.matches(it)
        }

        return name to RawSession(
            weekday = weekday.value,
            periodStart = period?.first,
            periodEnd = period?.last,
            startTime = timeRange?.first,
            endTime = timeRange?.second,
            campus = FieldPatterns.CAMPUS.find(block)?.value,
            room = room,
            teacher = teacher,
            weeksRaw = weeksRaw,
        )
    }

    private fun matchWeekday(text: String): DayOfWeek? {
        val normalized = text.replace(" ", "")
        for (day in DayOfWeek.entries) {
            if (normalized.contains(FieldPatterns.WEEKDAY_TEXTS.getValue(day))) return day
        }
        return null
    }

    /** `1-2节` / `第3节` / `3-4` -> `1..2` / `3..3`。 */
    private fun parsePeriod(label: String): IntRange? {
        val normalized = label.replace(" ", "")
        val match = FieldPatterns.PERIOD.find(normalized) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].toIntOrNull() ?: start
        if (start <= 0 || end < start) return null
        return start..end
    }

    /** `08:10-09:40` -> ("08:10","09:40")。 */
    private fun parseTimeRange(label: String): Pair<String, String>? {
        val match = FieldPatterns.TIME_RANGE.find(label) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    /** 页面标题 / 顶部说明里通常写着"2026-2027学年第一学期"。 */
    private fun extractSemesterName(document: Document): String? =
        document.select("h1,h2,h3,title,span,div")
            .asSequence()
            .map { it.text().trim() }
            .firstOrNull { FieldPatterns.SEMESTER_NAME.containsMatchIn(it) }

    private fun String.looksOnline(): Boolean = FieldPatterns.ONLINE_HINTS.any { contains(it) }

    private fun fail(stage: ParseStage, detail: String): AppResult.Failure =
        AppResult.Failure(AppError.Parse(stage, detail))
}

/**
 * 结构假设表 —— **校准教务改版时只改这里**。
 *
 * 这些正则按 URP / 正方系课表页面的常见形态编写，尚未在真实页面上校准（OD-005）。
 * 纪律：宁可匹配失败（返回显式错误）也不要宽松匹配（错认字段 -> 错误的课表）。
 */
internal object FieldPatterns {

    val WEEKDAY_TEXTS: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "星期一",
        DayOfWeek.TUESDAY to "星期二",
        DayOfWeek.WEDNESDAY to "星期三",
        DayOfWeek.THURSDAY to "星期四",
        DayOfWeek.FRIDAY to "星期五",
        DayOfWeek.SATURDAY to "星期六",
        DayOfWeek.SUNDAY to "星期日",
    )

    /** 表头行的识别（兼容"周一 / 星期一 / 礼拜一"）。 */
    val WEEKDAY_HINT: Regex = Regex("星期[一二三四五六日天]|周[一二三四五六日天]")

    /** 单元格内课程块分隔：连续的 `<br>`。 */
    val BLOCK_SEPARATOR: Regex = Regex("(?i)(?:<br\\s*/?>\\s*)+")

    /** 块内字段分隔：换行或中文顿号式空白。 */
    val LINE_SPLIT: Regex = Regex("[\\r\\n]+")

    /** 周次：`1-16` / `2-16周` / `3-15周(单)` / `1,3,5周`。必须带"周"或含逗号，避免误吃教室号。 */
    val WEEKS: Regex = Regex("""^(?:\d{1,2}(?:\s*-\s*\d{1,2})?)(?:\s*[,，]\s*\d{1,2}(?:\s*-\s*\d{1,2})?)*\s*(?:周|周次)?(?:\(?\s*[单双]\s*\)?)?$""")

    /** 含"教/实验室/楼/室"等字样的行视为地点候选。 */
    val NON_NAME_HINT: Regex = Regex("""\d|教|实验|楼|室|区|校区""")

    /** 中文姓名（2-4 字），用于把教师行从其它行里挑出来。 */
    val TEACHER_NAME: Regex = Regex("""^[\u4e00-\u9fa5]{2,4}$""")

    val CAMPUS: Regex = Regex("""([\u4e00-\u9fa5]{2,6}校区)""")

    /** 节次：`1-2节` / `第3节` / `3-4`。 */
    val PERIOD: Regex = Regex("""(?:第\s*)?(\d{1,2})\s*(?:[-—~]\s*(\d{1,2}))?\s*(?:节)?""")

    val TIME_RANGE: Regex = Regex("""(\d{1,2}:\d{2})\s*[-—~]\s*(\d{1,2}:\d{2})""")

    val SEMESTER_NAME: Regex = Regex("""\d{4}\s*[-—]\s*\d{4}\s*学年.{0,8}学期""")

    val ONLINE_HINTS: List<String> = listOf("线上", "网络", "慕课", "尔雅", "线上授课")
}
