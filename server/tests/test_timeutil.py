"""时间解析：Android `Instant.now().toString()` 是**纳秒 + Z**，必须能安全吃下。"""

from datetime import datetime, timezone

from app.timeutil import parse_iso_utc, to_iso_utc, utcnow


def test_android_nanosecond_iso_is_truncated_to_microseconds():
    parsed = parse_iso_utc("2026-10-10T06:25:01.123456789Z")
    assert parsed == datetime(2026, 10, 10, 6, 25, 1, 123456)
    assert parsed.tzinfo is None  # 统一 UTC naive


def test_offset_is_normalized_to_utc_naive():
    parsed = parse_iso_utc("2026-10-10T14:25:01.500000+08:00")
    assert parsed == datetime(2026, 10, 10, 6, 25, 1, 500000)


def test_naive_input_is_treated_as_utc():
    assert parse_iso_utc("2026-10-10T06:25:01") == datetime(2026, 10, 10, 6, 25, 1)


def test_round_trip_to_iso_utc():
    parsed = parse_iso_utc("2026-10-10T06:25:01.123456789Z")
    assert to_iso_utc(parsed) == "2026-10-10T06:25:01.123456Z"


def test_utcnow_is_naive_utc_close_to_now():
    now = utcnow()
    assert now.tzinfo is None
    delta = abs((datetime.now(timezone.utc).replace(tzinfo=None) - now).total_seconds())
    assert delta < 5
