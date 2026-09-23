package com.kebiao.app.feature.import

/* =========================================================================
 * WebView 导入的「登录入口」与「同源抽取脚本」
 *
 * ## 数据流（单向，不建立任何回传通道）
 * ```
 * WebViewLoginScreen.loadUrl(LOGIN_URL)
 *   -> 真人在系统 WebView 完成 SSO（含手机绑定 / 短信验证码）
 *   -> WebViewClient.onPageFinished -> evaluateJavascript(EXTRACTION_SCRIPT) { payloadJson -> ... }
 *   -> WebViewImportSource.submit(payloadJson)   // 只单向取值，不注册 @JavascriptInterface
 *   -> ScheduleJsonCodec.decode -> ImportMapper -> 核对视图 -> ImportCommitter
 * ```
 *
 * ## 为什么脚本产出的是 JSON 而不是 HTML
 * admin L3 的解析入口是 `ScheduleJsonCodec`（与桌面脚本 `tools/fetch_schedule.py`
 * 共用同一份 schema）。因此 WebView 侧必须把「课表表格」映射成 schema JSON；
 * 这一步在本项目里等价于后端 `ScheduleHtmlParser` 的字段级映射，只是运行在页面里。
 *
 * ## 真机首验新增的两条认知（2026-09-20 真机：SSO 登录成功，但停在门户首页）
 * H1 —— **站内路由（SPA pushState）不触发 `onPageFinished`**：
 *   从门户首页点进「我的课表」不产生整页加载，只在 `onPageFinished` 里抽取，
 *   等于脚本永无第二次机会。必须靠 `doUpdateVisitedHistory` + 轮询兜底。
 * H2 —— 课表卡片可能是异步渲染：page-finished 时 DOM 尚未填充，渲染完也不会再来一次
 *   page-finished，同样会被错过。
 * 两条指向同一结论：**抽取时机比抽取脚本更先成为瓶颈**（脚本的未验证性见下方诚实声明）。
 *
 * ## 真机第二轮取证（2026-09-20，用户回传的诊断行）
 * 诊断行是 `jwgl.sdju.edu.cn/home · 已看到课表表格（1个/135格），正在读取`，且
 * **URL 始终停在 `/home`** —— 课表就渲染在门户首页里，**SPA 路由从不变化**。
 * 因此：A/B 两条导航钩子（`onPageFinished` / `doUpdateVisitedHistory`）在本站
 * **从未触发过**，真正抓到课表的是 **C 轮询**。两条都保留（换页型站点靠 A/B，
 * 本站靠 C），但论贡献度时别把 C 当配角 —— 它是本站唯一生效的路径。
 *
 * ## 诚实声明（不得含糊，对应 OD-005）
 * 真实教务「我的课表」页面尚未在真机上实测（iframe 结构、合并单元格、单元格内多门课的
 * 分隔方式都未校准）。因此：
 *   - 本脚本的**失败路径是可信的**：页面不像课表时返回空串，不产生假数据；
 *   - **成功路径未被验证**，需一次真实页面校准（只改这一个字符串常量）。
 * 这与后端 `ScheduleHtmlParser.FieldPatterns` 的处境完全一致，不是本模块独有的风险。
 *
 * ## 学期元数据
 * 教务页面通常不公布「第 1 周周一」的日期，但它是「今天第几周」的唯一依据。
 * 因此这里与 `tools/fetch_schedule.py` 采取同一策略：以 Spec 第 12.2 节已公布的
 * 学期（2026-2027 学年第 1 学期 / 2026-09-14 起 / 16 周）作为默认值；
 * 若页面标题能识别出学年与学期序号则以页面为准。核对视图会把这门「默认学期」
 * 完整展示给用户确认，不存在"悄悄按错误学期入库"的路径。
 * ========================================================================= */

internal object ImportWebView {

    /**
     * 教务系统入口（ARCHITECTURE.md 第 7.6 节实测：未登录返回 302 跳 SSO）。
     * 刻意直接进教务系统而不是先开 SSO 页：302 由系统 WebView 自然跟随，
     * 少一层需要我们维护的跳转逻辑。
     */
    const val LOGIN_URL: String = "https://jwgl.sdju.edu.cn/home"

