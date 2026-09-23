"""桌面端课表导入脚本（Spec 第 12.1 节步骤 2；ADR-003 兜底路径 b）。

三种运行模式：

  1) --sample               从文档已公布的真实课表数据生成夹具 JSON。**无需登录、无需
                            网络、无需第三方依赖**，是本机今天就能实跑的模式，产出的
                            JSON 同时作为 App 解析器与 JVM 单测的夹具。
  2) --from-html <file>     解析一份已保存的课表 HTML（表格）-> JSON。用于教务改版时
                            先离线定位结构变化，再改 ScheduleHtmlParser。
  3) （无参数）              交互式：驱动 Edge 让真人完成 SSO 登录（含手机绑定 / 短信
                            验证码），登录后抓 iframe 内的课表表格。需要本机安装
                            playwright 与 msedge 通道。

诚实声明（不得含糊）：
  - 模式 3 在本机**未实际执行过**（需要真人登录凭据，且 playwright 未安装）。它的存在
    是路径 b 的可用性证明，不作为"已验证"结论。
  - 模式 1 的数据来源是 PRD 第 4.2 节 / Spec 第 2.1 节**已公布的**真实课表。文档未
    公布的字段（如导论课的星期与节次）在夹具中如实留空，并在 expected.unpublishedFields
    中逐条列出，不臆造。

用法：
    python tools/fetch_schedule.py --sample --out build/schedule.json
    python tools/fetch_schedule.py --from-html raw.html --out build/schedule.json
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone, timedelta
from html.parser import HTMLParser
from pathlib import Path

SCHEMA_VERSION = 1

# 学期元数据：Spec 第 12.2 节 / PRD 第 4 节（学期起始日 2026-09-14，第 1 周周一）
SEMESTER = {
    "name": "2026-2027 学年第一学期",
    "academicYear": "2026-2027",
    "termIndex": 1,
    "startDate": "2026-09-14",
    "totalWeeks": 16,
}

# 文档已公布的字段清单；未列出的字段在本夹具中一律 null，不臆造。
_UNPUBLISHED = [
    "自动化专业导论与职业生涯规划 的 weekday / periodStart / periodEnd / startTime / endTime",
    "大学物理实验B(1) 的 weekday / periodStart / periodEnd / startTime / endTime",
    "航空与航天 的 weekday / periodStart / periodEnd / startTime / endTime",
    "各课程的 course code（除 大学物理B(1)=053017P1-15）与 totalHours",
    "军事技能 的 weeksRaw（文档未公布周次；schemas 的 weeks_raw 为 NOT NULL，"
    "夹具以整学期 '1-16' 占位，导入核对视图应把该条标记为待人工确认）",
]


def _session(weeks_raw, room, teacher, weekday=None, ps=None, pe=None,
             start=None, end=None, campus="临港校区"):
    return {
        "weekday": weekday,
        "periodStart": ps,
        "periodEnd": pe,
        "startTime": start,
        "endTime": end,
        "campus": campus,
        "room": room,
        "teacher": teacher,
        "weeksRaw": weeks_raw,
    }


def build_sample() -> dict:
    """按 PRD 4.2 / Spec 2.1 已公布的真实数据构造夹具。

    数据形状覆盖 Spec 第 2.1 节全部五种反直觉形状：
      A 同一门课不同周换教室/教师   -> 大学物理B(1)、自动化专业导论与职业生涯规划
      B 每周实验室不同               -> 大学物理实验B(1)
      C 单双周规则                   -> 由 weeksRaw 承载，App 侧 WeekExpressionParser 解析
      D 纯线上课程                   -> 航空与航天
      E 无固定时间地点               -> 军事技能
    """
    courses = [
        {
            "name": "大学物理B(1)",
            "code": "053017P1-15",
            "totalHours": 48,
            "isOnline": False,
            "sessions": [
                _session("2", "E教305", "李彬彬", weekday=3, ps=1, pe=2,
                         start="08:10", end="09:40"),
                _session("3-16", "B105", "李彬彬", weekday=3, ps=1, pe=2,
                         start="08:10", end="09:40"),
            ],
        },
        {
            "name": "自动化专业导论与职业生涯规划",
            "code": None,
            "totalHours": None,
            "isOnline": False,
            "sessions": [
                _session("2", "D教203", "陈国初"),
                _session("3-4", "B203", "蒋璐峥"),
                _session("5,8", "B203", "陈国初"),
                _session("6-7", "B203", "于妍"),
            ],
        },
        {
            "name": "大学物理实验B(1)",
            "code": None,
            "totalHours": None,
            "isOnline": False,
            "sessions": [
                _session("9-11", "204(实验室2)", None),
                _session("12", "202(实验室1)", None),
                _session("13", "309(实验室11)", None),
                _session("14", "305(实验室7)", None),
                _session("15-16", "308(实验室10)", None),
            ],
        },
        {
            "name": "航空与航天",
            "code": None,
            "totalHours": None,
            "isOnline": True,
            "sessions": [_session("5-16", None, None)],
        },
        {
            "name": "军事技能",
            "code": None,
            "totalHours": None,
            "isOnline": False,
            "sessions": [
                {
                    "weekday": None, "periodStart": None, "periodEnd": None,
                    "startTime": None, "endTime": None, "campus": None,
                    "room": None, "teacher": None, "weeksRaw": "1-16",
                }
            ],
        },
    ]
    return {
        "schemaVersion": SCHEMA_VERSION,
        "exportedAt": _now_iso(),
        "source": "sample",
        "semester": dict(SEMESTER),
        "courses": courses,
        "expected": {
            "courseCount": len(courses),
            "note": (
                "documented-subset fixture：仅含 PRD 4.2 / Spec 2.1 已公布真实数据的课程。"
                "真实 SSO 抓取的完整课表应为 14 门课 + 2 门备注课（Spec 12.1），"
                "本夹具用于锁定已公布课程的关键不变量，不冒充完整课表。"
            ),
            "unpublishedFields": _UNPUBLISHED,
        },
    }


# ---------------------------------------------------------------------------
# 模式 2：离线解析已保存的课表 HTML 表格
# ---------------------------------------------------------------------------

class _TableGrabber(HTMLParser):
    """把 HTML 中的 <table> 抓成 row -> [cell 纯文本] 的二维结构。"""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.tables: list[list[list[str]]] = []
        self._depth = 0
        self._row: list[str] | None = None
        self._cell: list[str] | None = None

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            self._depth += 1
            if self._depth == 1:
                self.tables.append([])
        elif tag == "tr" and self._depth == 1:
            self._row = []
        elif tag in ("td", "th") and self._depth == 1:
            self._cell = []

    def handle_endtag(self, tag):
        if tag == "table":
            self._depth = max(0, self._depth - 1)
        elif tag == "tr" and self._depth == 1 and self._row is not None:
            self.tables[-1].append(self._row)
            self._row = None
        elif tag in ("td", "th") and self._depth == 1 and self._cell is not None:
            if self._row is not None:
                self._row.append("\n".join(
                    part.strip() for part in "".join(self._cell).splitlines() if part.strip()
                ))
            self._cell = None

    def handle_data(self, data):
        if self._depth == 1 and self._cell is not None:
            self._cell.append(data)


def parse_html(path: Path) -> dict:
    """把课表 HTML 转成中间表示。

    局限（必须知情）：真实教务表格的合并单元格 / 多行课程块布局尚未在真实页面上校准
    （OPEN-DECISIONS OD-005）。因此本函数只做**结构化抽取**，字段级的语义映射交由 App
    侧的 ScheduleHtmlParser（Spec 第 4.3 节：解析风险压缩在单一类里）。
    """
    grabber = _TableGrabber()
    grabber.feed(path.read_text(encoding="utf-8", errors="replace"))
    if not grabber.tables:
        raise SystemExit(
            "[FAIL] 未在 %s 中找到任何 <table>。教务改版或文件不是课表页，"
            "请以真实页面重新校准 ScheduleHtmlParser（OD-005）。" % path
        )
    table = max(grabber.tables, key=len)
    return {
        "schemaVersion": SCHEMA_VERSION,
        "exportedAt": _now_iso(),
        "source": "html",
        "semester": dict(SEMESTER),
        "courses": [],
        "rawTable": table,
        "expected": {
            "courseCount": None,
            "note": "原始表格已抽取，字段级映射需按真实页面校准后填入 courses。",
        },
    }


def _now_iso() -> str:
    return datetime.now(timezone(timedelta(hours=8))).isoformat(timespec="seconds")


def run_interactive(out: Path) -> int:
    """模式 3：驱动 Edge 完成真人 SSO 登录后抓取课表。本机未执行过。"""
    try:
        from playwright.sync_api import sync_playwright  # noqa: F401
    except ImportError:
        print("[FAIL] 未安装 playwright。交互模式需要：", file=sys.stderr)
        print("       pip install playwright && playwright install msedge", file=sys.stderr)
        print("       本机今天可跑的是 --sample 与 --from-html 两种模式。", file=sys.stderr)
        return 2

    from playwright.sync_api import sync_playwright  # noqa: PLC0415

    with sync_playwright() as pw:
        browser = pw.chromium.launch(channel="msedge", headless=False)
        page = browser.new_page()
        page.goto("https://jwgl.sdju.edu.cn/home", wait_until="domcontentloaded")
        print("请在浏览器中完成统一身份认证登录（含手机绑定 / 短信验证码），")
        print("进入「我的课表」页面后回到本终端按回车继续……")
        input()
        frames = [f for f in page.frames] or [page.main_frame]
        best = max(frames, key=lambda f: len(f.content()))
        out.parent.mkdir(parents=True, exist_ok=True)
        out.with_suffix(".html").write_text(best.content(), encoding="utf-8")
        print("已保存原始 HTML -> %s" % out.with_suffix(".html"))
        browser.close()
    print("[NOTE] 请用 --from-html 继续解析；本模式未在本机执行过，不构成已验证结论。")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="课刻 桌面端课表导入（ADR-003 兜底路径）")
    parser.add_argument("--sample", action="store_true", help="生成已公布真实数据的夹具 JSON")
    parser.add_argument("--from-html", type=Path, help="解析已保存的课表 HTML")
    parser.add_argument("--out", type=Path, default=Path("build/schedule.json"))
    args = parser.parse_args(argv)

    if args.sample:
        payload = build_sample()
    elif args.from_html:
        if not args.from_html.exists():
            print("[FAIL] 文件不存在: %s" % args.from_html, file=sys.stderr)
            return 2
        payload = parse_html(args.from_html)
    else:
        return run_interactive(args.out)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    counts = sum(len(c["sessions"]) for c in payload["courses"])
    print("已写入 %s" % args.out)
    print("  模式        : %s" % payload["source"])
    print("  课程数      : %d" % len(payload["courses"]))
    print("  ClassSession: %d" % counts)
    print("  学期起始日  : %s" % payload["semester"]["startDate"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
