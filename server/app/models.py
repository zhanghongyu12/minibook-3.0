"""ORM 模型（云端 = 副本，权威定义见 docs/14_cloud_sync.md §3.3）。

存储口径（**DEC-037 起**）：云端**不**再用「一张通用表 + JSON payload」，而是六张与本地
Room 实体同构的**业务表**（ledgers / categories / transactions / debts / budgets /
user_category_rules），外加一张只记变更序号的 `sync_log`。理由：云端要能直接用 SQL 查账
（`SELECT sum(amount) … WHERE date BETWEEN …`），JSON blob 做不到；线协议不变，客户端仍发
`payload`，服务端拆列入库、拉取时按列拼回（`services/entity_store.py`）。

六张业务表的统一口径：
- 主键一律 `(user_id, id)`：`id` 是客户端 UUID，带 `user_id` 后**在表结构层面按账号隔离**，
  两个账号即便撞同一个 UUID 也不会互相覆盖；
- `ledger_id` 是账本维度（账本表里等于自身 `id`），用于筛选与将来的按账本聚合；
  **不建**业务表之间的外键——同步到达顺序不保证，外键会让乱序批次直接写不进去；
- `created_at_raw` / `updated_at_raw` 存客户端**原始 ISO 串**（逐字回显；客户端
  `sync_state.base_updated_at` 依赖精确相等）；`updated_at` 另存规范化 DATETIME(6)，
  只用于服务端 LWW 比较（MySQL 不给 fsp=6 会截断毫秒 ⇒「同版本重复推送」判定失真）；
- `server_seq` = 该行最后一次变更的全局序号（与 `sync_log` 同口径）；
- **墓碑不落业务表**：删除 = 物理删行 + `sync_log` 追加 `deleted=1`，业务表里只有活数据。

MySQL 特有口径（DEC-031，不写清必踩）：
- 索引列必须显式长度（VARCHAR(n)），复合主键总字节数远低于 InnoDB 3072B 上限；
- 时间列用 `UTCDateTime`：MySQL 侧 DATETIME(6)，否则毫秒被截断；
- 表选项 InnoDB + utf8mb4（`sync_counters` 的行锁依赖 InnoDB）。
"""

from datetime import datetime

