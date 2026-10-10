"""ISO 8601 时间处理。

客户端时间来自 Android `TimeFormatter.nowIso()` = `Instant.now().toString()`，
形如 `2026-10-10T06:25:01.123456789Z`（**纳秒 + Z**）。Python `datetime.fromisoformat`
只稳定支持 6 位小数，故先截断小数位再解析——不处理会直接抛异常/精度错乱。
"""

import re
from datetime import datetime, timezone

MAX_FRACTION_DIGITS = 6

_FRACTION = re.compile(r"\.(\d+)")


def parse_iso_utc(value: str | datetime) -> datetime:
    """解析 ISO 8601（容忍 'Z'、纳秒小数位、带偏移）→ **UTC naive**。"""
    if isinstance(value, datetime):
        dt = value
    else:
        text = value.strip()
        if text.endswith(("Z", "z")):
            text = text[:-1] + "+00:00"
        match = _FRACTION.search(text)
        if match and len(match.group(1)) > MAX_FRACTION_DIGITS:
            text = text[: match.start(1)] + match.group(1)[:MAX_FRACTION_DIGITS] + text[match.end(1) :]
        dt = datetime.fromisoformat(text)

    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt


def to_iso_utc(dt: datetime | None) -> str | None:
    """UTC naive/aware → `...Z` 形式 ISO 8601 字符串。"""
    if dt is None:
        return None
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt.isoformat(timespec="microseconds") + "Z"


def utcnow() -> datetime:
    """当前 UTC naive 时间（与时间列口径一致）。"""
    return datetime.now(timezone.utc).replace(tzinfo=None)
