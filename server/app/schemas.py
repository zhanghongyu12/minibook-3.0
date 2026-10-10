"""HTTP 契约（Pydantic DTO）——与 docs/14_cloud_sync.md §3.5 一一对应。

命名口径：信封字段 snake_case（entity_type / entity_id / ledger_id / updated_at），
`payload` 内是 Android Room 实体的**原样 camelCase 列**（少一层转换 = 少一类 bug）。
"""

from typing import Any

from pydantic import BaseModel, Field, field_validator

from .entity_spec import ENTITY_TYPES, PayloadError, assert_envelope_consistency, validate_payload
from .timeutil import parse_iso_utc

USERNAME_PATTERN = r"^[A-Za-z0-9_.-]{3,32}$"
MIN_PASSWORD_LENGTH = 8
MAX_PUSH_ITEMS = 1000


# ── 认证 ──────────────────────────────────────────────────────────────


class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=USERNAME_PATTERN)
    password: str = Field(min_length=MIN_PASSWORD_LENGTH, max_length=128)
    device_id: str | None = Field(default=None, max_length=36)
    device_name: str | None = Field(default=None, max_length=64)


class LoginRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32)
    password: str = Field(min_length=1, max_length=128)
    device_id: str | None = Field(default=None, max_length=36)
    device_name: str | None = Field(default=None, max_length=64)
    platform: str | None = Field(default=None, max_length=32)
    app_version: str | None = Field(default=None, max_length=32)


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=16, max_length=256)
    device_id: str | None = Field(default=None, max_length=36)


class LogoutRequest(BaseModel):
    refresh_token: str = Field(min_length=16, max_length=256)


class UserOut(BaseModel):
    user_id: str
    username: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    user: UserOut


# ── 同步 ──────────────────────────────────────────────────────────────


class SyncItem(BaseModel):
    entity_type: str
    entity_id: str = Field(min_length=1, max_length=64)
    ledger_id: str = Field(min_length=1, max_length=36)
    updated_at: str = Field(min_length=1, max_length=64)
    deleted: bool = False
    payload: dict[str, Any] | None = None

    @field_validator("entity_type")
    @classmethod
    def _known_entity(cls, value: str) -> str:
        if value not in ENTITY_TYPES:
            raise ValueError(f"未知实体类型: {value}")
        return value

    @field_validator("updated_at")
    @classmethod
    def _parseable_time(cls, value: str) -> str:
        try:
            parse_iso_utc(value)
        except ValueError as exc:  # 客户端时间格式错要早点拒，别写进库才发现
            raise ValueError(f"updated_at 不是合法 ISO 8601: {value!r}") from exc
        return value

    def validate_against_spec(self) -> None:
        """逐实体白名单 + 信封一致性（同步服务在条目下标上下文里调用）。"""
        if not self.deleted:
            validate_payload(self.entity_type, self.payload)
        elif self.payload is not None:
            # 墓碑也可带 payload（保留最后内容），仍须过白名单
            validate_payload(self.entity_type, self.payload)
        assert_envelope_consistency(
            entity_type=self.entity_type,
            entity_id=self.entity_id,
            ledger_id=self.ledger_id,
            updated_at=self.updated_at,
            payload=self.payload,
        )


class PushRequest(BaseModel):
    device_id: str | None = Field(default=None, max_length=36)
    items: list[SyncItem] = Field(default_factory=list, max_length=MAX_PUSH_ITEMS)


class ServerItem(BaseModel):
    entity_type: str
    entity_id: str
    ledger_id: str
    updated_at: str  # 服务端存的是客户端原始 ISO 串（回传即原样，避免格式漂移）
    deleted: bool
    payload: dict[str, Any] | None = None
    server_seq: int


class PushConflict(BaseModel):
    entity_type: str
    entity_id: str
    reason: str  # server_newer / tombstone_wins
    server: ServerItem


class PushResponse(BaseModel):
    applied: int
    skipped: int
    conflicts: list[PushConflict]
    server_seq: int


class PullResponse(BaseModel):
    items: list[ServerItem]
    next_seq: int
    has_more: bool


class StatusResponse(BaseModel):
    #: 本账号自己的最高 server_seq（= 拉完全部数据后客户端应达到的游标；无数据为 0）
    server_seq: int
    #: 服务端**全局**计数器值（跨账号共享，仅诊断用；客户端不要拿它当拉取游标）
    server_seq_global: int
    counts: dict[str, int]
    server_time: str


__all__ = [
    "ENTITY_TYPES",
    "LoginRequest",
    "LogoutRequest",
    "PayloadError",
    "PullResponse",
    "PushConflict",
    "PushRequest",
    "PushResponse",
    "RefreshRequest",
    "RegisterRequest",
    "ServerItem",
    "StatusResponse",
    "SyncItem",
    "TokenPair",
    "UserOut",
]
