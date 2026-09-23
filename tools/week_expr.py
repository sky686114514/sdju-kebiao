"""周次表达式解析器参考实现（Python 镜像）。

本文件是 Kotlin 侧 ``WeekExpressionParser`` 的逐行镜像实现，用于在 Android 工具链
就绪之前先把算法正确性钉死（ARCHITECTURE.md 第 6.4 节 / 第 14.4 节）。

对照关系（改这里必须同步改 Kotlin，反之亦然）：
    app/src/main/java/com/kebiao/app/data/import/parser/WeekExpressionParser.kt

设计纪律（Spec 第 11 节内嵌已知坑）：解析失败必须显式抛错，禁止静默返回空集。
竞品"课路识别错导致连续几天没课"的根因就是解析失败被上层 catch 吞成空集合。
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum


class WeekParseErrorCode(str, Enum):
    EMPTY = "EMPTY_WEEKS"
    MALFORMED_TOKEN = "MALFORMED_TOKEN"
    REVERSED_RANGE = "REVERSED_RANGE"
    OUT_OF_SEMESTER_RANGE = "OUT_OF_SEMESTER_RANGE"


class WeekParseError(ValueError):
    """周次串无法解析。携带机器可读的 code，供导入核对视图分类展示。"""

    def __init__(self, code: WeekParseErrorCode, raw: str, detail: str) -> None:
        super().__init__(f"[{code.value}] raw={raw!r} detail={detail}")
        self.code = code
        self.raw = raw
        self.detail = detail


class Parity(str, Enum):
    ALL = "ALL"
    ODD = "ODD"
    EVEN = "EVEN"


@dataclass(frozen=True)
class ParsedWeeks:
    weeks: frozenset[int]
    parity: Parity
    raw: str
    total_weeks_inferred: bool


# 归一化：全角 -> 半角。教务系统实际输出的分隔符形态不统一，这里一次性收敛。
_FULLWIDTH_DIGITS = str.maketrans("０１２３４５６７８９", "0123456789")
_HYPHEN_LIKE = ("－", "—", "–", "~", "～", "至", "到")
_STRIP_NON_ESSENTIAL = re.compile(r"[^0-9,\-]")


def _normalize(raw: str) -> str:
    text = raw.strip().translate(_FULLWIDTH_DIGITS)
    text = text.replace("，", ",")
    for ch in _HYPHEN_LIKE:
        text = text.replace(ch, "-")
    return _STRIP_NON_ESSENTIAL.sub("", text)


def detect_parity(raw: str) -> Parity:
    """判定单双周。

    顺序有意为之：先认 "单双周"（语义是"每周"），再认 "单"，最后认 "双"。
    若把 "单" 放在最前，教务系统偶尔输出的 "单双周" 会被误判成单周 —— 这是一处
    典型的沉默逻辑错误，故显式列出。
    """
    if "单双周" in raw or "每周" in raw:
        return Parity.ALL
    if "单" in raw:
        return Parity.ODD
    if "双" in raw:
        return Parity.EVEN
    return Parity.ALL


def _apply_parity(weeks: set[int], parity: Parity) -> set[int]:
    if parity is Parity.ALL:
        return weeks
    if parity is Parity.ODD:
        return {w for w in weeks if w % 2 == 1}
    return {w for w in weeks if w % 2 == 0}


def _expand_tokens(cleaned: str, raw: str) -> set[int]:
    weeks: set[int] = set()
    for token in cleaned.split(","):
        if not token:
            continue
        parts = [p for p in token.split("-") if p]
        if len(parts) == 1:
            weeks.add(int(parts[0]))
        elif len(parts) == 2:
            start, end = int(parts[0]), int(parts[1])
            if start > end:
                raise WeekParseError(
                    WeekParseErrorCode.REVERSED_RANGE,
                    raw,
                    f"区间起止倒置: {start}-{end}",
                )
            weeks.update(range(start, end + 1))
        else:
            raise WeekParseError(
                WeekParseErrorCode.MALFORMED_TOKEN,
                raw,
                f"无法识别的片段: {token!r}",
            )
    return weeks


def parse_weeks(raw: str, total_weeks: int | None = None) -> ParsedWeeks:
    """把原始周次串解析为规范化周次集合。

    total_weeks 为 None 时（教务系统未给学期总周数），以 max(weeks) 作为下界推导，
    并在返回值上打 total_weeks_inferred=True 标记，供导入核对视图记录差异
    （ARCHITECTURE.md 第 6.4 节边界用例 9：推导而非丢弃）。
    """
    cleaned = _normalize(raw)
    if not cleaned:
        raise WeekParseError(WeekParseErrorCode.EMPTY, raw, "归一化后为空，无任何周次数字")

    parity = detect_parity(raw)
    weeks = _expand_tokens(cleaned, raw)
    if not weeks:
        raise WeekParseError(WeekParseErrorCode.EMPTY, raw, "未解析出任何周次")

    inferred = total_weeks is None
    effective_total = max(weeks) if inferred else total_weeks

    filtered = _apply_parity(weeks, parity)
    clamped = {w for w in filtered if 1 <= w <= effective_total}
    if not clamped:
        raise WeekParseError(
            WeekParseErrorCode.OUT_OF_SEMESTER_RANGE,
            raw,
            f"单双周过滤与夹逼后为空 (totalWeeks={effective_total})",
        )
    return ParsedWeeks(frozenset(clamped), parity, raw, inferred)
