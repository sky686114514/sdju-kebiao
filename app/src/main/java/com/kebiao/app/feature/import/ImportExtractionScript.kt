package com.kebiao.app.feature.import

/* =========================================================================
 * 课表抽取脚本（同源执行，产出 `ScheduleJsonCodec` 的 schema JSON）—— 单独
 * 成文件只为守住「单文件 ≤300 行」：`ImportWebView.kt` 还要装登录入口、探针、
 * 入口探测三块，本脚本自身已近 300 行，硬塞同文件必然破线。
 *
 * ## 选表不在本文件
 * 见 `ImportTableScan`（跨 iframe 扫描 + 语义优先选表 + 星期列映射）。
 *
 * ## 单元格解析不在本文件
 * 见 `ImportCellParser`（**块锚定 + 窗口扫描**）。真机第十三轮回传的逐格证据
 * 推翻了第六轮的 blob 兜底：整格全局搜周次会**跨块抓错**（静默错数据，最贵的一类），
 * 现在改成以课程号切块、块内按 (周次)(节次 时间) 窗口扫描，一格可产多条。
 *
 * ## 数据纪律 —— 失败必回 `{"ok":false,"reason":…}`（枚举 `ExtractFailureReason`），
 * 不再静默返回空串；解析不出周次的条目依旧不入库。成功载荷 schema 不得改动。
 *
 * ## 同槽合并不在本文件
 * 见 `ImportWeekMerge`。主循环产出的多条安排在这里按
 * (课名, 星期, 节次, 教室, 教师) 合并成一条，`weeksRaw` 换成**并集规范串** ——
 * 真机第十四轮：教务把一节课按周次段拆成多段渲染，不合并就会出现重复卡片。
 *
 * ## 逐块证据 `evidence`（v2）
 * **每块一条**（不是每格一条）—— 本轮取证要看清的是**块边界**，
 * `rawBlock` 是该块原文（截断 160），配合 `blocksFound` / `multiBlockCells`
 * 一眼能看出"课有没有被切成两块"。
 * 只进 `diagnostics`（`ScheduleJsonCodec` 配 `ignoreUnknownKeys=true`，会被忽略），
 * **绝不进 `courses`**。上限 24 条，满则按先进先出丢弃最早的一条。
 *
 * ## 诚实声明（OD-005）—— 成功路径只在 Node 合成 DOM 上验证过，**不等于**
 * 校方页面就是这几种形态。
 * ========================================================================= */

internal object ImportExtractionScript {

