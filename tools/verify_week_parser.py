"""周次解析器交叉验证（Spec 第 12.1 节步骤 1，本机今天可真实运行）。

验证手段有三层，逐层加大力度：

  第 1 层  规范期望比对 —— Spec 第 2.1 节 / ARCHITECTURE 第 6.4 节列出的全部真实
           周次串，逐一比对解析结果与规范写死的期望集合。
  第 2 层  失败路径断言 —— 非法串必须显式抛错。**断言"不返回空集"是本文件的
           核心价值**：静默返回空集正是竞品"连续几天没课"的根因。
  第 3 层  独立实现交叉验证（结构不同的第二个实现 + 随机夹具）—— 见下方 oracle。
           本层专治沉默逻辑错误（off-by-one / 去重漏做 / 上溢夹逼方向写反）：
           主实现用"建集合"路线，oracle 用"逐周覆盖判定"路线，两条路线对同一输入
           必须给出完全相同的集合。

运行：
    python tools/verify_week_parser.py
退出码 0 = 全绿；非 0 = 存在失败用例。
"""

from __future__ import annotations

import random
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from week_expr import (  # noqa: E402
    Parity,
    WeekParseError,
    WeekParseErrorCode,
    parse_weeks,
)

TOTAL_WEEKS = 16  # 2026-2027 学年第 1 学期（学期起始日 2026-09-14，Spec 第 12.2 节）

# (原始串, 期望集合)。期望值来源：ARCHITECTURE.md 第 6.4 节边界用例 + PRD 第 4.2 节真实数据。
EXPECTED: list[tuple[str, set[int]]] = [
    ("2-16 双周", set(range(2, 17, 2))),               # 真实：单双周规则
    ("3-15 单周", set(range(3, 16, 2))),               # 真实：单双周规则
    ("9-16", set(range(9, 17))),                       # 真实：每周
    ("1-16", set(range(1, 17))),                       # 全周
    ("第1-16周", set(range(1, 17))),                    # 带"第…周"修饰
    ("2,3-4,5,8,6-7", {2, 3, 4, 5, 6, 7, 8}),          # 乱序 + 重叠区间 -> 并集去重
    ("1-20", set(range(1, 17))),                       # 上溢夹逼到 totalWeeks
    ("5-16", set(range(5, 17))),                       # 真实：尔雅线上课《航空与航天》
    ("3-16", set(range(3, 17))),                       # 真实：大学物理B(1) 第 3 周起
    ("9-11", {9, 10, 11}),                             # 真实：大学物理实验B(1) 204(实验室2)
    ("12", {12}),                                      # 真实：大学物理实验B(1) 202(实验室1)
    ("13", {13}),                                      # 真实：大学物理实验B(1) 309(实验室11)
    ("14", {14}),                                      # 真实：大学物理实验B(1) 305(实验室7)
    ("15-16", {15, 16}),                               # 真实：大学物理实验B(1) 308(实验室10)
    ("2", {2}),                                        # 真实：大学物理B(1) 第 2 周 E教305
    ("3-4", {3, 4}),                                   # 真实：导论课 蒋璐峥
    ("5,8", {5, 8}),                                   # 真实：导论课 陈国初（不连续）
    ("6-7", {6, 7}),                                   # 真实：导论课 于妍
    ("1-16 单周", set(range(1, 17, 2))),                # 单周全学期
    ("１-８", {1, 2, 3, 4, 5, 6, 7, 8}),               # 全角数字
    ("２，３-４", {2, 3, 4}),                           # 全角逗号 + 全角数字
    ("2～6", {2, 3, 4, 5, 6}),                         # 全角波浪号当区间分隔
    ("1-16 每周", set(range(1, 17))),                  # 显式"每周"
    ("1-16 单双周", set(range(1, 17))),                 # "单双周"= 每周，不得误判为单周

    # 教务新版 SPA 形态：周次可能被括号包裹（前端同步）。
    # 与 Kotlin `WeekExpressionParserTest` 的同批用例逐条对应 —— 镜像脚本不是交付产物，
    # 两侧必须各自验过，不能只信一侧。
    ("(2-16)(双周)", set(range(2, 17, 2))),             # 括号包裹 + 双周
    ("(2-8周)", set(range(2, 9))),                      # 括号包裹，无单双
    ("3-15周(单)", set(range(3, 16, 2))),               # 单周写在括号里，不得丢失
    ("2,4,6周", {2, 4, 6}),                             # 枚举周次
    ("1-16周", set(range(1, 17))),                      # 老形态保持不变
]