    /**
     * 轻量探针：只回答"这一页像不像课表"，**不点击、不改 DOM**（禁副作用）。
     * 轮询每轮先跑它，命中才执行 [EXTRACTION_SCRIPT]——避免每轮跑重脚本。
     *
     * 产出字段：`tables`（全部可达文档里含 >=3 行的表总数）/`weekdayHeader`（被选中那张表
     * 能否映射出 >=2 个星期列）/`weekdayColumns`/`maxCells`/`rowCount`/`frames`（iframe 总数）/
     * `framesAccessible`（成功读到的 iframe 数）/`url`。这些字段同时是真机诊断的**取证数据**。
     *
     * 重要：选表与星期判据**全部来自 `ImportTableScan`**，含跨 iframe 扫描。与
     * [EXTRACTION_SCRIPT]（见 `ImportExtractionScript`）用**同一个** `pickScheduleTable`：
     *  - 两边曾各写一套星期判据（探针认「周一」、抽取只认「星期一」），代价是
     *    "诊断说已看到课表表格、抽取却静默返回空串"；
     *  - 探针曾按"格子最多"选表，代价是挑中月历条（`cellsPerRow[2] = 127`）而抽取在那张表里
     *    一格课都找不到。**探针与抽取必须选出同一张表**，判据改一处两边同时生效。
     */
    val PROBE_SCRIPT: String = """
        ${ImportTableScan.JS}
        (function () {
          var KB = __kbTableScan;
          // 跨 iframe 扫描 + 语义选表，与抽取同一份实现
          var scored = KB.pickScheduleTable(document);
          var picked = scored.length ? scored[0] : null;
          var frames = KB.framesOf(document).slice(1);
          return JSON.stringify({
            tables: scored.length,
            weekdayHeader: !!picked && picked.weekdays >= 2,
            weekdayColumns: picked ? picked.weekdays : 0,
            maxCells: picked ? picked.cells : 0,
            rowCount: picked ? picked.rows.length : 0,
            frames: frames.length,
            framesAccessible: frames.filter(function (f) { return f.accessible; }).length,
            hasIframe: frames.length,
            url: location.href
          });
        })()
    """.trimIndent()

    /**
     * 「我的课表」入口探测：扫描含「课表」字样的可点击元素，返回**唯一候选**。
     *
     * 安全约束（硬）：
     *   - 只**读取** href，**绝不点击**（宁可让用户自己点，也不能替用户在教务系统里乱点）；
     *   - 候选 0 个或多于 1 个一律返回空串 —— 歧义时不猜；
     *   - 只接受带 href 的元素（没有 href 就无从 `loadUrl`，点了是假按钮）。
     * href 会用 `location.href` 补全为绝对地址，避免相对路径交给 `loadUrl` 失败。
     */
    val SCHEDULE_ENTRY_SCRIPT: String = """
        (function () {
          function text(el) { return (el && el.textContent ? el.textContent : '').replace(/\s+/g, ' ').trim(); }
          function all(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }
          var seen = {};
          var candidates = [];
          all('a[href], [onclick], button, [role="button"], [role="link"]').forEach(function (el) {
            var t = text(el);
            if (!t || t.indexOf('课表') < 0) { return; }
            if (t.length > 40) { return; }
            var raw = el.getAttribute ? el.getAttribute('href') : null;
            if (!raw) { return; }
            var abs = raw;
            try { abs = new URL(raw, location.href).href; } catch (e) { }
            // 只接受 http(s)：`javascript:` / `#/路由` 之类的 href 交给 loadUrl 要么
            // 无效果，要么会丢掉 SPA 当前状态跳到一个空白深链，宁可不给这个入口。
            if (!/^https?:/i.test(abs)) { return; }
            var key = abs + '|' + t;
            if (seen[key]) { return; }
            seen[key] = true;
            candidates.push({ href: abs, text: t });
          });
          if (candidates.length !== 1) { return ''; }
          return JSON.stringify({ href: candidates[0].href, text: candidates[0].text });
        })()
    """.trimIndent()

    /**
     * 同源抽取脚本：把课表表格映射为 `ScheduleJsonCodec` 的 schema JSON。
     *
     * 本体在同包 [ImportExtractionScript] —— 它自己就 200 行上下，塞进本文件必然破
     * 「单文件 ≤300 行」的硬约束。这里保留同名入口，调用方（[ImportViewModel]）不受影响。
     */
    val EXTRACTION_SCRIPT: String get() = ImportExtractionScript.SCRIPT

    /**
     * 结构导出脚本（诊断用，供真机取证）：把探针认定的那张课表结构交出来。
     * 本体在同包 [ImportStructureDump]，原因同上。
     */
    val STRUCTURE_DUMP_SCRIPT: String get() = ImportStructureDump.SCRIPT
}
