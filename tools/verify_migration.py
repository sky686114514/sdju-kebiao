#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Room 迁移静态验证（v1 -> v2）。

为什么需要它：
    本项目没有真机/模拟器环境，用不了 Room 的 `MigrationTestHelper`（它需要仪器化测试宿主）。
    但"迁移后 schema 是否与 Room 期望的一致"这个最易出错的环节，可以在
    **纯 Python + 真实 SQLite 引擎**上钉死：

      1. 用 v1 schema JSON 的 createSql 建出真实的 v1 库（表 + 索引 + room_master_table）；
      2. 施加 **从 Migrations.kt 源码里正则抽出来的** MIGRATION_1_2 SQL
         —— 不是抄一份常量，抽的是真实产物，避免"测试跑的是副本"这种假验证；
      3. 把迁移后的库结构与 v2 schema JSON 描述的期望结构
         **逐表 / 逐列 / 逐索引 / 逐外键**比对；
      4. 行为验证：索引确实被查询规划器使用（v1 全表扫描 vs v2 走索引），
         且外键 ON DELETE SET NULL 语义未被迁移破坏。

它**不能**替代真机上的迁移实测（没有跑过 Android 的 RoomOpenHelper），
但能把 schema 等价性这一环从"靠肉眼看"变成"可自动复现"。

用法：
    python tools/verify_migration.py