# 非法串 -> 期望的错误码。断言"抛错"，绝不允许静默返回空集。
MUST_FAIL: list[tuple[str, WeekParseErrorCode]] = [
    ("", WeekParseErrorCode.EMPTY),
    ("   ", WeekParseErrorCode.EMPTY),
    ("周", WeekParseErrorCode.EMPTY),
    (",", WeekParseErrorCode.EMPTY),
    ("16-2", WeekParseErrorCode.REVERSED_RANGE),
    ("5-1,9-10", WeekParseErrorCode.REVERSED_RANGE),
    ("1-2-3", WeekParseErrorCode.MALFORMED_TOKEN),
    ("-", WeekParseErrorCode.MALFORMED_TOKEN),
    ("17-20", WeekParseErrorCode.OUT_OF_SEMESTER_RANGE),   # 全部越界，不得静默成空集
    ("0", WeekParseErrorCode.OUT_OF_SEMESTER_RANGE),       # 第 0 周不存在
    ("17-19 单周", WeekParseErrorCode.OUT_OF_SEMESTER_RANGE),
    # 纵深防御：即便前端的前置守卫失效，这两类噪声也必须显式报错，
    # 不得被吞成空集，更不得歪成一个荒谬的周次。
    ("(2026-2027-1)-533008G1-19", WeekParseErrorCode.MALFORMED_TOKEN),  # 课程号
    ("105", WeekParseErrorCode.OUT_OF_SEMESTER_RANGE),     # 门牌号：不得变成第 105 周
]


# ---------------------------------------------------------------------------
# 第 3 层：独立实现 oracle（结构上与 week_expr 不同）——"逐周覆盖判定"
# ---------------------------------------------------------------------------

_TOKEN = re.compile(r"(\d+)(?:\s*-\s*(\d+))?")


def oracle_numeric_weeks(raw: str, total_weeks: int) -> set[int]:
    """独立实现：不谈"集合运算"，直接逐周问"这一周被任何一个 token 覆盖吗"。

    仅支持纯数字表达式（fuzz 夹具不生成单双周字样），与主实现互为对照。
    返回 None 表示本 oracle 认为输入非法（倒置区间）。
    """
    tokens: list[tuple[int, int]] = []
    for m in _TOKEN.finditer(raw.replace("，", ",")):
        start = int(m.group(1))
        end = int(m.group(2)) if m.group(2) is not None else start
        if start > end:
            return None  # type: ignore[return-value]
        tokens.append((start, end))

    covered: set[int] = set()
    for week in range(1, total_weeks + 1):
        for start, end in tokens:
            if start <= week <= end:
                covered.add(week)
                break
    return covered


def fuzz_against_oracle(rounds: int, seed: int) -> tuple[int, list[str]]:
    """随机夹具交叉验证：主实现 vs oracle，逐例要求集合完全相等。"""
    rng = random.Random(seed)
    failures: list[str] = []
    executed = 0
    for _ in range(rounds):
        parts: list[str] = []
        for _ in range(rng.randint(1, 5)):
            if rng.random() < 0.5:
                parts.append(str(rng.randint(1, 20)))
            else:
                a = rng.randint(1, 20)
                b = rng.randint(a, 20)
                parts.append(f"{a}-{b}")
        raw = ",".join(parts)
        expected = oracle_numeric_weeks(raw, TOTAL_WEEKS)
        if expected is None:
            continue
        executed += 1
        try:
            actual = set(parse_weeks(raw, TOTAL_WEEKS).weeks)
        except WeekParseError as exc:
            if not expected:
                continue  # 双方法一致认为"全越界" -> 主实现抛错，可接受
            failures.append(f"raw={raw!r} 主实现抛 {exc}，oracle 期望 {sorted(expected)}")
            continue
        if actual != expected:
            failures.append(
                f"raw={raw!r} 主实现={sorted(actual)} oracle={sorted(expected)}"
            )
    return executed, failures


