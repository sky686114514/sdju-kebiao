package com.kebiao.app.feature.import

/* =========================================================================
 * 选表 / 取行 / 星期列映射 —— 三条路径共用的一段 JS：
 *   探针 `ImportWebView.PROBE_SCRIPT`
 *   抽取 `ImportExtractionScript.SCRIPT`
 *   取证 `ImportStructureDump.SCRIPT`
 *
 * ## 为什么要共用（真机前两轮的教训，每一条都付出过代价）
 *  1. 探针认「周一」、抽取只认「星期一」 —— 诊断说"已看到课表表格"，抽取静默失败；
 *  2. 探针按"格子最多"选表 —— 挑中月历条（真机 dump：`cellsPerRow[2] = 127`），
 *     那张表里一个像课程的格子都没有（`samples: []`），抽取消失。
 * 同一个概念写两套判据必错一次，现在三者共用同一个 `pickScheduleTable`。
 *
 * ## 三条从**首份真实 DOM**（2026-09-20 用户回传）学到的规则
 *  R1 只取**直接子**行/格：嵌套 `<table>` 的 `tr` 混进来会让列索引整体错位，
 *     "周一"会落到错误的那一列上 —— 整张课表平移一列。
 *  R2 一个单元格里出现 **>=2 个不同星期**就是聚合标签（真实 `header[0]` 是
 *     "周一周二周三周四周五周六周日"），整列跳过；否则节次列会被当成某一天。
 *  R3 选表**语义优先**：先比"能映射出几个星期列"，再比格子数。
 *  R4 **跨 iframe 扫描**（真机第五轮破案）：课表渲染在 iframe 文档里，
 *     主文档的 `querySelectorAll` 天生跨不进。`framesOf` 收集全部可达
 *     `contentDocument`，跨域 iframe try/catch 记为不可读不算失败。
 *
 * caller 形态：`${ImportTableScan.JS}` + 各自 IIFE（本文件自身不产出任何结果）。
 * ========================================================================= */

internal object ImportTableScan {

    /** caller 里的全局名：三个脚本各求值一次，同名覆盖无副作用。 */
    private const val GLOBAL = "__kbTableScan"

