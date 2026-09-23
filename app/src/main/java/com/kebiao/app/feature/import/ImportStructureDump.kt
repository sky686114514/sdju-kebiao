package com.kebiao.app.feature.import

/* =========================================================================
 * 课表结构导出（诊断专用，供真机取证）
 *
 * ## 为什么从"采样"改成"全量矩阵"
 * 采样失效了：真实 DOM 回传的 dump 里 `samples: []` —— 探针挑中的那张表压根
 * 一个像课程的格子都没有（后来查明是选表规则按"格子最多"选中的月历条）。
 * 靠启发式挑"文本最长的格子"再猜第三种 DOM 写法，已经猜过三轮（`<br>` 分隔、
 * 整行锚定周次、行首取节次），**全部是假设**。现在直接把整张矩阵交出来。
 *
 * ## 输出两块
 *  1. `candidates` —— 所有候选表的摘要（行数/格子数/星期列数/是否被选中/首行文本），
 *     用来回答"探针为什么挑了这张表"；
 *  2. `matrix` —— 被选中那张表的**行 × 列文本矩阵**，每格 40 字、整体 6000 字预算，
 *     超出从**尾部**丢行（表头在最前面，必须保住）。
 *
 * ## 隐私纪律（硬，违反即退回）
 *  - 完整文本**只给被选中的那一张 `<table>`**（候补最多泄露自己的首行 80 字）；
 *  - **不含 URL**（连 query 都不含，避免把会话参数带出去）；
 *  - 不碰表单控件、不读 cookie/localStorage。
 * 这份文本是要被用户**复制出去**的，隐私优先级高于信息量 —— 6000 字预算就是这条纪律的边界。
 * ========================================================================= */

internal object ImportStructureDump {

    /** 单格文本截断长度：40 字足以看清"这是不是课程块"，又不至于搬走一整个课程块。 */
    private const val CELL_LIMIT = 40

    /** 矩阵总字符预算：超出就从**尾部**丢行（表头在前面，必须保住）。 */
    private const val MATRIX_LIMIT = 6000

    /** 候选表摘要上限：通常末尾几张是噪声小表，12 张足够回答"为什么挑了它"。 */
    private const val CANDIDATE_LIMIT = 12

    val SCRIPT: String = """
        ${ImportTableScan.JS}
        (function () {
          var CELL_LIMIT = $CELL_LIMIT;
          var MATRIX_LIMIT = $MATRIX_LIMIT;
          var CANDIDATE_LIMIT = $CANDIDATE_LIMIT;
          var KB = __kbTableScan;
          var squash = KB.squash, cellsOf = KB.cellsOf, blockText = KB.blockText;
          var pickScheduleTable = KB.pickScheduleTable, weekdayColumns = KB.weekdayColumns;
          var columnShiftOf = KB.columnShiftOf;

          function all(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }
          function cut(s, n) { return String(s).length > n ? String(s).slice(0, n) + '...' : String(s); }
          // 一个课程块有多行（课名/周次/教室/教师），用 " / " 连起来塞进一个格子
          function cellText(cell) {
            return squash(blockText(cell).split(/\r?\n/).map(squash)
              .filter(function (x) { return x.length > 0; }).join(' / '));
          }

          // 选表跨 iframe + 语义优先，与**抽取完全相同**的实现
          var scored = pickScheduleTable(document);
          if (!scored.length) { return JSON.stringify({ error: '页面上没有含 3 行以上的表格' }); }

          var candidates = scored.slice(0, CANDIDATE_LIMIT).map(function (s, i) {
            return {
              index: i,
              selected: i === 0,
              rows: s.rows.length,
              cells: s.cells,
              weekdayColumns: s.weekdays,
              headerIndex: s.headerIndex,
              firstRow: cut(cellsOf(s.rows[0]).map(cellText).join(' | '), 80),
              frameSrc: s.src
            };
          });

          var chosen = scored[0];
          var rows = chosen.rows;
          var headerIndex = chosen.headerIndex;
          var mapped = headerIndex >= 0 ? weekdayColumns(rows[headerIndex]) : { map: {}, skipped: [] };
          var columnToWeekday = mapped.map;

          var matrix = [];
          var used = 0;
          var truncated = false;
          rows.forEach(function (r) {
            if (truncated) { return; }
            var line = [];
            var rowUsed = 0;
            cellsOf(r).forEach(function (c) {
              var t = cut(cellText(c), CELL_LIMIT);
              line.push(t);
              rowUsed += t.length + 1;
            });
            if (used + rowUsed > MATRIX_LIMIT) { truncated = true; return; }
            used += rowUsed;
            matrix.push(line);
          });

          return JSON.stringify({
            rowCount: rows.length,
            headerIndex: headerIndex,
            cellsPerRow: rows.map(function (r) { return cellsOf(r).length; }),
            weekdayColumnMap: columnToWeekday,
            aggregatedColumns: mapped.skipped,
            columnShift: headerIndex >= 0 ? columnShiftOf(rows, headerIndex, columnToWeekday) : 0,
            frameSrc: chosen.src,
            matrix: matrix,
            matrixChars: used,
            matrixTruncated: truncated,
            candidateCount: scored.length,
            candidates: candidates
          });
        })()
    """.trimIndent()
}