from sqlalchemy import (
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

#: 客户端实体 ID 列宽（信封允许 `entity_id` 最长 64）
ID_LEN = 64
#: 客户端原始时间串列宽（如 `2026-10-10T06:25:01.123456789Z` = 30 字符）
RAW_TIME_LEN = 64
#: `createdAt` 原始串列宽
RAW_CREATED_LEN = 40


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
    # 显示名（用户可改，可为空 ⇒ 客户端回退显示 username）。2026-10-12 随「账号」页加入。
    nickname: Mapped[str | None] = mapped_column(String(32), nullable=True)
    # 资料补充（2026-10-12 第二轮）：邮箱 / 个性签名 / 地区。三列均可空 = 未填写；
    # 空串由 schema 归一为 NULL（表示「清除」），库里不留空字符串。
    email: Mapped[str | None] = mapped_column(String(254), nullable=True)
    bio: Mapped[str | None] = mapped_column(String(80), nullable=True)
    region: Mapped[str | None] = mapped_column(String(32), nullable=True)
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


# ── 业务表（云端副本，与本地 Room 实体同构；DEC-037）──────────────────────


class Ledger(Base):
    """账本（Room `ledgers`）。"""

    __tablename__ = "ledgers"
    __table_args__ = _MYSQL_TABLE_ARGS

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    id: Mapped[str] = mapped_column(String(ID_LEN), primary_key=True)
    ledger_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)  # 账本表里 = id
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    icon: Mapped[str] = mapped_column(String(64), nullable=False)
    color: Mapped[str] = mapped_column(String(32), nullable=False)
    is_default: Mapped[bool] = mapped_column(Boolean, nullable=False)
    position: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at_raw: Mapped[str] = mapped_column(String(RAW_CREATED_LEN), nullable=False)
    updated_at_raw: Mapped[str] = mapped_column(String(RAW_TIME_LEN), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)
    server_seq: Mapped[int] = mapped_column(BigInteger, nullable=False)
    server_updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class Category(Base):
    """分类（Room `categories`）。`order` 是 MySQL 保留字 ⇒ 列名用 `sort_order`。"""

    __tablename__ = "categories"
    __table_args__ = (
        Index("ix_categories_user_ledger", "user_id", "ledger_id"),
        _MYSQL_TABLE_ARGS,
    )

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    id: Mapped[str] = mapped_column(String(ID_LEN), primary_key=True)
    ledger_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    name: Mapped[str] = mapped_column(String(128), nullable=False)
    icon: Mapped[str] = mapped_column(String(64), nullable=False)
    color: Mapped[str] = mapped_column(String(32), nullable=False)
    type: Mapped[str] = mapped_column(String(16), nullable=False)
    is_built_in: Mapped[bool] = mapped_column(Boolean, nullable=False)
    sort_order: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at_raw: Mapped[str] = mapped_column(String(RAW_CREATED_LEN), nullable=False)
    updated_at_raw: Mapped[str] = mapped_column(String(RAW_TIME_LEN), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)
    server_seq: Mapped[int] = mapped_column(BigInteger, nullable=False)
    server_updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class Transaction(Base):
    """交易/账单（Room `transactions`）——云端账目分析的主表。"""

    __tablename__ = "transactions"
    __table_args__ = (
        Index("ix_transactions_user_ledger_date", "user_id", "ledger_id", "date"),
        Index("ix_transactions_user_ledger_type", "user_id", "ledger_id", "type"),
        Index("ix_transactions_user_category", "user_id", "category_id"),
        _MYSQL_TABLE_ARGS,
    )

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    id: Mapped[str] = mapped_column(String(ID_LEN), primary_key=True)
    ledger_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    type: Mapped[str] = mapped_column(String(16), nullable=False)  # income / expense
    amount: Mapped[int] = mapped_column(BigInteger, nullable=False)  # 分（整数，绝不用浮点）
    category_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    description: Mapped[str | None] = mapped_column(String(512), nullable=True)
    date: Mapped[str] = mapped_column(String(10), nullable=False)  # yyyy-MM-dd（ISO 串，可直接比较/排序）
    sync_status: Mapped[str] = mapped_column(String(16), nullable=False)
    external_id: Mapped[str | None] = mapped_column(String(191), nullable=True)  # 外部来源去重 ID
    counterparty: Mapped[str | None] = mapped_column(String(128), nullable=True)
    payment_method: Mapped[str | None] = mapped_column(String(64), nullable=True)
    source: Mapped[str] = mapped_column(String(16), nullable=False)
    #: 账单转债务关联（DEC-056）：指向 debts.id；NULL=未转。**不建外键**——
    #: 债务可能先被删（客户端随即把该列清回 NULL），外键会引入跨设备删除顺序依赖。
    debt_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    created_at_raw: Mapped[str] = mapped_column(String(RAW_CREATED_LEN), nullable=False)
    updated_at_raw: Mapped[str] = mapped_column(String(RAW_TIME_LEN), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)
    server_seq: Mapped[int] = mapped_column(BigInteger, nullable=False)
    server_updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class Debt(Base):
    """借还款（Room `debts`）。"""

    __tablename__ = "debts"
    __table_args__ = (
        Index("ix_debts_user_ledger_date", "user_id", "ledger_id", "date"),
        Index("ix_debts_user_ledger_party", "user_id", "ledger_id", "party_name"),
        _MYSQL_TABLE_ARGS,
    )

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    id: Mapped[str] = mapped_column(String(ID_LEN), primary_key=True)
    ledger_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    party_name: Mapped[str] = mapped_column(String(128), nullable=False)
    amount: Mapped[int] = mapped_column(BigInteger, nullable=False)
    kind: Mapped[str] = mapped_column(String(16), nullable=False)
    direction: Mapped[str] = mapped_column(String(16), nullable=False)
    date: Mapped[str] = mapped_column(String(10), nullable=False)
    remark: Mapped[str | None] = mapped_column(String(512), nullable=True)
    sync_status: Mapped[str] = mapped_column(String(16), nullable=False)
    created_at_raw: Mapped[str] = mapped_column(String(RAW_CREATED_LEN), nullable=False)
    updated_at_raw: Mapped[str] = mapped_column(String(RAW_TIME_LEN), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)
    server_seq: Mapped[int] = mapped_column(BigInteger, nullable=False)
    server_updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class Budget(Base):
    """预算（Room `budgets`）。"""

    __tablename__ = "budgets"
    __table_args__ = (
        Index("ix_budgets_user_ledger_month", "user_id", "ledger_id", "month"),
        _MYSQL_TABLE_ARGS,
    )

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    id: Mapped[str] = mapped_column(String(ID_LEN), primary_key=True)
    ledger_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    month: Mapped[str] = mapped_column(String(7), nullable=False)  # yyyy-MM
    category_id: Mapped[str | None] = mapped_column(String(ID_LEN), nullable=True)  # 空 = 当月总预算
    amount: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_at_raw: Mapped[str] = mapped_column(String(RAW_CREATED_LEN), nullable=False)
    updated_at_raw: Mapped[str] = mapped_column(String(RAW_TIME_LEN), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)
    server_seq: Mapped[int] = mapped_column(BigInteger, nullable=False)
    server_updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class UserCategoryRule(Base):
    """自动归类规则（Room `user_category_rules`）。"""

    __tablename__ = "user_category_rules"
    __table_args__ = (
        Index("ix_user_category_rules_user_ledger", "user_id", "ledger_id"),
        _MYSQL_TABLE_ARGS,
    )

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    id: Mapped[str] = mapped_column(String(ID_LEN), primary_key=True)
    ledger_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    source: Mapped[str] = mapped_column(String(16), nullable=False)
    keyword: Mapped[str] = mapped_column(String(128), nullable=False)
    category_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    created_at_raw: Mapped[str] = mapped_column(String(RAW_CREATED_LEN), nullable=False)
    updated_at_raw: Mapped[str] = mapped_column(String(RAW_TIME_LEN), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)
    server_seq: Mapped[int] = mapped_column(BigInteger, nullable=False)
    server_updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class SyncLog(Base):
    """变更日志：拉取游标 + 墓碑的唯一来源。

    为什么单独一张表，而不是给六张业务表都加 `deleted`/`server_seq` 索引去 union：
    1. 拉取是「按账号、按序号增量」的**单一有序流**——一张 append-only 表天然有序（主键
       `(user_id, server_seq)` 就是 InnoDB 聚簇索引，扫它不需要额外排序）；
    2. 墓碑必须有地方活：业务表只留活数据 ⇒ 删除只在这里留一条 `deleted=1`，
       将来若同一 id 被重新创建，靠序号先后就能正确表达「删过又建」；
    3. 六张表 union + 各自排序在分页边界上极易出错（跨表 limit 的游标语义）。
    """

    __tablename__ = "sync_log"
    __table_args__ = (
        PrimaryKeyConstraint("user_id", "server_seq", name="pk_sync_log"),
        Index("ix_sync_log_user_entity", "user_id", "entity_type", "entity_id", "server_seq"),
        _MYSQL_TABLE_ARGS,
    )

    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    server_seq: Mapped[int] = mapped_column(BigInteger, nullable=False)
    entity_type: Mapped[str] = mapped_column(String(32), nullable=False)
    entity_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    ledger_id: Mapped[str] = mapped_column(String(ID_LEN), nullable=False)
    deleted: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    updated_at_raw: Mapped[str] = mapped_column(String(RAW_TIME_LEN), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False)  # 规范化，LWW/比较用
    logged_at: Mapped[datetime] = mapped_column(UTCDateTime, nullable=False, default=_utc_now)


class SyncCounter(Base):
    """全局单调 `server_seq` 生成器。

    实现为**原子自增**：`UPDATE sync_counters SET value = value + 1 WHERE name = 'server_seq'`
    后读回（rowcount 必须为 1），不依赖行锁语义，SQLite/MySQL 通用（见 `14_cloud_sync.md §12.2`）。

    本表是**部署状态**而非业务数据：行缺失时 `next_seq()` 会按 `MAX(sync_log.server_seq)` 自愈补种
    （DEC-055）。清理数据时**不要删这张表**（同理别删 `alembic_version`）。
    """

    __tablename__ = "sync_counters"
    __table_args__ = _MYSQL_TABLE_ARGS

    name: Mapped[str] = mapped_column(String(32), primary_key=True)
    value: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    # 保留整数类型引用，避免 lint 报未使用（后续如需按用户分片计数会用到）
    shard: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