    val JS: String = """
        var $GLOBAL = (function () {
          var CN_DAY = '一二三四五六日天';
          var BLOCK = ['div','p','li','ul','ol','tr','table','section','article','header','footer',
                       'h1','h2','h3','h4','h5','h6','dl','dt','dd','blockquote','pre'];

          function squash(s) { return (s || '').replace(/\s+/g, ' ').trim(); }
          // 全角数字 -> 半角：教务页面里两种都可能出现在周次/节次上
          function hw(s) {
            return (s || '').replace(/[\uFF10-\uFF19]/g, function (c) {
              return String.fromCharCode(c.charCodeAt(0) - 0xFEE0);
            });
          }

          // R1 只取直接子行/子格。为什么不能 `querySelectorAll('tr')`：
          // 月历条那张表的第 2 行就是这么数出 127 个格子的（嵌套表的 td 全被算进来）。
          function probe(scope, sel) {
            if (!scope || !scope.querySelectorAll) { return []; }
            try { return Array.prototype.slice.call(scope.querySelectorAll(sel)); } catch (e) { return []; }
          }
          function childrenByTag(parent, tags) {
            var out = [];
            if (!parent || !parent.children) { return out; }
            Array.prototype.slice.call(parent.children).forEach(function (k) {
              if (tags.indexOf((k.tagName || '').toUpperCase()) >= 0) { out.push(k); }
            });
            return out;
          }
          function rowsOf(table) {
            var hit = probe(table, ':scope > tbody > tr, :scope > thead > tr, :scope > tfoot > tr, :scope > tr');
            if (hit.length) { return hit; }
            var groups = childrenByTag(table, ['TBODY', 'THEAD', 'TFOOT']);
            if (!groups.length) { return childrenByTag(table, ['TR']); }
            var out = [];
            groups.forEach(function (g) { childrenByTag(g, ['TR']).forEach(function (r) { out.push(r); }); });
            return out.length ? out : childrenByTag(table, ['TR']);
          }
          function cellsOf(row) {
            var hit = probe(row, ':scope > td, :scope > th');
            if (hit.length) { return hit; }
            return childrenByTag(row, ['TD', 'TH']);
          }

          // 隐私：iframe src 可能带 query 参数（含学号）。只保留 host+path，剥 query 与 fragment。
          function stripQuery(url) {
            if (!url) { return null; }
            var s = String(url), q = s.indexOf('?'), h = s.indexOf('#');
            if (q >= 0) { s = s.slice(0, q); }
            if (h >= 0) { s = s.slice(0, h); }
            return s;
          }

          // R4 跨 iframe 扫描：课表常在 iframe 文档里，主文档的 querySelectorAll 跨不进。
          //  逐个取 iframe.contentDocument，跨域会抛异常 —— try/catch 记不可读，不算失败不中断。
          //  深度上限 2（root=0 → iframe=1 → 嵌套 iframe=2），教务系统不会嵌那么深。
          function framesOf(rootDoc, depth) {
            depth = depth || 0;
            var out = [{ doc: rootDoc, src: null, accessible: true, depth: depth }];
            if (depth >= 2 || !rootDoc) { return out; }
            var iframes;
            try { iframes = Array.prototype.slice.call(rootDoc.querySelectorAll('iframe')); }
            catch (e) { return out; }
            iframes.forEach(function (f) {
              var srcStr = null, cd = null, ok = false;
              try { srcStr = stripQuery(f.getAttribute('src') || ''); } catch (e2) { srcStr = null; }
              try { cd = f.contentDocument; ok = !!cd; } catch (e3) { cd = null; ok = false; }
              if (ok && cd) {
                out.push({ doc: cd, src: srcStr, accessible: true, depth: depth + 1 });
                var nested = framesOf(cd, depth + 1);
                for (var i = 1; i < nested.length; i++) { out.push(nested[i]); }
              } else {
                out.push({ doc: null, src: srcStr, accessible: false, depth: depth + 1 });
              }
            });
            return out;
          }

          // 块级感知取文本：`<br>` 与块级元素都折算成换行。为什么：只按 `<br>`
          // 切，遇到"每个字段一个 <div>"的页面会把整格压成一行，周次必然失配。
          // 抽取（解析 lines）与取证（导出矩阵）同用这一个实现。
          function blockText(el) {
            var out = '', kids = el.childNodes, i, n, tag;
            for (i = 0; i < kids.length; i++) {
              n = kids[i];
              if (n.nodeType === 3) { out += n.nodeValue || ''; continue; }
              if (n.nodeType !== 1) { continue; }
              tag = (n.tagName || '').toLowerCase();
              if (tag === 'br') { out += '\n'; continue; }
              out += blockText(n);
              if (BLOCK.indexOf(tag) >= 0) { out += '\n'; }
            }
            return out;
          }
          function linesOf(el) {
            return blockText(el).split(/\r?\n/).map(squash).filter(function (s) { return s.length > 0; });
          }

          // 重要：星期判据**只有这一套**（探针曾另写一个正则，代价是上一轮的静默失败）。
          //    "周"必须紧跟集合内字符："2-16周"/"第3周"/"每周"都不会被误判成星期。
          //    返回长度有严格语义：0 无关 / 1 可作某一天的列头 / >=2 聚合标签。
          function weekdaysIn(text) {
            var s = squash(hw(text)).replace(/\s/g, '');
            var re = /星期([一二三四五六日天])|周([一二三四五六日天])/g;
            var seen = {}, out = [], m;
            while ((m = re.exec(s)) !== null) {
              var ch = m[1] || m[2];
              var idx = CN_DAY.indexOf(ch);
              if (idx < 0) { continue; }
              var day = idx < 6 ? idx + 1 : 7;   // 日 / 天 都算第 7 天
              if (!seen[day]) { seen[day] = true; out.push(day); }
            }
            return out;
          }

          // R2 一行 -> "列序号 -> 星期"。
          // 重要：只在**表头行**调用：课程格里的"周五"是上课时间，不是列头。
          function weekdayColumns(row) {
            var map = {}, skipped = [];
            cellsOf(row).forEach(function (cell, index) {
              var days = weekdaysIn(cell.textContent || '');
              if (days.length >= 2) { skipped.push(index); return; }
              if (days.length === 1) { map[index] = days[0]; }
            });
            return { map: map, skipped: skipped };
          }

          function countCells(rows) {
            var n = 0;
            rows.forEach(function (r) { n += cellsOf(r).length; });
            return n;
          }

          // R3 单表打分：找出"能映射出不同星期列最多"的那一行当表头候选
          function scanTable(t) {
            var rows = rowsOf(t);
            if (rows.length < 3) { return null; }
            var best = { table: t, rows: rows, cells: countCells(rows), weekdays: 0, headerIndex: -1 };
            rows.forEach(function (r, i) {
              var mapped = weekdayColumns(r);
              var days = {}, n = 0;
              for (var k in mapped.map) {
                if (Object.prototype.hasOwnProperty.call(mapped.map, k) && !days[mapped.map[k]]) {
                  days[mapped.map[k]] = true; n++;
                }
              }
              if (n > best.weekdays) { best.weekdays = n; best.headerIndex = i; }
            });
            return best;
          }

          // R3+R4 在**全部可达文档**的合集上选表（判据不变：>=2 星期列优先 → 星期列数 → 格子数）。
          // 选中结果带 doc / src 字段，回答"来自哪个 frame"。
          // 重要：探针/抽取/取证必须挑中同一张表，各自排一套就是第二种错位。
          function pickScheduleTable(rootDoc) {
            var frames = framesOf(rootDoc);
            var scored = [];
            frames.forEach(function (fr) {
              if (!fr.doc) { return; }
              var tables;
              try { tables = Array.prototype.slice.call(fr.doc.querySelectorAll('table')); }
              catch (e) { return; }
              tables.forEach(function (t) {
                var s = scanTable(t);
                if (s) { s.doc = fr.doc; s.src = fr.src; scored.push(s); }
              });
            });
            scored.sort(function (a, b) {
              var sa = a.weekdays >= 2 ? 1 : 0, sb = b.weekdays >= 2 ? 1 : 0;
              if (sa !== sb) { return sb - sa; }
              if (a.weekdays !== b.weekdays) { return b.weekdays - a.weekdays; }
              return b.cells - a.cells;
            });
            return scored;
          }

          // 表头下方数据行的最大格子数（列偏移判定用）
          function maxDataCells(rows, headerIndex) {
            var n = 0;
            for (var i = 0; i < rows.length; i++) {
              if (i <= headerIndex) { continue; }
              var c = cellsOf(rows[i]).length;
              if (c > n) { n = c; }
            }
            return n;
          }

          // 列偏移对齐：真实 DOM 里表头行是 [聚合格, 周一…周日]（8 格）而数据行只有 7 格 ——
          // 星期列序号比数据行列序号**整体大 1**，不平移就会整体错一列（周一读到节次列）。
          // 只在证据完整时平移：星期列数 == 数据行最大格子数 且 存在前置列；否则不猜。
          function columnShiftOf(rows, headerIndex, columnToWeekday) {
            var indices = [];
            for (var k in columnToWeekday) {
              if (Object.prototype.hasOwnProperty.call(columnToWeekday, k)) { indices.push(parseInt(k, 10)); }
            }
            if (!indices.length) { return 0; }
            var min = Math.min.apply(null, indices);
            return min > 0 && indices.length === maxDataCells(rows, headerIndex) ? min : 0;
          }

          return {
            squash: squash, hw: hw, blockText: blockText, linesOf: linesOf,
            rowsOf: rowsOf, cellsOf: cellsOf, countCells: countCells,
            weekdaysIn: weekdaysIn, weekdayColumns: weekdayColumns,
            framesOf: framesOf, stripQuery: stripQuery,
            pickScheduleTable: pickScheduleTable, maxDataCells: maxDataCells,
            columnShiftOf: columnShiftOf
          };
        })();
    """.trimIndent()
}
