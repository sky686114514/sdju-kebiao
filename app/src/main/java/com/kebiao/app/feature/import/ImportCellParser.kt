package com.kebiao.app.feature.import

/* =========================================================================
 * 单元格解析：块锚定 + 窗口扫描
 *
 * ## 真机第十三轮回传的逐格证据，暴露了上一版的四个真问题
 *  - P0-1 行是**分组行**：课名 / 课程号 / 一坨 45 字的长串。长串里有节次和时间，
 *    于是「整行含 节/8:10 就不是周次」的守卫把整格判死 —— 14 条课全变成
 *    「没教室 / 教师待定」。
 *  - P0-2 单双周写法是 **(2-16(双)周)** —— 单双标记套在括号**里面**，
 *    不是上一版猜的 `(2-16周)(双周)`。
 *  - P0-3 上一版的 blob 兜底在**整格**上全局搜周次，会跨块抓错：
 *    高等数学B(1) 抓成 14 周、大学物理B(1) 只记 (2周) 丢掉 (3-16周)。
 *    这是最贵的失效 —— 数据看着正常，其实是错的。
 *  - P0-4 一格里有**多个时间段**（lines = 6/8/12/14），上一版一格只产 1 条。
 *
 * ## 本版的做法
 *  1. **块锚定**：以课程号 `[(（] 4位 - 4位 - 数字 ][)）] - XXXX` 为**主锚**，
 *     每遇一个课程号开一个新块，课名取它**前一行**；
 *  2. **块内窗口扫描**：块内先剔掉课程号与「48学时」，再全局找周次，
 *     每个周次配一个「(节次 时间)」窗口，**教室/教师只在该窗口内取**；
 *  3. **严禁跨块搜索** —— P0-3 的解药就是这一条；
 *  4. 周次**原样保留**单双标记（含嵌套括号），交给下游
 *     `WeekExpressionParser.normalize()` + `detectParity()` 判奇偶。
 *
 * ## 数据纪律（没放宽）
 *  - 解析不出周次的块依旧不入库，只计数；
 *  - 课程号 / 学时**永远不能**当周次或教室；
 *  - 整坨文本绝不充当课名。
 * ========================================================================= */

internal object ImportCellParser {

    /** caller 里的全局名，与 `__kbTableScan` 同款写法。 */
    private const val GLOBAL = "__kbCellParser"

    /** 逐块证据里 `rawBlock` 的截断长度：够看清块边界，又不至于把 payload 撑爆。 */
    private const val BLOCK_RAW = 160