def _render(weeks: set[int]) -> str:
    if not weeks:
        return "{}"
    ordered = sorted(weeks)
    if ordered == list(range(ordered[0], ordered[-1] + 1)):
        return "{" + f"{ordered[0]}..{ordered[-1]}" + "}"
    return "{" + ",".join(str(w) for w in ordered) + "}"


def main() -> int:
    passed = 0
    failed = 0
    print("=" * 78)
    print("周次解析器交叉验证  totalWeeks=%d" % TOTAL_WEEKS)
    print("=" * 78)

    print("\n[第 1 层] 规范期望比对（Spec 2.1 / ARCHITECTURE 6.4）")
    for raw, expected in EXPECTED:
        try:
            got = set(parse_weeks(raw, TOTAL_WEEKS).weeks)
        except WeekParseError as exc:
            print(f"  [FAIL] {raw!r:<16} 抛异常: {exc}")
            failed += 1
            continue
        if got == expected:
            print(f"  [PASS] {raw!r:<16} -> {_render(got)}")
            passed += 1
        else:
            print(f"  [FAIL] {raw!r:<16} 期望 {_render(expected)} 实得 {_render(got)}")
            failed += 1

    print("\n[第 2 层] 失败路径断言（必须显式抛错，禁止静默返回空集）")
    for raw, code in MUST_FAIL:
        try:
            got = set(parse_weeks(raw, TOTAL_WEEKS).weeks)
        except WeekParseError as exc:
            if exc.code is code:
                print(f"  [PASS] {raw!r:<16} -> 抛 {exc.code.value}")
                passed += 1
            else:
                print(f"  [FAIL] {raw!r:<16} 期望 {code.value} 实得 {exc.code.value}")
                failed += 1
            continue
        print(f"  [FAIL] {raw!r:<16} 未抛错，静默返回 {_render(got)} —— 沉默逻辑错误")
        failed += 1

    print("\n[第 2b 层] 单双周隔离配对断言（防止奇偶过滤器恒真/取反）")
    odd = set(parse_weeks("3-15 单周", TOTAL_WEEKS).weeks)
    even = set(parse_weeks("2-16 双周", TOTAL_WEEKS).weeks)
    checks = [
        ("单周集合全部为奇数", all(w % 2 == 1 for w in odd)),
        ("双周集合全部为偶数", all(w % 2 == 0 for w in even)),
        ("单双周集合互斥", odd.isdisjoint(even)),
        ("单周不含第 4 周", 4 not in odd),
        ("双周不含第 5 周", 5 not in even),
    ]
    for label, ok in checks:
        print(f"  [{'PASS' if ok else 'FAIL'}] {label}")
        passed += 1 if ok else 0
        failed += 0 if ok else 1

    print("\n[第 3 层] 独立实现交叉验证（oracle = 逐周覆盖判定，seed=20260920）")
    executed, mismatches = fuzz_against_oracle(rounds=4000, seed=20260920)
    if mismatches:
        for line in mismatches[:10]:
            print(f"  [FAIL] {line}")
        failed += len(mismatches)
    else:
        passed += 1
    print(f"  [{'PASS' if not mismatches else 'FAIL'}] 随机夹具 {executed} 例，"
          f"主实现与 oracle 集合完全一致；不一致 {len(mismatches)} 例")

    print("\n[第 3b 层] 缺 totalWeeks 时的下界推导（ARCHITECTURE 6.4 边界用例 9）")
    inferred = parse_weeks("3-16", None)
    ok = inferred.weeks == frozenset(range(3, 17)) and inferred.total_weeks_inferred
    print(f"  [{'PASS' if ok else 'FAIL'}] '3-16' totalWeeks=None -> "
          f"{_render(set(inferred.weeks))} inferred={inferred.total_weeks_inferred}")
    passed += 1 if ok else 0
    failed += 0 if ok else 1

    print("\n" + "=" * 78)
    print(f"结果: {passed} passed, {failed} failed")
    print("=" * 78)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
