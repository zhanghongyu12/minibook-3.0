"""ORM 模型（云端 = 副本，权威定义见 docs/14_cloud_sync.md §3.3）。

MySQL 特有口径（DEC-031，不写清必踩）：
- 索引列必须显式长度（VARCHAR(n)），复合主键总字节数远低于 InnoDB 3072B 上限；
- `payload` 用 JSON 类型（MySQL→JSON，SQLite→TEXT，可移植）；
- 时间列用 `UTCDateTime`：MySQL 侧 DATETIME(6)，否则毫秒被截断 ⇒「同 updated_at = no-op」判定失真；
- 表选项 InnoDB + utf8mb4（`sync_counters` 的行锁依赖 InnoDB）。
"""

from datetime import datetime

from sqlalchemy import (
    JSON,
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    PrimaryKeyConstraint,
    String,
    UniqueConstraint,
)
from sqlalchemy.dialects import mysql
from sqlalchemy.orm import Mapped, mapped_column

from .db import Base

#: MySQL 用 DATETIME(6)（保留微秒），SQLite 用默认 DateTime（其实现本就带小数秒）
UTCDateTime = DateTime().with_variant(mysql.DATETIME(fsp=6), "mysql")

#: MySQL 表参数：InnoDB + utf8mb4 / utf8mb4_unicode_ci。
#: **必须显式写 collate**——MySQL 8 对 `CHARSET=utf8mb4` 默认给 `utf8mb4_0900_ai_ci`，
#: 与部署文档「collation-server = utf8mb4_unicode_ci」口径不一致（实测 2026-10-10 内网 MySQL 8.0.46）。
_MYSQL_TABLE_ARGS = {
    "mysql_engine": "InnoDB",
    "mysql_charset": "utf8mb4",
    "mysql_collate": "utf8mb4_unicode_ci",
}


def _utc_now() -> datetime:
    """UTC naive（不依赖服务器时区）。"""
    from datetime import timezone

    return datetime.now(timezone.utc).replace(tzinfo=None)


class User(Base):
    __tablename__ = "users"
    __table_args__ = _MYSQL_TABLE_ARGS

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    # 用户名统一小写存储（DEC-031：MySQL 排序规则大小写不敏感、SQLite 敏感，必须两端一致）
    username: Mapped[str] = mapped_column(String(32), nullable=False, unique=True)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)
    disabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"
    __table_args__ = (
        Index("ix_refresh_tokens_user", "user_id"),
        Index("ix_refresh_tokens_hash", "token_hash"),
        _MYSQL_TABLE_ARGS,
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    token_hash: Mapped[str] = mapped_column(String(128), nullable=False)  # 只存 sha256，不存原文
    issued_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)
    expires_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(UTCDateTime, nullable=True)
    device_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    rotated_from: Mapped[str | None] = mapped_column(String(36), nullable=True)


class Device(Base):
    __tablename__ = "devices"
    __table_args__ = (
        UniqueConstraint("user_id", "id", name="uq_devices_user_id"),
        _MYSQL_TABLE_ARGS,
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    platform: Mapped[str | None] = mapped_column(String(32), nullable=True)
    app_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    last_seen_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class SyncRecord(Base):
    """通用同步记录：一行 = 一个实体在当前时点的云端副本（DEC-033）。

    为什么通用而不是 6 张类型化表：客户端 schema 会持续演进，类型化表会把「加一列」
    变成「改服务端 + 迁移 + 契约」三件事；服务端只是副本、不做 join/聚合。
    校验不放松——字段白名单在 `entity_spec.py`，逐条 Pydantic 校验。
    """

    __tablename__ = "sync_records"
    __table_args__ = (
        PrimaryKeyConstraint("user_id", "entity_type", "entity_id", name="pk_sync_records"),
        Index("ix_sync_records_user_seq", "user_id", "server_seq"),
        Index("ix_sync_records_user_ledger", "user_id", "ledger_id"),
        _MYSQL_TABLE_ARGS,
    )

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    entity_type: Mapped[str] = mapped_column(String(32), nullable=False)
    entity_id: Mapped[str] = mapped_column(String(64), nullable=False)
    ledger_id: Mapped[str] = mapped_column(String(36), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)  # 客户端时间（UTC naive），LWW 依据
    #: 客户端传来的**原始 ISO 串**（如 `2026-10-10T06:25:01.123456789Z`）——回传/比对都用它，
    #: 避免「服务端归一化格式 vs 客户端原串」导致客户端误判冲突（`sync_state.base_updated_at` 依赖精确相等）
    updated_at_raw: Mapped[str] = mapped_column(String(64), nullable=False)
    deleted: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)  # 墓碑：不物理删
    payload: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    server_seq: Mapped[int] = mapped_column(BigInteger, nullable=False)  # 拉取游标
    server_updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class SyncCounter(Base):
    """全局单调 `server_seq` 生成器。

    实现为**原子自增**：`UPDATE sync_counters SET value = value + 1 WHERE name = 'server_seq'`
    后读回（rowcount 必须为 1），不依赖行锁语义，SQLite/MySQL 通用（见 `14_cloud_sync.md §12.2`）。
    """

    __tablename__ = "sync_counters"
    __table_args__ = _MYSQL_TABLE_ARGS

    name: Mapped[str] = mapped_column(String(32), primary_key=True)
    value: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    # 保留整数类型引用，避免 lint 报未使用（后续如需按用户分片计数会用到）
    shard: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
