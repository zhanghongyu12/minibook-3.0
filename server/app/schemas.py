"""HTTP 契约（Pydantic DTO）——与 docs/14_cloud_sync.md §3.5 一一对应。

命名口径：信封字段 snake_case（entity_type / entity_id / ledger_id / updated_at），
`payload` 内是 Android Room 实体的**原样 camelCase 列**（少一层转换 = 少一类 bug）。
"""

from datetime import datetime, timezone
import re
from typing import Any

from pydantic import BaseModel, Field, field_serializer, field_validator

from .entity_spec import ENTITY_TYPES, PayloadError, assert_envelope_consistency, validate_payload
from .timeutil import parse_iso_utc

USERNAME_PATTERN = r"^[A-Za-z0-9_.-]{3,32}$"
#: 宽松邮箱校验：只拦明显不是邮箱的输入（有 @、两侧非空、域名带点），不做完整 RFC 匹配。
EMAIL_PATTERN = r"^[^@\s]+@[^@\s]+\.[^@\s]+$"
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
    #: 显示名（用户可改；空 = 未设置 ⇒ 客户端回退显示 username）。2026-10-12 随「账号」页加入。
    nickname: str | None = None
    #: 资料补充（2026-10-12 第二轮）：均可空 = 未填写。
    email: str | None = None
    bio: str | None = None
    region: str | None = None
    created_at: datetime | None = None
    #: 最近一次**登录**时间（只看首发的刷新令牌，令牌轮换不算新登录）；仅 `GET /me` 返回。
    last_login_at: datetime | None = None

    @field_serializer("created_at", "last_login_at")
    def _iso_utc(self, value: datetime | None) -> str | None:
        """统一输出带 `Z` 的 UTC ISO 串。

        2026-10-12 真机缺陷：`UTCDateTime` 读出来是 **naive** UTC，序列化后不带时区，
        客户端 `Instant.parse` 直接解析失败 ⇒ 「注册时间」把 UTC 原样显示（差 8 小时且难看）。
        服务端把契约钉死为「带 Z 的 UTC」，客户端只负责转本地时区与格式化。
        """
        if value is None:
            return None
        if value.tzinfo is None:
            value = value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int
    user: UserOut


class ProfileUpdateRequest(BaseModel):
    """改资料（2026-10-12，第二轮扩充邮箱 / 个性签名 / 地区）：资料字段与口令**都可选**。

    口径：`model_fields_set` 判定「用户到底传了哪个字段」——传空串表示**清除**该字段，
    与「没传这个字段」是两件事，只看值会把两者混为一谈。
    口令变更**必须**同时给 `old_password`（服务端不信任已登录的设备），
    改完立即撤销该账号**全部**旧刷新令牌并向当前设备换发一对新令牌（见 `routers/auth.py`）。
    """

    nickname: str | None = Field(default=None, max_length=32)
    email: str | None = Field(default=None, max_length=254)
    bio: str | None = Field(default=None, max_length=80)
    region: str | None = Field(default=None, max_length=32)
    old_password: str | None = Field(default=None, max_length=128)
    new_password: str | None = Field(default=None, min_length=MIN_PASSWORD_LENGTH, max_length=128)

    @field_validator("nickname", "bio", "region")
    @classmethod
    def _clean_optional_text(cls, value: str | None) -> str | None:
        """空串 = 清除（归一为 None），顺手去首尾空白；超长由 `Field(max_length)` 兜底。"""
        if value is None:
            return None
        cleaned = value.strip()
        return cleaned or None

    @field_validator("email")
    @classmethod
    def _clean_email(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        if not cleaned:
            return None  # 空串 = 清除邮箱
        if not re.fullmatch(EMAIL_PATTERN, cleaned):
            raise ValueError("邮箱格式不正确")
        return cleaned


class ProfileUpdateResponse(BaseModel):
    user: UserOut
    #: 仅当本次改了口令时非空：旧刷新令牌已全部撤销，客户端必须用新令牌覆盖本地会话。
    tokens: TokenPair | None = None


class DeleteAccountRequest(BaseModel):
    """注销账号（不可撤销、连带删除云端全部数据）：需口令二次确认。"""

    password: str = Field(min_length=1, max_length=128)


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
