"""建表：用户系统 + 同步记录 + 游标计数器（F-15）。

Revision ID: 0001_init
Revises: None
Create Date: 2026-10-10

口径（DEC-031）：MySQL 侧 InnoDB + utf8mb4；时间列 DATETIME(6)（避免毫秒被截断导致
「同 updated_at = no-op」判定失真）；`payload` 用 JSON；`sync_counters` 先落一行 `server_seq = 0`
（`next_seq()` 只做原子自增，行必须由迁移保证存在）。
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import mysql

revision: str = "0001_init"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

#: MySQL 用 DATETIME(6)，其余方言用默认 DateTime（SQLite 本就带小数秒）
UTCDateTime = sa.DateTime().with_variant(mysql.DATETIME(fsp=6), "mysql")

MYSQL_TABLE_ARGS = {
    "mysql_engine": "InnoDB",
    "mysql_charset": "utf8mb4",
    # 必须显式写 collate：MySQL 8 对 CHARSET=utf8mb4 默认给 utf8mb4_0900_ai_ci，
    # 与部署口径 utf8mb4_unicode_ci 不一致（实测 2026-10-10）
    "mysql_collate": "utf8mb4_unicode_ci",
}

COUNTER_NAME = "server_seq"


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("username", sa.String(length=32), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("created_at", UTCDateTime, nullable=False),
        sa.Column("updated_at", UTCDateTime, nullable=False),
        sa.Column("disabled", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.PrimaryKeyConstraint("id", name="pk_users"),
        sa.UniqueConstraint("username", name="uq_users_username"),
        **MYSQL_TABLE_ARGS,
    )

    op.create_table(
        "refresh_tokens",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("user_id", sa.String(length=36), nullable=False),
        sa.Column("token_hash", sa.String(length=128), nullable=False),
        sa.Column("issued_at", UTCDateTime, nullable=False),
        sa.Column("expires_at", UTCDateTime, nullable=False),
        sa.Column("revoked_at", UTCDateTime, nullable=True),
        sa.Column("device_id", sa.String(length=36), nullable=True),
        sa.Column("rotated_from", sa.String(length=36), nullable=True),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name="fk_refresh_tokens_user", ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id", name="pk_refresh_tokens"),
        **MYSQL_TABLE_ARGS,
    )
    op.create_index("ix_refresh_tokens_user", "refresh_tokens", ["user_id"])
    op.create_index("ix_refresh_tokens_hash", "refresh_tokens", ["token_hash"])

    op.create_table(
        "devices",
        sa.Column("id", sa.String(length=36), nullable=False),
        sa.Column("user_id", sa.String(length=36), nullable=False),
        sa.Column("name", sa.String(length=64), nullable=True),
        sa.Column("platform", sa.String(length=32), nullable=True),
        sa.Column("app_version", sa.String(length=32), nullable=True),
        sa.Column("last_seen_at", UTCDateTime, nullable=False),
        sa.Column("created_at", UTCDateTime, nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name="fk_devices_user", ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id", name="pk_devices"),
        sa.UniqueConstraint("user_id", "id", name="uq_devices_user_id"),
        **MYSQL_TABLE_ARGS,
    )

    op.create_table(
        "sync_records",
        sa.Column("user_id", sa.String(length=36), nullable=False),
        sa.Column("entity_type", sa.String(length=32), nullable=False),
        sa.Column("entity_id", sa.String(length=64), nullable=False),
        sa.Column("ledger_id", sa.String(length=36), nullable=False),
        sa.Column("updated_at", UTCDateTime, nullable=False),
        sa.Column("updated_at_raw", sa.String(length=64), nullable=False),
        sa.Column("deleted", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("payload", sa.JSON(), nullable=True),
        sa.Column("server_seq", sa.BigInteger(), nullable=False),
        sa.Column("server_updated_at", UTCDateTime, nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name="fk_sync_records_user", ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("user_id", "entity_type", "entity_id", name="pk_sync_records"),
        **MYSQL_TABLE_ARGS,
    )
    op.create_index("ix_sync_records_user_seq", "sync_records", ["user_id", "server_seq"])
    op.create_index("ix_sync_records_user_ledger", "sync_records", ["user_id", "ledger_id"])

    op.create_table(
        "sync_counters",
        sa.Column("name", sa.String(length=32), nullable=False),
        sa.Column("value", sa.BigInteger(), nullable=False),
        sa.Column("shard", sa.Integer(), nullable=False, server_default="0"),
        sa.PrimaryKeyConstraint("name", name="pk_sync_counters"),
        **MYSQL_TABLE_ARGS,
    )
    op.bulk_insert(
        sa.table(
            "sync_counters",
            sa.column("name", sa.String(length=32)),
            sa.column("value", sa.BigInteger()),
            sa.column("shard", sa.Integer()),
        ),
        [{"name": COUNTER_NAME, "value": 0, "shard": 0}],
    )


def downgrade() -> None:
    """逆序删表。

    **不显式 `drop_index`**：MySQL 上 `DROP INDEX ix_refresh_tokens_user` 会直接失败
    （errno 1553 `Cannot drop index ...: needed in a foreign key constraint`，实测 2026-10-10
    内网 MySQL 8.0.46）——该索引是 `fk_refresh_tokens_user` 的支撑索引。
    `drop_table` 本身会连带删掉表上的索引与外键，故先删子表、最后删父表 `users`。
    """
    op.drop_table("sync_records")
    op.drop_table("devices")
    op.drop_table("refresh_tokens")
    op.drop_table("sync_counters")
    op.drop_table("users")
