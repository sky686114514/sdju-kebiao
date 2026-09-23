package com.kebiao.app.feature.import

/* =========================================================================
 * 同槽合并 + 周次并集规范串
 *
 * ## 真机第十四轮的现象（用户 21:27 截图）
 *  今天视图（第 2 教学周）里 `中国近现代史纲要` 出现**两次**、卡片内容逐字相同；
 *  核对页写「共 13 门课 · 60 条上课安排」，50 条告警全是「同一门课的两条安排周次重叠」。
 *
 * ## 根因（不是切块错）
 *  教务系统把**同一节课**按周次段渲染成多段塞在同一个格子里：
 *  `大学物理实验B(1)` 就是 9-11 / 12 / 13 / 14 / 15-16 五段。
 *  第 13 轮把它们当成 5 条安排产出了 —— 缺的就是这一步「合并」。
 *  对用户来说：同一门课 + 同一星期 + 同一节次 + 同一教室 + 同一教师 = **一节课**，
 *  周次应当是**并集**，不该并列成多条。
 *
 * ## 为什么不能直接拼接 `weeksRaw`
 *  下游 `WeekExpressionParser.normalize()`（data/import/parser/WeekExpressionParser.kt:96）
 *  会剥掉所有非「数字 / 连字符 / 逗号」的字符。`(2周)(3-16周)` 拼起来 -> `23-16`
 *  -> 区间 23..16 **起止倒置报错**。所以必须展开成周集合、求并集、再压缩成规范串。
 *
 * ## 输出为什么不能带「单 / 双」
 *  `detectParity()`（同文件 :88）按**原串**判奇偶：`contains("单") -> ODD`。
 *  若输出里留着「单」字，下游会把已经求过并集的集合**再筛一遍**，
 *  等于把 `(2-16(双)周) ∪ (3-16周)` 的 {2..16} 错杀成 {2,4,..,16}。
 *  所以 parity 在这里就要**落进数字里**，输出串必须是纯「数字 , - 周」。
 *
 * ## 数据纪律
 *  - token 展开失败 => **原样保留**并计入 `mergeUnresolved`，绝不静默丢弃；
 *  - 本层不做学期夹逼（夹逼由下游按 `effectiveTotal` 做）。
 * ========================================================================= */

internal object ImportWeekMerge {

    /** caller 里的全局名，与 `__kbTableScan` / `__kbCellParser` 同款写法。 */
    private const val GLOBAL = "__kbWeekMerge"

