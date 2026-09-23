package com.kebiao.app.feature.import

import org.json.JSONObject

/* =========================================================================
 * WebView 诊断 JSON -> 一句人话
 *
 * 从 `ImportViewModel` 拆出来，只为守住「单文件 ≤300 行」：VM 现在同时装
 * 流程编排、登录现场诊断字段、一次性指令，四段文案函数会把文件顶到 376 行。
 *
 * 纪律：解析不出来就说"解析不出来"，**不猜、不伪造**。这些文案是给真机上
 * 卡住的用户看的，一句漂亮但错误的话比一句朴素的"无法解析"贵得多。
 * ========================================================================= */

/**
 * 抽取脚本的**失败原因枚举** —— 与 `ImportExtractionScript.SCRIPT` 里
 * `fail(reason, …)` 的字符串**一一对应**，改一侧必须同步另一侧。
 *
 * 全部取值（四项，穷举）：
 *  - [NO_TABLE]：页面上没有「含 3 行以上的表格」。附带 `tables`（页面表格总数）。
 *  - [NO_HEADER_ROW]：找到表格，但没有任何一行含星期文本。
 *    附带 `tables`（候选表数）、`rows`（最大表的行数）。
 *  - [NO_WEEKDAY_COLUMNS]：定位到表头行，但**映射出的星期列不足 2 个** ——
 *    表头写法与星期判据对不上（真机上正是「周一」简写触发的），或误把课程行当表头。
 *    附带 `headerText`（表头行文本，截断 120）、`headerCells`（各格文本数组）。
 *  - [NO_SESSIONS]：表头正常，但一格课程都没解析出来。
 *    附带 `cellsWithText`、`droppedNoWeeks`、`droppedNoName`、`sampleCellText`（截断 120）。
 *
 * 为什么要有它：这些出口原先一律 `return ''` —— 页面明明有课表，UI 却永远停在
 * "正在读取"，用户只能干等。现在每种失败都能一句话说清卡在哪一步。
 */
internal object ExtractFailureReason {
    const val NO_TABLE = "NO_TABLE"
    const val NO_HEADER_ROW = "NO_HEADER_ROW"
    const val NO_WEEKDAY_COLUMNS = "NO_WEEKDAY_COLUMNS"
    const val NO_SESSIONS = "NO_SESSIONS"
}

/** 探针 JSON -> 一句人话。 */
internal fun describeProbe(probeJson: String): String = try {
    val probe = JSONObject(probeJson)
    val tables = probe.optInt("tables", 0)
    val weekday = probe.optBoolean("weekdayHeader", false)
    val cells = probe.optInt("maxCells", 0)
    val frames = probe.optInt("frames", 0)
    val framesAcc = probe.optInt("framesAccessible", 0)
    val fi = if (frames > 0) " · iframe $frames 个（可读 $framesAcc）" else ""
    when {
        tables == 0 -> "可读部分没有像课表的表格$fi · 还没到课表页或课表在跨域 iframe 里"
        !weekday -> "可读部分 $tables 张表$fi · 没看到星期表头"
        else -> "已看到课表表格（$tables 张 / $cells 格$fi），正在读取"
    }
} catch (_: Exception) {
    "探针结果无法解析"
}

/**
 * 探针是否认定"像课表"：有表格且出现星期表头。
 * 与 `WebViewLoginScreen` 里的同名判定**保持一致**（同一条规则两处用，避免口径分裂）。
 */
internal fun probeLooksLikeSchedule(probeJson: String): Boolean = try {
    val probe = JSONObject(probeJson)
    probe.optInt("tables", 0) > 0 && probe.optBoolean("weekdayHeader", false)
} catch (_: Exception) {
    false
}

/**
 * 抽取失败 JSON -> 一句人话，让"卡住了"变成"卡在第几步"。
 *
 * 分支与 [ExtractFailureReason] 的四个取值一一对应；认不出 reason 就直说认不出，不猜。
 */