    val JS: String = """
        var $GLOBAL = (function () {

          // ---- 形态字典（全部来自真机回传的逐格证据，不是猜的）----
          //  课程号：(2026-2027-1)-053017P1-15 —— 块的主锚。它长得最像"周次"，
          //  所以扫描前必须整段剔掉，否则必被误认（真机第六轮就是这么错的）。
          var CODE_HEAD = /^[(（]\s*\d{4}\s*[-—~－]\s*\d{4}\s*[-—~－]\s*\d+\s*[)）]\s*-\s*\S+/;
          var CODE_ALL = /[(（]\s*\d{4}\s*[-—~－]\s*\d{4}\s*[-—~－]\s*\d+\s*[)）]\s*-\s*\S+/g;
          //  周次（括号形态）：三种真机写法一次吃下
          //    (2-16(双)周) 单双标记套在括号里   <- 真机主流
          //    (2-16周)(双周) 尾巴另起一个括号
          //    (2周) / (9-11周) / (2-8周)
          //  必须含 周/单/双 才算数：课程号 (2026-2027-1) 与课名 大学物理B(1) 都很像周次
          //  注意：可选的 (双周) 尾巴必须连它前面的 \s* 一起可选 —— 否则尾巴匹配不上时
          //  空格已被吃掉，周次会带一个尾随空格（真机里就是 "1-16周 " 对不上字符串）。
          var WEEK_PAREN = /[(（]\s*\d[\d,\-—~－、.\s]*(?:[(（]\s*[单双]\s*[)）]\s*)?(?:周)?\s*[)）](?:\s*[(（]\s*(?:单双周|单周|双周|单|双)\s*[)）])?/g;
          //  周次（无括号兜底）：2,4,6周 / 1-16周 / 3-15周(单)
          //  **只在括号形态一个都没命中时才用**，避免与真实括号周次抢。
          var WEEK_BARE = /\d{1,2}(?:[-—~－,、]\d{1,2})*(?:周)?(?:\s*[(（]\s*(?:单双周|单周|双周|单|双)\s*[)）])?/g;
          //  节次 + 时间：(1-2节 8:10-9:40) / (10-11节 17:30-19:05)
          var PERIOD_TIME = /[(（]\s*(?:第)?\s*\d{1,2}\s*(?:[-—~－]\s*\d{1,2})?\s*节[^)）]*?\d{1,2}[:：]\d{2}\s*[-—~－]\s*\d{1,2}[:：]\d{2}[^)）]*[)）]/g;
          //  教室里不该留下的括号垃圾（节次/时间残留）
          var PAREN_JUNK = /[(（][^)）]*(?:节|[:：])[^)）]*[)）]/g;

          function squashSpace(s) { return String(s == null ? '' : s).replace(/\s+/g, ' ').trim(); }
          function isWeekToken(t) { return !!t && /周|单|双/.test(t); }

          function periodsFrom(text) {
            var m = String(text || '').match(/(?:第)?(\d{1,2})\s*(?:[-—~－]\s*(\d{1,2}))?\s*节/);
            if (!m) { return null; }
            return { start: parseInt(m[1], 10), end: m[2] ? parseInt(m[2], 10) : parseInt(m[1], 10) };
          }
          function timesFrom(text) {
            var m = String(text || '').match(/(\d{1,2}[:：]\d{2})\s*[-—~－]\s*(\d{1,2}[:：]\d{2})/);
            if (!m) { return null; }
            return { start: m[1].replace(/：/g, ':'), end: m[2].replace(/：/g, ':') };
          }

          // 课名兜底：整格被压成一行且没有课程号时，从头部截到第一个 数字/(/（ 为止。
          //  含 校区|学时|节|教室|教师 或为空 -> 不给（保持 NO_NAME），**绝不整坨入库**。
          function nameFromBlob(cellText) {
            var s = String(cellText || '').replace(/^\s+/, '');
            var m = s.match(/^[^\d(（]*/);
            var cand = m ? squashSpace(m[0]) : '';
            if (!cand) { return null; }
            if (/校区|学时|节|教室|教师|老师/.test(cand)) { return null; }
            return cand;
          }

          /** 像不像一门课的名字（挡掉课程号 / 学时 / 校区 / 节次行）。 */
          function plausibleName(s) {
            if (!s) { return false; }
            if (s.length > 30) { return false; }
            if (CODE_HEAD.test(s)) { return false; }
            if (/学时|校区|教室|教师|老师|周次|[:：]\d/.test(s)) { return false; }
            return /[\u4e00-\u9fa5]/.test(s) || /[A-Za-z]/.test(s);
          }

          // ---- 1) 切块：每遇一个课程号开一块，课名取它前一行 ----
          function splitBlocks(lines) {
            var anchors = [], i;
            for (i = 0; i < lines.length; i++) {
              if (CODE_HEAD.test(lines[i])) { anchors.push(i); }
            }
            var blocks = [];
            if (anchors.length) {
              for (i = 0; i < anchors.length; i++) {
                var a = anchors[i];
                var start = a, name = null, j;
                // 课名 = 课程号之前最近一行像课名的行
                for (j = a - 1; j >= 0; j--) {
                  if (CODE_HEAD.test(lines[j])) { break; }
                  if (plausibleName(lines[j])) { name = lines[j]; start = j; break; }
                }
                // 块尾：默认到下一个课程号；但下一块的课名行要留给下一块
                var stop = (i + 1 < anchors.length) ? anchors[i + 1] : lines.length;
                if (i + 1 < anchors.length) {
                  for (j = anchors[i + 1] - 1; j > a; j--) {
                    if (!CODE_HEAD.test(lines[j]) && plausibleName(lines[j])) { stop = j; break; }
                  }
                }
                var seg = lines.slice(start, stop);
                blocks.push({ name: name, text: seg.join(' '), raw: seg.join(' / ') });
              }
              return blocks;
            }
            // 没有课程号行：整格一块。课名三条路，逐条退让。
            var text = lines.join(' ');
            var nm = null;
            for (i = 0; i < lines.length; i++) {
              var cm = lines[i].match(/[(（]\s*\d{4}\s*[-—~－]\s*\d{4}\s*[-—~－]\s*\d+\s*[)）]\s*-\s*\S+/);
              if (cm && cm.index > 0) {
                var pre = squashSpace(lines[i].slice(0, cm.index).replace(/[\s,，;；]+${'$'}/, ''));
                if (plausibleName(pre)) { nm = pre; break; }
              }
            }
            if (!nm && lines.length >= 2 && plausibleName(lines[0])) { nm = lines[0]; }
            if (!nm && lines.length === 1) { nm = nameFromBlob(lines[0]); }
            return [{ name: nm, text: text, raw: lines.join(' / ') }];
          }

          // ---- 2) 块内窗口扫描：每个 (周次) 配一个 (节次 时间) 窗口 ----
          function scanBlock(blockText, name, ctx) {
            // 课程号与学时先剔掉：它们是最像"周次"的噪音
            var text = String(blockText || '').replace(CODE_ALL, ' ').replace(/\d+\s*学时/g, ' ');
            var weeks = [], m, i;
            WEEK_PAREN.lastIndex = 0;
            while ((m = WEEK_PAREN.exec(text)) !== null) {
              if (!m[0]) { WEEK_PAREN.lastIndex++; continue; }
              var tokP = m[0].replace(/\s+${'$'}/, '');
              if (isWeekToken(tokP)) {
                weeks.push({ token: tokP, start: m.index, end: m.index + tokP.length });
              }
            }
            var source = weeks.length ? 'block' : null;
            if (!weeks.length) {
              WEEK_BARE.lastIndex = 0;
              while ((m = WEEK_BARE.exec(text)) !== null) {
                if (!m[0]) { WEEK_BARE.lastIndex++; continue; }
                var tokB = m[0].replace(/\s+${'$'}/, '');
                if (isWeekToken(tokB)) {
                  weeks.push({ token: tokB, start: m.index, end: m.index + tokB.length });
                }
              }
              if (weeks.length) { source = 'bare'; }
            }
            var campusM = text.match(/([\u4e00-\u9fa5]{2,6}校区)/);
            var campus = campusM ? campusM[1] : null;
            var sessions = [];
            for (i = 0; i < weeks.length; i++) {
              var w = weeks[i];
              // 窗口边界 = 下一个周次的起点 —— **绝不越过块边界**（P0-3 的解药）
              var winEnd = (i + 1 < weeks.length) ? weeks[i + 1].start : text.length;
              var win = text.slice(w.start, winEnd);
              var pt = null, ptEnd = -1;
              PERIOD_TIME.lastIndex = 0;
              var pm = PERIOD_TIME.exec(win);
              if (pm && pm[0]) { pt = pm[0]; ptEnd = w.start + pm.index + pm[0].length; }
              var tailStart = (ptEnd > w.end) ? ptEnd : w.end;
              var tail = squashSpace(text.slice(tailStart, winEnd).replace(PAREN_JUNK, ' '));
              if (!tail && pt) {
                tail = squashSpace(text.slice(w.end, winEnd).replace(PAREN_JUNK, ' '));
              }
              // 真机第三字段会把课名重复一遍（大学物理实验B(1)），教室里不该有它
              if (name && tail) {
                var at = tail.indexOf(name);
                if (at > 0) { tail = squashSpace(tail.slice(0, at)); }
              }
              var room = null, teacher = null;
              var tm = tail.match(/([\u4e00-\u9fa5]{2,4})${'$'}/);
              if (tm) {
                var cand = tm[1];
                // 教师名不会以 馆/楼/场/室… 收尾，也不会带 校区/教 N
                if (!/[馆楼场室园厅房心区路]$/.test(cand) && !/校区|教\d|中心|实验/.test(cand)) {
                  teacher = cand;
                  tail = squashSpace(tail.slice(0, tm.index));
                }
              }
              if (tail && tail.length <= 48 && !/学时|周次|节|[:：]/.test(tail) &&
                  (/[\d]/.test(tail) || /校区|教|楼|馆|场|室|中心/.test(tail))) {
                room = tail;
              }
              // 节次/时间：优先窗口内那一个 (节次 时间)；没有就依次在窗口、周次之前、
              // 整块文本里找 —— **始终是块内，绝不跨块**。
              var pre = text.slice(0, w.start);
              var per = pt ? periodsFrom(pt) : (periodsFrom(win) || periodsFrom(pre) || periodsFrom(text));
              var tim = pt ? timesFrom(pt) : (timesFrom(win) || timesFrom(pre) || timesFrom(text));
              sessions.push({
                name: name, weeksRaw: w.token, room: room, teacher: teacher, campus: campus,
                period: per || ctx.rowPeriod || null,
                time: tim || ctx.rowTime || null,
                lessonSource: (per || tim) ? 'block' : 'row',
                weekSource: source
              });
            }
            var tokens = [];
            for (i = 0; i < weeks.length; i++) { tokens.push(weeks[i].token); }
            return { weeks: tokens, source: source, sessions: sessions };
          }

          /**
           * 解析一个课程格。
           * ctx = { rowPeriod, rowTime } —— 行首（节次列）的兜底值。
           * 返回 { name, sessions: [...], blocks, blob, dropReason, blockEvidence }
           *  - sessions：一格可以产多条（真机 P0-4：一格多个时间段）
           *  - blockEvidence：每块一条，只进 diagnostics，绝不进 courses
           *  - dropReason：null | 'NO_NAME' | 'NO_WEEKS'（整格一条都没产才算丢）
           */
          function parseCell(cellText, lines, ctx) {
            ctx = ctx || {};
            var list = lines || [];
            var blob = list.length <= 1;
            if (!list.length) {
              return { name: null, sessions: [], blocks: 0, blob: true,
                       dropReason: 'NO_NAME', blockEvidence: [] };
            }
            var blocks = splitBlocks(list);
            var sessions = [], ev = [], hasName = false, i, k;
            for (i = 0; i < blocks.length; i++) {
              var b = blocks[i];
              if (b.name) { hasName = true; }
              var r = scanBlock(b.text, b.name, ctx);
              var drop = !b.name ? 'NO_NAME' : (r.sessions.length ? null : 'NO_WEEKS');
              ev.push({
                block: i, blocks: blocks.length, lines: list.length,
                rawBlock: String(b.raw || '').slice(0, ${BLOCK_RAW}),
                name: b.name, weeks: r.weeks.join(' / '), weekSource: r.source,
                room: r.sessions.length ? r.sessions[0].room : null,
                teacher: r.sessions.length ? r.sessions[0].teacher : null,
                drop: drop
              });
              for (k = 0; k < r.sessions.length; k++) { sessions.push(r.sessions[k]); }
            }
            var dropReason = null;
            if (!sessions.length) { dropReason = hasName ? 'NO_WEEKS' : 'NO_NAME'; }
            return {
              name: blocks.length ? blocks[0].name : null,
              sessions: sessions, blocks: blocks.length, blob: blob,
              dropReason: dropReason, blockEvidence: ev
            };
          }

          return {
            parseCell: parseCell,
            periodsFrom: periodsFrom,
            timesFrom: timesFrom,
            nameFromBlob: nameFromBlob,
            plausibleName: plausibleName,
            splitBlocks: splitBlocks,
            scanBlock: scanBlock
          };
        })();
    """.trimIndent()
}
