"""统一错误体与错误码（见 docs/14_cloud_sync.md §3.5）。

响应形状恒为 `{"error": {"code": "...", "message": "...", ...}}`。
"""

from typing import Any

#: 错误码 → HTTP 状态码
ERROR_STATUS: dict[str, int] = {
    "invalid_credentials": 401,
    "username_taken": 409,
    "validation_error": 422,
    "token_expired": 401,
    "token_invalid": 401,
    "token_revoked": 401,
    "not_found": 404,
    "rate_limited": 429,
    "server_error": 500,
}


class ApiError(Exception):
    """业务错误：由 main.py 的异常处理器转成统一错误体。"""

    def __init__(
        self,
        code: str,
        message: str,
        status_code: int | None = None,
        extra: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code or ERROR_STATUS.get(code, 400)
        self.extra = extra or {}


def error_body(code: str, message: str, extra: dict[str, Any] | None = None) -> dict[str, Any]:
    body: dict[str, Any] = {"code": code, "message": message}
    if extra:
        body.update(extra)
    return {"error": body}