internal fun describeExtractFailure(failureJson: String): String = try {
    val obj = JSONObject(failureJson)
    when (obj.optString("reason", "")) {
        ExtractFailureReason.NO_TABLE -> {
            val fr = obj.optInt("frames", 0)
            val fi = if (fr > 0) " · iframe $fr 个（可读 ${obj.optInt("framesAccessible", 0)}）" else ""
            "可读部分没有像课表的表格（页面表格 ${obj.optInt("tables", 0)} 个$fi）"
        }

        ExtractFailureReason.NO_HEADER_ROW ->
            "找到表格但没有含星期的表头行（候选 ${obj.optInt("tables", 0)} 张 / " +
                "最大表 ${obj.optInt("rows", 0)} 行）"

        ExtractFailureReason.NO_WEEKDAY_COLUMNS -> {
            val header = obj.optString("headerText", "").take(60)
            "已看到课表表格，但表头里没找到星期列（周一/星期二…）：表头是「$header」"
        }

        ExtractFailureReason.NO_SESSIONS ->
            "已看到课表表格，但一格课程都没解析出来：有内容的格子 " +
                "${obj.optInt("cellsWithText", 0)} 个，" +
                "${obj.optInt("droppedNoWeeks", 0)} 格没识别出周次"

        else -> "抽取失败，原因未识别"
    }
} catch (_: Exception) {
    "抽取失败，结果无法解析"
}

/**
 * 自动点击脚本的结论 JSON -> 一句人话，让用户知道 App 替他点了什么。
 *
 * 与 [describeExtractFailure] 同纪律：认不出 `step` 就直说无法解析，**不猜** ——
 * 这是真机上"停在门户首页"时用户唯一能看到的进展说明，一句漂亮但错误的话
 * 比一句朴素的"无法解析"贵得多。
 *
 * `step` 取值与 `ImportEntryClick.SCRIPT` 的四个出口一一对应。
 */
internal fun describeEntryClick(stepJson: String): String = try {
    val obj = JSONObject(stepJson)
    val text = obj.optString("text", "").takeIf { it.isNotBlank() }
    when (obj.optString("step", "")) {
        "entry" -> "已自动点进「${text ?: "课表"}」，正在等课表渲染"
        "menu" -> "没看到课表入口，已自动点开「${text ?: "菜单"}」"
        "expand" -> "菜单里也没有课表，已自动点开「${text ?: "分组"}」"
        "none" -> "页面上没找到课表入口，请手动点左上角菜单里的「我的课表」"
        else -> "自动进入课表：结果无法解析"
    }
} catch (_: Exception) {
    "自动进入课表：结果无法解析"
}

/** 「我的课表」入口 JSON -> href；歧义或解不出来时返回 null。 */
internal fun readEntryHref(entryJson: String): String? = try {
    JSONObject(entryJson).optString("href", "").takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null
}

/**
 * 成功载荷的 `diagnostics` -> (一句人话, 逐格证据 JSON 原文)。
 *
 * 真机第六轮的现象是"课表认出来了却只解析出 1 门课"，其余格子被静默丢弃 ——
 * 所以这里把「读到几格 / 入库几门 / 丢弃几格 / 整格一行几格 / 切成几块」摊给用户看，
 * 并把逐块证据原文留一份，用户点一下就能复制出来。
 *
 * 失败载荷（`{"ok":false,…}`）没有 diagnostics，两个都返回 null —— **不崩**。
 */
internal fun describeCellEvidence(payloadJson: String): Pair<String?, String?> = try {
    val root = JSONObject(payloadJson)
    // 失败载荷：ok=false 且没有 diagnostics
    if (!root.optBoolean("ok", true)) return Pair(null, null)
    val diag = root.optJSONObject("diagnostics") ?: return Pair(null, null)
    val cells = diag.optInt("cellsWithText", 0)
    val noName = diag.optInt("droppedNoName", 0)
    val noWeeks = diag.optInt("droppedNoWeeks", 0)
    val blob = diag.optInt("blobCells", 0)
    val lineBlob = diag.optInt("droppedLineBlob", 0)
    val blocks = diag.optInt("blocksFound", 0)
    val multi = diag.optInt("multiBlockCells", 0)
    val courses = root.optJSONArray("courses")?.length() ?: 0
    val blobPart = if (blob > 0) " / 整格一行 $blob 格（兜底也失败 $lineBlob 格）" else ""
    val blockPart = if (blocks > 0) "；切块 $blocks 块（一格多块 $multi 格）" else ""
    val stats = "读到 $cells 格有内容，入库 $courses 门课；" +
        "丢弃：无课名 $noName 格 / 无周次 $noWeeks 格" + blobPart + blockPart
    val evidence = diag.optJSONArray("evidence")?.takeIf { it.length() > 0 }?.toString()
    Pair(stats, evidence)
} catch (_: Exception) {
    Pair(null, null)
}