    /**
     * 把课表表格映射为 schema JSON。
     *
     * 返回约定（调用方据此分流，见 `WebViewLoginScreen`）：
     *  - `{"courses":[…], "diagnostics":{…}}` —— 成功：**至少解析出 1 门课**，直接入库；
     *  - `{"ok":false,"reason":…}` —— 失败：**不再静默返回空串**，四种原因
     *    见 [ExtractFailureReason]，走诊断通道（不入库），UI 显示卡在哪一步。
     *  成功载荷的 schema **不得改动**（后端 `ScheduleJsonCodec` 依赖它）。
     */
    val SCRIPT: String = """
        ${ImportTableScan.JS}
        ${ImportCellParser.JS}
        ${ImportWeekMerge.JS}
        (function () {
          var KB = __kbTableScan;
          var CP = __kbCellParser;
          var WM = __kbWeekMerge;
          var squash = KB.squash, hw = KB.hw, cellsOf = KB.cellsOf;
          var blockText = KB.blockText, linesOf = KB.linesOf;
          var pickScheduleTable = KB.pickScheduleTable, weekdayColumns = KB.weekdayColumns;
          var columnShiftOf = KB.columnShiftOf, framesOf = KB.framesOf;
          var periodsFrom = CP.periodsFrom, timesFrom = CP.timesFrom;

          var EVIDENCE_LIMIT = 24;
          var EVIDENCE_BLOCK_RAW = 160;

          // 失败出口：全部带 ok:false + reason，替代原来的静默 return ''
          function fail(reason, extra) {
            var out = { ok: false, reason: reason };
            for (var k in extra) {
              if (Object.prototype.hasOwnProperty.call(extra, k)) { out[k] = extra[k]; }
            }
            return JSON.stringify(out);
          }

          // ---- 1) 选表：跨 iframe + 语义优先，与探针/取证同一份实现 ----
          var scored = pickScheduleTable(document);
          if (!scored.length) {
            var fr = framesOf(document), ifs = fr.slice(1), tCount = 0;
            fr.forEach(function (f) { if (f.doc) { try { tCount += f.doc.querySelectorAll('table').length; } catch (e) {} } });
            return fail('NO_TABLE', { tables: tCount, frames: ifs.length,
              framesAccessible: ifs.filter(function (f) { return f.accessible; }).length });
          }
          var chosen = scored[0];
          var rows = chosen.rows;
          if (chosen.headerIndex < 0) {
            return fail('NO_HEADER_ROW', {
              tables: scored.length, rows: rows.length,
              cells: chosen.cells, weekdays: chosen.weekdays
            });
          }
          var headerIndex = chosen.headerIndex;
          var mapped = weekdayColumns(rows[headerIndex]);
          var columnToWeekday = mapped.map;
          if (Object.keys(columnToWeekday).length < 2) {
            return fail('NO_WEEKDAY_COLUMNS', {
              headerText: squash(rows[headerIndex].textContent || '').slice(0, 120),
              headerCells: cellsOf(rows[headerIndex]).map(function (c) { return squash(c.textContent || ''); }),
              aggregatedColumns: mapped.skipped
            });
          }
          var columnShift = columnShiftOf(rows, headerIndex, columnToWeekday);

          var byCourse = {};
          var order = [];
          var cellsWithText = 0;
          var scanned = 0;
          var droppedNoWeeks = 0;
          var droppedNoName = 0;
          var sampleCellText = '';
          // 第六轮新增：整格被压成一行的格子数 / 因此被丢弃的格子数 / 逐块证据
          var blobCells = 0;
          var droppedLineBlob = 0;
          // 第十三轮新增：切块统计 —— 块数对不上就是"课被切坏了"的第一现场
          var blocksFound = 0;
          var multiBlockCells = 0;
          var evidence = [];

          for (var r = headerIndex + 1; r < rows.length; r++) {
            var cells = cellsOf(rows[r]);
            if (!cells.length) { continue; }
            var rowLabel = squash(blockText(cells[0]));
            var rowPeriod = periodsFrom(rowLabel);
            var rowTime = timesFrom(rowLabel);

            for (var col in columnToWeekday) {
              var cell = cells[parseInt(col, 10) - columnShift];
              if (!cell) { continue; }
              var cellText = hw(squash(blockText(cell)));
              var lines = linesOf(cell).map(hw);
              if (!lines.length) { continue; }
              cellsWithText++;
              if (!sampleCellText) { sampleCellText = cellText; }

              var p = CP.parseCell(cellText, lines, { rowPeriod: rowPeriod, rowTime: rowTime });
              if (p.blob) { blobCells++; }
              blocksFound += p.blocks;
              if (p.blocks >= 2) { multiBlockCells++; }

              // 逐块证据（v2）：每块一条，只进 diagnostics，绝不进 courses。满 24 条按先进先出。
              for (var bi = 0; bi < p.blockEvidence.length; bi++) {
                var be = p.blockEvidence[bi];
                if (evidence.length >= EVIDENCE_LIMIT) { evidence.shift(); }
                evidence.push({
                  row: r, day: columnToWeekday[col], block: be.block, blocks: be.blocks,
                  lines: be.lines, rawBlock: be.rawBlock.slice(0, EVIDENCE_BLOCK_RAW),
                  name: be.name, weeks: be.weeks, weekSource: be.weekSource,
                  room: be.room, teacher: be.teacher, drop: be.drop
                });
              }

              if (p.dropReason === 'NO_NAME') { droppedNoName++; continue; }
              if (p.dropReason === 'NO_WEEKS') {
                droppedNoWeeks++;
                if (p.blob) { droppedLineBlob++; }
                continue;
              }

              // 一格可以产多条（真机 P0-4：一格多个时间段块）
              for (var si = 0; si < p.sessions.length; si++) {
                var s = p.sessions[si];
                var key = s.name || p.name;
                if (!key) { continue; }
                scanned++;
                if (!byCourse[key]) { byCourse[key] = []; order.push(key); }
                byCourse[key].push({
                  weekday: columnToWeekday[col],
                  periodStart: s.period ? s.period.start : null,
                  periodEnd: s.period ? s.period.end : null,
                  startTime: s.time ? s.time.start : null,
                  endTime: s.time ? s.time.end : null,
                  campus: s.campus,
                  room: s.room,
                  teacher: s.teacher,
                  weeksRaw: s.weeksRaw
                });
              }
            }
          }

          if (!scanned) {
            return fail('NO_SESSIONS', {
              cellsWithText: cellsWithText, droppedNoWeeks: droppedNoWeeks,
              droppedNoName: droppedNoName, sampleCellText: sampleCellText.slice(0, 120),
              blobCells: blobCells, blocksFound: blocksFound, multiBlockCells: multiBlockCells
            });
          }

          // ---- 学期元数据：从被选中的 frame 文档里识别，否则用 Spec 默认值 ----
          var semester = {
            name: '2026-2027 学年第一学期', academicYear: '2026-2027',
            termIndex: 1, startDate: '2026-09-14', totalWeeks: 16
          };
          try {
            var bodyText = (chosen.doc && chosen.doc.body ? chosen.doc.body.innerText : '') || '';
            var nameMatch = bodyText.match(/\d{4}\s*[-—]\s*\d{4}\s*学年\s*.{0,8}学期/);
            if (nameMatch) {
              var found = nameMatch[0].replace(/\s+/g, '');
              semester.name = found;
              var years = found.match(/(\d{4})\s*[-—]\s*(\d{4})/);
              if (years) { semester.academicYear = years[1] + '-' + years[2]; }
              semester.termIndex = /第一学期/.test(found) ? 1 : (/第二学期/.test(found) ? 2 : (/短学期/.test(found) ? 3 : 1));
            }
          } catch (e) { /* 识别失败即保留默认值，不阻断 */ }

          // ---- 6) 同槽合并：教务会把一节课按周次段拆成多段渲染在同一格里 ----
          //  同一门课 + 同一星期 + 同一节次 + 同一教室 + 同一教师 = 一节课，
          //  周次取**并集**（见 ImportWeekMerge：展开 -> 求并 -> 压缩成规范串）。
          //  不合并的话：今天视图里同一张卡片会出现两次（真机第十四轮就是这么炸的），
          //  核对页还会刷出几十条「周次重叠」告警。
          var sessionsBefore = scanned;
          var sessionsAfter = 0;
          var mergedGroups = 0;
          var mergeUnresolved = 0;
          var mergedByCourse = {};
          for (var oi = 0; oi < order.length; oi++) {
            var courseKey = order[oi];
            var res = WM.mergeSessions(byCourse[courseKey]);
            mergedByCourse[courseKey] = res.sessions;
            sessionsAfter += res.sessions.length;
            mergedGroups += res.mergedGroups;
            mergeUnresolved += res.unresolved;
          }

          var courses = order.map(function (courseName) {
            var sessions = mergedByCourse[courseName];
            var noRoom = sessions.every(function (s) { return !s.room; });
            var online = noRoom && /线上|网络|慕课|尔雅|线上授课/.test(courseName);
            return {
              name: courseName, code: null, totalHours: null,
              isOnline: online, sessions: sessions
            };
          });

          return JSON.stringify({
            schemaVersion: 1,
            exportedAt: new Date().toISOString(),
            semester: semester,
            courses: courses,
            diagnostics: {
              rowCount: rows.length, cellsWithText: cellsWithText, scanned: scanned,
              droppedNoWeeks: droppedNoWeeks, droppedNoName: droppedNoName,
              columnShift: columnShift, aggregatedColumns: mapped.skipped,
              blobCells: blobCells, droppedLineBlob: droppedLineBlob,
              blocksFound: blocksFound, multiBlockCells: multiBlockCells,
              sessionsBefore: sessionsBefore, sessionsAfter: sessionsAfter,
              mergedGroups: mergedGroups, mergeUnresolved: mergeUnresolved,
              evidence: evidence
            }
          });
        })()
    """.trimIndent()
}
