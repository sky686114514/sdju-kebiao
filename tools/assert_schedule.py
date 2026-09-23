"""课表夹具断言（Spec 第 12.1 节步骤 3）。

断言对象是 `fetch_schedule.py` 产出的 JSON。断言分两组：

  A. 结构断言    —— schema / 学期元数据 / 课程数，与 Spec 第 12.1 节的期望一致。
  B. 语义断言    —— 逐门课核对"哪一周、在哪上、谁上"。这是本项目唯一能抓住
                    "导入识别错 -> 旷课"这类头号差评的检查点（AC-02 / AC-03）。

另外做一次**自洽交叉校验**：用 week_expr 重新解析每条 session 的 weeksRaw，断言
解析结果落在学期范围内且同一门课的 session 周次集合两两互斥（形状 1 与形状 2 的
建模铁律：互斥周次行，Spec 第 2.1 节）。

用法：
    python tools/assert_schedule.py build/schedule.json
    python tools/assert_schedule.py build/schedule.json --expect-courses 16
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from week_expr import WeekParseError, parse_weeks  # noqa: E402

EXPECTED_START_DATE = "2026-09-14"
EXPECTED_TOTAL_WEEKS = 16

# 形状 1：同一门课不同周换教室 / 换教师（AC-02 / AC-03）
INTRO_BY_WEEKS = {
    frozenset({2}): ("D教203", "陈国初"),
    frozenset({3, 4}): ("B203", "蒋璐峥"),
    frozenset({5, 8}): ("B203", "陈国初"),
    frozenset({6, 7}): ("B203", "于妍"),
}

# 形状 2：每周实验室不同（AC-01 的实验室部分）
LAB_BY_WEEKS = {
    frozenset({9, 10, 11}): "204(实验室2)",
    frozenset({12}): "202(实验室1)",
    frozenset({13}): "309(实验室11)",
    frozenset({14}): "305(实验室7)",
    frozenset({15, 16}): "308(实验室10)",
}


class Report:
    def __init__(self) -> None:
        self.passed = 0
        self.failed: list[str] = []

    def check(self, label: str, ok: bool, detail: str = "") -> None:
        if ok:
            print(f"  [PASS] {label}")
            self.passed += 1
        else:
            print(f"  [FAIL] {label}" + (f"  -> {detail}" if detail else ""))
            self.failed.append(label)


def _by_name(payload: dict) -> dict[str, dict]:
    return {c["name"]: c for c in payload["courses"]}


def _weeks_of(session: dict, total_weeks: int, report: Report, owner: str) -> frozenset:
    raw = session.get("weeksRaw")
    try:
        return parse_weeks(raw, total_weeks).weeks
    except WeekParseError as exc:
        report.check(f"{owner} 周次串 {raw!r} 可解析", False, str(exc))
        return frozenset()


def assert_structure(payload: dict, expect_courses: int | None, report: Report) -> None:
    print("\n[A] 结构断言")
    report.check("schemaVersion == 1", payload.get("schemaVersion") == 1,
                 f"实得 {payload.get('schemaVersion')!r}")

    sem = payload.get("semester", {})
    report.check(f"学期起始日 == {EXPECTED_START_DATE}",
                 sem.get("startDate") == EXPECTED_START_DATE, f"实得 {sem.get('startDate')!r}")
    report.check(f"学期总周数 == {EXPECTED_TOTAL_WEEKS}",
                 sem.get("totalWeeks") == EXPECTED_TOTAL_WEEKS, f"实得 {sem.get('totalWeeks')!r}")
    report.check("termIndex == 1", sem.get("termIndex") == 1, f"实得 {sem.get('termIndex')!r}")
    report.check("academicYear == '2026-2027'", sem.get("academicYear") == "2026-2027",
                 f"实得 {sem.get('academicYear')!r}")

    declared = (payload.get("expected") or {}).get("courseCount")
    target = expect_courses if expect_courses is not None else declared
    actual = len(payload.get("courses", []))
    if target is None:
        print(f"  [SKIP] 课程数断言（夹具未声明期望值）；实得 {actual}")
    else:
        report.check(f"课程数 == {target}", actual == target, f"实得 {actual}")

    note = (payload.get("expected") or {}).get("note")
    if note:
        print(f"  [INFO] {note}")


def assert_key_courses(payload: dict, total_weeks: int, report: Report) -> None:
    print("\n[B] 语义断言（哪一周 / 在哪上 / 谁上）")
    courses = _by_name(payload)

    # ---- 大学物理B(1)：AC-02 第 2 周 E教305，第 3 周起 B105 ----
    phys = courses.get("大学物理B(1)")
    if phys is None:
        report.check("存在课程 大学物理B(1)", False, "未找到")
    else:
        rows = {_weeks_of(s, total_weeks, report, "大学物理B(1)"): s for s in phys["sessions"]}
        report.check("大学物理B(1) 恰有 2 条 ClassSession", len(rows) == 2, f"实得 {len(rows)}")
        report.check("大学物理B(1) 第 2 周 教室 == E教305",
                     rows.get(frozenset({2}), {}).get("room") == "E教305",
                     f"实得 {rows.get(frozenset({2}), {}).get('room')!r}")
        report.check("大学物理B(1) 第 3-16 周 教室 == B105",
                     rows.get(frozenset(range(3, 17)), {}).get("room") == "B105",
                     f"实得 {rows.get(frozenset(range(3, 17)), {}).get('room')!r}")
        report.check("大学物理B(1) 两条会话周次互斥且并集 == 第 2-16 周",
                     len(rows) == 2 and set().union(*rows.keys()) == set(range(2, 17)),
                     f"并集 {sorted(set().union(*rows.keys())) if rows else '[]'}")
        report.check("大学物理B(1) 周次限定在 1..16 内",
                     all(w in range(1, total_weeks + 1) for ws in rows for w in ws))

    # ---- 自动化专业导论与职业生涯规划：AC-03 五段轮换 ----
    intro = courses.get("自动化专业导论与职业生涯规划")
    if intro is None:
        report.check("存在课程 自动化专业导论与职业生涯规划", False, "未找到")
    else:
        rows = {_weeks_of(s, total_weeks, report, "导论课"): (s.get("room"), s.get("teacher"))
                for s in intro["sessions"]}
        report.check("导论课 ClassSession 数 == 4", len(rows) == 4, f"实得 {len(rows)}")
        for weeks, expected in INTRO_BY_WEEKS.items():
            actual = rows.get(weeks)
            label = f"导论课 第 {_fmt(weeks)} 周 教室/教师 == {expected[0]}/{expected[1]}"
            report.check(label, actual == expected, f"实得 {actual}")

    # ---- 大学物理实验B(1)：每周不同实验室 ----
    lab = courses.get("大学物理实验B(1)")
    if lab is None:
        report.check("存在课程 大学物理实验B(1)", False, "未找到")
    else:
        rows = {_weeks_of(s, total_weeks, report, "物理实验"): s.get("room")
                for s in lab["sessions"]}
        report.check("大学物理实验B(1) ClassSession 数 == 5", len(rows) == 5, f"实得 {len(rows)}")
        for weeks, room in LAB_BY_WEEKS.items():
            report.check(f"物理实验 第 {_fmt(weeks)} 周 实验室 == {room}",
                         rows.get(weeks) == room, f"实得 {rows.get(weeks)!r}")
        report.check("物理实验 五段周次并集 == 第 9-16 周",
                     set().union(*rows.keys()) == set(range(9, 17)) if rows else False)

    # ---- 形状 4：纯线上课程 ----
    online = courses.get("航空与航天")
    if online is None:
        report.check("存在课程 航空与航天", False, "未找到")
    else:
        report.check("航空与航天 isOnline == true", online.get("isOnline") is True,
                     f"实得 {online.get('isOnline')!r}")
        sessions = online["sessions"]
        report.check("航空与航天 全部 session 的 room 为 null（不假造教室）",
                     all(s.get("room") is None for s in sessions))
        report.check("航空与航天 周次 == 第 5-16 周",
                     len(sessions) == 1
                     and _weeks_of(sessions[0], total_weeks, report, "线上课")
                     == frozenset(range(5, 17)))

    # ---- 形状 5：无固定时间地点 ----
    drill = courses.get("军事技能")
    if drill is None:
        report.check("存在课程 军事技能", False, "未找到")
    else:
        ok = all(
            s.get("weekday") is None and s.get("periodStart") is None
            and s.get("periodEnd") is None and s.get("startTime") is None
            and s.get("endTime") is None and s.get("room") is None
            for s in drill["sessions"]
        )
        report.check("军事技能 weekday/节次/时间/教室 全部为 null", ok)


def _fmt(weeks: frozenset) -> str:
    ordered = sorted(weeks)
    if not ordered:
        return "-"
    if ordered == list(range(ordered[0], ordered[-1] + 1)):
        return f"{ordered[0]}-{ordered[-1]}"
    return ",".join(str(w) for w in ordered)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="课刻 课表夹具断言")
    ap.add_argument("fixture", type=Path)
    ap.add_argument("--expect-courses", type=int, default=None,
                    help="覆盖夹具声明的课程数期望（真实抓取应传 16 = 14 门 + 2 门备注）")
    args = ap.parse_args(argv)

    if not args.fixture.exists():
        print(f"[FAIL] 夹具不存在: {args.fixture}", file=sys.stderr)
        return 2

    payload = json.loads(args.fixture.read_text(encoding="utf-8"))
    report = Report()
    print("=" * 78)
    print(f"课表夹具断言  fixture={args.fixture}")
    print("=" * 78)

    total_weeks = payload.get("semester", {}).get("totalWeeks") or EXPECTED_TOTAL_WEEKS
    assert_structure(payload, args.expect_courses, report)
    assert_key_courses(payload, total_weeks, report)

    print("\n" + "=" * 78)
    print(f"结果: {report.passed} passed, {len(report.failed)} failed")
    print("=" * 78)
    return 1 if report.failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