    val JS: String = """
        var $GLOBAL = (function () {

          // 学期的周数上限。用于把「课程号里的年份」这类噪音挡在门外 ——
          //  这位数字一旦混进来，展开出的集合会有几千项，是典型的沉默逻辑错误。
          var MAX_WEEK = 60;

          /**
           * parity 判定 —— 必须与 Kotlin 侧 `WeekExpressionParser.detectParity` 逐字一致：
           *   "单双周"/"每周" -> ALL，再 "单" -> ODD，再 "双" -> EVEN。
           * 顺序不能改：教务偶尔输出的「单双周」若先判了「单」就会被当成单周。
           */
          function parityOf(tok) {
            if (/单双周|每周/.test(tok)) { return 'ALL'; }
            if (/单/.test(tok)) { return 'ODD'; }
            if (/双/.test(tok)) { return 'EVEN'; }
            return 'ALL';
          }

          /**
           * 单个周次片段 -> 升序去重后的周数组。
           * 拿不到任何合法周数（或越界 / 起止倒置 / 被 parity 筛空）返回 null：
           *  **不明着猜**，交给调用方原样保留并计数。
           */
          function expandToken(tok) {
            var src = String(tok == null ? '' : tok);
            var parity = parityOf(src);
            var hits = {}, got = false;
            var re = /\d{1,2}\s*(?:[-—~－至到]\s*\d{1,2})?/g, m;
            while ((m = re.exec(src)) !== null) {
              if (!m[0]) { re.lastIndex++; continue; }
              var core = m[0].replace(/\s/g, '');
              var seg = core.split(/[-—~－至到]/);
              var a = parseInt(seg[0], 10);
              var b = seg.length > 1 ? parseInt(seg[1], 10) : a;
              if (!isFinite(a) || a < 1 || a > MAX_WEEK) { return null; }
              if (!isFinite(b) || b < 1 || b > MAX_WEEK) { return null; }
              if (a > b) { return null; }
              for (var w = a; w <= b; w++) { hits[w] = true; got = true; }
            }
            if (!got) { return null; }
            var out = [];
            for (var k in hits) {
              if (!Object.prototype.hasOwnProperty.call(hits, k)) { continue; }
              var n = parseInt(k, 10);
              if (parity === 'ODD' && n % 2 === 0) { continue; }
              if (parity === 'EVEN' && n % 2 === 1) { continue; }
              out.push(n);
            }
            if (!out.length) { return null; }
            return out.sort(function (x, y) { return x - y; });
          }

          /** 升序去重。 */
          function uniqSorted(list) {
            var seen = {}, out = [], i;
            for (i = 0; i < list.length; i++) { seen[list[i]] = true; }
            for (var k in seen) {
              if (Object.prototype.hasOwnProperty.call(seen, k)) { out.push(parseInt(k, 10)); }
            }
            return out.sort(function (x, y) { return x - y; });
          }

          /**
           * 周集合 -> 规范串（**不含"单/双"字样**）。
           *  连续 >= 3 个 -> `a-b`；不足 3 个 -> 逐个列出；`,` 连接；结尾 `周`。
           *  {2..16} -> `2-16周`      {3,5,..,15} -> `3,5,7,9,11,13,15周`
           */
          function compress(list) {
            var s = uniqSorted(list), out = [], i = 0;
            while (i < s.length) {
              var j = i;
              while (j + 1 < s.length && s[j + 1] === s[j] + 1) { j++; }
              if (j - i + 1 >= 3) { out.push(s[i] + '-' + s[j]); }
              else { for (var q = i; q <= j; q++) { out.push(String(s[q])); } }
              i = j + 1;
            }
            return out.join(',') + '周';
          }

          /**
           * 多个周次片段 -> 并集规范串。
           * 返回 { weeksRaw, weeks, unresolved }：
           *  - weeks：展开后的周集合（纯数字，parity 已落进去），便于取证；
           *  - unresolved：展开失败的片段数 —— 这些片段**原样**接在规范串后面，不静默。
           */
          function unionTokens(tokens) {
            var i, k, pool = [], bad = [];
            for (i = 0; i < tokens.length; i++) {
              var ex = expandToken(tokens[i]);
              if (!ex) { bad.push(tokens[i]); continue; }
              for (k = 0; k < ex.length; k++) { pool.push(ex[k]); }
            }
            var weeks = uniqSorted(pool);
            var parts = [];
            if (weeks.length) { parts.push(compress(weeks)); }
            for (i = 0; i < bad.length; i++) { parts.push(bad[i]); }
            return { weeksRaw: parts.join(','), weeks: weeks, unresolved: bad.length };
          }

          /**
           * 同槽合并。
           * key = (weekday, periodStart, periodEnd, room, teacher)；null 按空串参与 key。
           * 同 key 的多条 -> 一条，weeksRaw 取并集规范串；其余字段取首条的
           *  （缺值才由后条补 —— 空教室 + 有教室 这种组合才补得有意义）。
           * 返回 { sessions, mergedGroups, unresolved }；session 字段集合与原 schema 一致。
           */
          function mergeSessions(sessions) {
            var list = sessions || [], map = {}, keys = [], i;
            for (i = 0; i < list.length; i++) {
              var s = list[i];
              var key = [
                s.weekday == null ? '' : s.weekday,
                s.periodStart == null ? '' : s.periodStart,
                s.periodEnd == null ? '' : s.periodEnd,
                s.room || '', s.teacher || ''
              ].join('|');
              if (!map[key]) {
                map[key] = {
                  weekday: s.weekday, periodStart: s.periodStart, periodEnd: s.periodEnd,
                  startTime: s.startTime, endTime: s.endTime,
                  campus: s.campus, room: s.room, teacher: s.teacher,
                  tokens: [s.weeksRaw]
                };
                keys.push(key);
                continue;
              }
              var g = map[key];
              g.tokens.push(s.weeksRaw);
              if (!g.startTime && s.startTime) { g.startTime = s.startTime; }
              if (!g.endTime && s.endTime) { g.endTime = s.endTime; }
              if (!g.campus && s.campus) { g.campus = s.campus; }
            }
            var out = [], mergedGroups = 0, unresolved = 0;
            for (i = 0; i < keys.length; i++) {
              var grp = map[keys[i]];
              var u = unionTokens(grp.tokens);
              unresolved += u.unresolved;
              if (grp.tokens.length > 1) { mergedGroups++; }
              out.push({
                weekday: grp.weekday,
                periodStart: grp.periodStart, periodEnd: grp.periodEnd,
                startTime: grp.startTime, endTime: grp.endTime,
                campus: grp.campus, room: grp.room, teacher: grp.teacher,
                weeksRaw: u.weeksRaw
              });
            }
            return { sessions: out, mergedGroups: mergedGroups, unresolved: unresolved };
          }

          return {
            parityOf: parityOf,
            expandToken: expandToken,
            compress: compress,
            unionTokens: unionTokens,
            mergeSessions: mergeSessions
          };
        })();
    """.trimIndent()
}
