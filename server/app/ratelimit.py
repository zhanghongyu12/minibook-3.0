"""极简内存限流（登录/注册）：滑动窗口，单进程有效。

不引第三方依赖——本服务是单进程 uvicorn，够用；多进程/多实例时需换 Redis（已登记为二期）。
"""

import threading
import time
from collections import deque

from .errors import ApiError

WINDOW_SECONDS = 60.0

_hits: dict[str, deque[float]] = {}
_lock = threading.Lock()


def check_rate_limit(key: str, limit_per_minute: int) -> None:
    if limit_per_minute <= 0:
        return
    now = time.monotonic()
    with _lock:
        bucket = _hits.setdefault(key, deque())
        while bucket and now - bucket[0] > WINDOW_SECONDS:
            bucket.popleft()
        if len(bucket) >= limit_per_minute:
            raise ApiError("rate_limited", "操作过于频繁，请稍后再试")
        bucket.append(now)


def reset() -> None:
    """测试用：清空计数。"""
    with _lock:
        _hits.clear()