需要先至少跑一次 `:app:kspDebugKotlin`（或 assembleDebug）生成 v2 的 schema JSON。
"""

from __future__ import annotations

import json
import re
import sqlite3
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent
SCHEMA_DIR = ROOT / "app" / "schemas" / "com.kebiao.app.data.local.KebiaoDatabase"
MIGRATIONS_KT = (
    ROOT / "app" / "src" / "main" / "java" / "com" / "kebiao" / "app"
    / "data" / "local" / "Migrations.kt"
)
DB_KT = (
    ROOT / "app" / "src" / "main" / "java" / "com" / "kebiao" / "app"
    / "data" / "local" / "KebiaoDatabase.kt"
)

_passed = 0
_failed = 0
_lines: list[str] = []


def emit(s: str = "") -> None:
    """同时打到 stdout 与报告文件（stdout 在 Windows 控制台可能被转码，报告文件是权威）。"""
    _lines.append(s)
    try:
        print(s)
    except Exception:  # noqa: BLE001 - 控制台编码问题不影响报告
        pass


def check(cond: bool, label: str, detail: str = "") -> bool:
    global _passed, _failed
    if cond:
        _passed += 1
        emit(f"  PASS  {label}")
    else:
        _failed += 1
        emit(f"  FAIL  {label}" + (f"  ::  {detail}" if detail else ""))
    return bool(cond)


def load_schema(version: int) -> dict:
    cands = sorted(SCHEMA_DIR.glob("*.json"))
    if not cands:
        raise SystemExit(f"[FATAL] {SCHEMA_DIR} 下没有 schema JSON，请先跑一次 :app:kspDebugKotlin")
    for f in cands:
        data = json.loads(f.read_text(encoding="utf-8"))
        if data["database"]["version"] == version:
            return data["database"]
    raise SystemExit(f"[FATAL] 找不到 version={version} 的 schema JSON（现有：{[f.name for f in cands]}）")


def extract_migration_sql() -> str:
    """从 Migrations.kt 的 MIGRATION_1_2 **声明体**里抽出 execSQL 的 SQL 字面量。

    锚点必须落在真正的声明 `MIGRATION_1_2 = object : Migration(1, 2)`，
    不能落在 KDoc 里对 [MIGRATION_1_2] 的引用（否则会抓到模板注释里的例子）。
    结束边界取 `val ALL_MIGRATIONS`，避免窗口溢出到后面的 ForeignKeysCallback。
    """
    src = MIGRATIONS_KT.read_text(encoding="utf-8")
    anchor = re.search(r"MIGRATION_1_2\s*=\s*object\s*:\s*Migration\(1,\s*2\)", src)
    if not anchor:
        raise SystemExit("[FATAL] Migrations.kt 里找不到 `MIGRATION_1_2 = object : Migration(1, 2)` 声明")
    end = src.find("val ALL_MIGRATIONS", anchor.end())
    body = src[anchor.end(): end if end > 0 else anchor.end() + 3000]
    found = re.findall(r'execSQL\(\s*"([^"]*)"\s*\)', body)
    if not found:
        raise SystemExit("[FATAL] MIGRATION_1_2 声明体内没有找到 execSQL(\"...\")")
    if len(found) != 1:
        raise SystemExit(f"[FATAL] MIGRATION_1_2 体内有 {len(found)} 条 execSQL，期望恰好 1 条：{found}")
    return found[0]


def check_db_version_const() -> int:
    src = DB_KT.read_text(encoding="utf-8")
    m = re.search(r"const\s+val\s+VERSION\s*:\s*Int\s*=\s*(\d+)", src)
    if not m:
        raise SystemExit("[FATAL] KebiaoDatabase.kt 里找不到 VERSION 常量")
    return int(m.group(1))


def build_v1(con: sqlite3.Connection, v1: dict) -> None:
    con.execute("PRAGMA foreign_keys = ON")
    for ent in v1["entities"]:
        t = ent["tableName"]
        con.execute(ent["createSql"].replace("${TABLE_NAME}", t))
        for ix in ent.get("indices", []):
            con.execute(ix["createSql"].replace("${TABLE_NAME}", t))
    for q in v1["setupQueries"]:
        con.execute(q)
    con.commit()


def snapshot(con: sqlite3.Connection) -> dict:
    """读回真实库结构。"""
    tables = [
        r[0] for r in con.execute(
            "SELECT name FROM sqlite_master WHERE type='table' "
            "AND name NOT LIKE 'sqlite_%' AND name <> 'room_master_table' ORDER BY name"
        )
    ]
    out = {}
    for t in tables:
        cols = [
            {
                "name": r[1],
                "type": (r[2] or "").upper(),
                "notnull": int(r[3]),
                "dflt": None if r[4] is None else str(r[4]).strip(),
                "pk": int(r[5]),
            }
            for r in con.execute(f"PRAGMA table_info(`{t}`)")
        ]
        indices = []
        for r in con.execute(f"PRAGMA index_list(`{t}`)"):
            name, unique, origin = r[1], r[2], r[3]
            if origin != "c":  # 只看显式 CREATE INDEX（Room 的索引均为 'c'）
                continue
            columns = [rr[2] for rr in con.execute(f"PRAGMA index_info(`{name}`)")]
            indices.append({"name": name, "unique": bool(unique), "columns": columns})
        fks = [
            {"table": r[2], "to": r[4], "on_delete": r[6]}
            for r in con.execute(f"PRAGMA foreign_key_list(`{t}`)")
        ]
        out[t] = {
            "columns": cols,
            "indices": sorted(indices, key=lambda x: x["name"]),
            "fks": sorted(fks, key=lambda x: (x["table"], x["to"], x["on_delete"])),
        }
    return out


def expected_from(v: dict) -> dict:
    out = {}
    for ent in v["entities"]:
        t = ent["tableName"]
        cols = [
            {
                "name": f["columnName"],
                "type": f["affinity"].upper(),
                # 注意：Room 对可空列会**省略** notNull 键（不是 false），故必须用 get
                "notnull": 1 if f.get("notNull") else 0,
                "dflt": None if f.get("defaultValue") is None else str(f["defaultValue"]).strip(),
                "pk": 1 if f["columnName"] in ent["primaryKey"]["columnNames"] else 0,
            }
            for f in ent["fields"]
        ]
        indices = [
            {"name": i["name"], "unique": bool(i["unique"]), "columns": list(i["columnNames"])}
            for i in ent.get("indices", [])
        ]
        fks = [
            {"table": fk["table"], "to": fk["referencedColumns"][0], "on_delete": fk["onDelete"]}
            for fk in ent.get("foreignKeys", [])
        ]
        out[t] = {
            "columns": cols,
            "indices": sorted(indices, key=lambda x: x["name"]),
            "fks": sorted(fks, key=lambda x: (x["table"], x["to"], x["on_delete"])),
        }
    return out


def compare_shape(actual: dict, want: dict, label: str) -> None:
    check(sorted(actual) == sorted(want), f"{label}: 表集合一致",
          f"actual={sorted(actual)} want={sorted(want)}")
    for t in sorted(set(actual) & set(want)):
        a, w = actual[t], want[t]

        # 列
        check([c["name"] for c in a["columns"]] == [c["name"] for c in w["columns"]],
              f"{label}: {t} 列名与顺序")
        for ca, cw in zip(a["columns"], w["columns"]):
            check(ca == cw, f"{label}: {t}.{cw['name']} 列属性", f"actual={ca} want={cw}")

        # 索引
        check([i["name"] for i in a["indices"]] == [i["name"] for i in w["indices"]],
              f"{label}: {t} 索引集合",
              f"actual={[i['name'] for i in a['indices']]} want={[i['name'] for i in w['indices']]}")
        for ia, iw in zip(a["indices"], w["indices"]):
            check(ia == iw, f"{label}: {t} 索引 {iw['name']} 定义", f"actual={ia} want={iw}")

        # 外键
        check(a["fks"] == w["fks"], f"{label}: {t} 外键定义", f"actual={a['fks']} want={w['fks']}")


def query_plan(con: sqlite3.Connection, sql: str) -> str:
    rows = list(con.execute("EXPLAIN QUERY PLAN " + sql))
    return " | ".join(str(r[-1]) for r in rows)


def main() -> int:
    emit("=" * 78)
    emit("Room 迁移验证：v1 -> v2（真实 SQLite 引擎，SQL 抽自 Migrations.kt 源码）")
    emit("=" * 78)

    v1 = load_schema(1)
    v2 = load_schema(2)
    mig_sql = extract_migration_sql()
    version_const = check_db_version_const()

    emit(f"\n[抽取到的 MIGRATION_1_2 SQL]\n    {mig_sql}")
    emit(f"[KebiaoDatabase.VERSION 常量]\n    {version_const}")

    # ---- A. 版本与迁移 SQL 的基本形状 ----
    emit("\n[A] 版本 / 迁移 SQL 形状")
    check(v1["version"] == 1, "1.json 的 version == 1")
    check(v2["version"] == 2, "2.json 的 version == 2")
    check(version_const == 2, "KebiaoDatabase.VERSION == 2", f"actual={version_const}")
    check(v2["version"] == version_const,
          "schema JSON 版本与 VERSION 常量一致", f"json={v2['version']} const={version_const}")
    check(mig_sql.strip().upper().startswith("CREATE INDEX"), "迁移 SQL 是 CREATE INDEX")
    check("IF NOT EXISTS" in mig_sql.upper(), "迁移 SQL 幂等（IF NOT EXISTS）")
    check("idx_sessions_batch" in mig_sql, "迁移 SQL 目标索引名 idx_sessions_batch")
    check("import_batch_id" in mig_sql, "迁移 SQL 目标列 import_batch_id")

    # ---- B. 1.json 与 2.json 的差异必须"仅此一处" ----
    emit("\n[B] 1.json 与 2.json 的差异面（必须只有新增索引）")
    e1, e2 = expected_from(v1), expected_from(v2)
    check(sorted(e1) == sorted(e2), "v1/v2 表集合相同")
    for t in sorted(e1):
        check([c["name"] for c in e1[t]["columns"]] == [c["name"] for c in e2[t]["columns"]],
              f"v1/v2 {t} 列结构未变")
        check(e1[t]["columns"] == e2[t]["columns"], f"v1/v2 {t} 列属性未变")
        check(e1[t]["fks"] == e2[t]["fks"], f"v1/v2 {t} 外键未变")
    added = {
        t: [i["name"] for i in e2[t]["indices"] if i["name"] not in [x["name"] for x in e1[t]["indices"]]]
        for t in sorted(e1)
    }
    removed = {
        t: [i["name"] for i in e1[t]["indices"] if i["name"] not in [x["name"] for x in e2[t]["indices"]]]
        for t in sorted(e1)
    }
    check(all(not v for v in removed.values()), "无索引被移除", f"removed={removed}")
    check(added == {"semesters": [], "courses": [], "class_sessions": ["idx_sessions_batch"],
                    "import_batches": []},
          "新增索引恰好只有 class_sessions.idx_sessions_batch", f"added={added}")

    # ---- C. 施加迁移并比对 schema ----
    emit("\n[C] 施加 MIGRATION_1_2 后，与 2.json 期望结构逐项比对")
    con = sqlite3.connect(":memory:")
    build_v1(con, v1)
    before = snapshot(con)
    compare_shape(before, e1, "v1(迁移前)")

    con.execute(mig_sql)
    after = snapshot(con)
    compare_shape(after, e2, "v2(迁移后)")

    # ---- D. 行为验证 ----
    emit("\n[D] 行为验证")
    con_v1 = sqlite3.connect(":memory:")
    build_v1(con_v1, v1)
    plan_v1 = query_plan(con_v1, "SELECT * FROM class_sessions WHERE import_batch_id = 1")
    plan_v2 = query_plan(con, "SELECT * FROM class_sessions WHERE import_batch_id = 1")
    emit(f"    v1 查询计划: {plan_v1}")
    emit(f"    v2 查询计划: {plan_v2}")
    check("idx_sessions_batch" not in plan_v1, "迁移前：该查询不走 idx_sessions_batch")
    check("idx_sessions_batch" in plan_v2, "迁移后：该查询确实使用 idx_sessions_batch")

    # 外键 SET NULL 语义未被破坏
    try:
        con.execute("INSERT INTO semesters (id, name, academic_year, term_index, start_date, "
                    "total_weeks, is_current, created_at, updated_at) "
                    "VALUES (1,'S','2026-2027',1,'2026-09-01',16,1,0,0)")
        con.execute("INSERT INTO courses (id, semester_id, name, is_online, created_at, updated_at) "
                    "VALUES (1,1,'C',0,0,0)")
        con.execute("INSERT INTO import_batches (id, semester_id, source, status, started_at) "
                    "VALUES (1,1,0,0,0)")
        con.execute("INSERT INTO class_sessions (id, course_id, weeks_raw, week_numbers, source, "
                    "import_batch_id, created_at, updated_at) VALUES (1,1,'1-16',',1,',0,1,0,0)")
        con.execute("DELETE FROM import_batches WHERE id = 1")
        con.commit()
        row = con.execute("SELECT import_batch_id FROM class_sessions WHERE id = 1").fetchone()
        check(row is not None and row[0] is None,
              "迁移后外键 ON DELETE SET NULL 语义仍生效", f"import_batch_id={row}")
    except sqlite3.Error as exc:
        check(False, "迁移后外键 SET NULL 语义仍生效", f"SQLite 错误: {exc}")

    emit("\n" + "=" * 78)
    emit(f"RESULT: passed={_passed} failed={_failed}")
    emit("=" * 78)
    emit("NOTE: 本脚本验证的是 schema 等价性与 SQLite 层面行为；")
    emit("      它未在 Android 运行时（RoomOpenHelper）上执行过迁移。")
    return 1 if _failed else 0


if __name__ == "__main__":
    code = 1
    try:
        code = main()
    except SystemExit as exc:
        emit(f"[FATAL] {exc}")
    except Exception as exc:  # noqa: BLE001 - 兜底，保证报告一定落盘
        emit(f"[FATAL] 未预期异常: {type(exc).__name__}: {exc}")
    finally:
        # 报告落盘（UTF-8），绕开 Windows 控制台编码把中文转码搞乱的问题
        report = ROOT / "output" / "migration_verify_report.txt"
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text("\n".join(_lines) + "\n", encoding="utf-8")
    raise SystemExit(code)
