"""DEC-037：把「一张通用记录表 + JSON payload」拆成**六张业务表 + 一张变更日志**。

Revision ID: 0002_typed_entities
Revises: 0001_init
Create Date: 2026-10-10

为什么要改（用户拍板，DEC-037）：
`sync_records.payload` 是 JSON blob，云端**无法用 SQL 查账**（`SELECT sum(amount) … WHERE date
BETWEEN …` 做不到），违背「数据上云」的初衷。改成六张与本地 Room 实体同构的业务表后：
云端可直接聚合；线协议不变（客户端仍发/收 payload），Android 零改动。

口径与 `app/models.py` 逐列一致：
- 主键 `(user_id, id)`（账号隔离写在表结构里）；`ledger_id` 是账本维度；
- `created_at_raw` / `updated_at_raw` 存客户端原始 ISO 串（逐字回显），`updated_at` 另存
  DATETIME(6) 供服务端 LWW 比较；`server_seq` 与 `sync_log` 同口径；
- **墓碑不落业务表**：`sync_log.deleted=1` 才是删除记录，业务表里只有活数据。

回填：把 `sync_records` 现有行拆列入业务表（`deleted=1` 的只写日志），然后删掉旧表。
无法拆分的历史脏行（实体类型未知 / payload 不合白名单）**保留在日志里**并打印告警，
不让一次升级把行整批吞掉 —— 老 API 的写入路径本来就过了同一套白名单，正常不该出现。
"""

import json
from collections.abc import Sequence
from types import SimpleNamespace

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import mysql

revision: str = "0002_typed_entities"
down_revision: str | None = "0001_init"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

#: MySQL 用 DATETIME(6)，其余方言用默认 DateTime（SQLite 本就带小数秒）
UTCDateTime = sa.DateTime().with_variant(mysql.DATETIME(fsp=6), "mysql")

MYSQL_TABLE_ARGS = {
    "mysql_engine": "InnoDB",
    "mysql_charset": "utf8mb4",
    "mysql_collate": "utf8mb4_unicode_ci",
}

ID_LEN = 64
RAW_TIME_LEN = 64
RAW_CREATED_LEN = 40

ENTITY_TO_TABLE = {
    "ledger": "ledgers",
    "category": "categories",
    "transaction": "transactions",
    "debt": "debts",
    "budget": "budgets",
    "user_category_rule": "user_category_rules",
}
TABLE_TO_ENTITY = {table: entity for entity, table in ENTITY_TO_TABLE.items()}

#: 表名 → (业务列, 索引)；业务列形如 (列名, 类型, 可空)
_BUSINESS: dict[str, tuple[tuple[tuple[str, sa.types.TypeEngine, bool], ...], tuple[tuple[str, tuple[str, ...]], ...]]] = {
    "ledgers": (
        (
            ("name", sa.String(length=128), False),
            ("icon", sa.String(length=64), False),
            ("color", sa.String(length=32), False),
            ("is_default", sa.Boolean(), False),
            ("position", sa.Integer(), False),
        ),
        (),
    ),
    "categories": (
        (
            ("name", sa.String(length=128), False),
            ("icon", sa.String(length=64), False),
            ("color", sa.String(length=32), False),
            ("type", sa.String(length=16), False),
            ("is_built_in", sa.Boolean(), False),
            ("sort_order", sa.Integer(), False),
        ),
        (("ix_categories_user_ledger", ("user_id", "ledger_id")),),
    ),
    "transactions": (
        (
            ("type", sa.String(length=16), False),
            ("amount", sa.BigInteger(), False),
            ("category_id", sa.String(length=ID_LEN), False),
            ("description", sa.String(length=512), True),
            ("date", sa.String(length=10), False),
            ("sync_status", sa.String(length=16), False),
            ("external_id", sa.String(length=191), True),
            ("counterparty", sa.String(length=128), True),
            ("payment_method", sa.String(length=64), True),
            ("source", sa.String(length=16), False),
        ),
        (
            ("ix_transactions_user_ledger_date", ("user_id", "ledger_id", "date")),
            ("ix_transactions_user_ledger_type", ("user_id", "ledger_id", "type")),
            ("ix_transactions_user_category", ("user_id", "category_id")),
        ),
    ),
    "debts": (
        (
            ("party_name", sa.String(length=128), False),
            ("amount", sa.BigInteger(), False),
            ("kind", sa.String(length=16), False),
            ("direction", sa.String(length=16), False),
            ("date", sa.String(length=10), False),
            ("remark", sa.String(length=512), True),
            ("sync_status", sa.String(length=16), False),
        ),
        (
            ("ix_debts_user_ledger_date", ("user_id", "ledger_id", "date")),
            ("ix_debts_user_ledger_party", ("user_id", "ledger_id", "party_name")),
        ),
    ),
    "budgets": (
        (
            ("month", sa.String(length=7), False),
            ("category_id", sa.String(length=ID_LEN), True),
            ("amount", sa.BigInteger(), False),
        ),
        (("ix_budgets_user_ledger_month", ("user_id", "ledger_id", "month")),),
    ),
    "user_category_rules": (
        (
            ("source", sa.String(length=16), False),
            ("keyword", sa.String(length=128), False),
            ("category_id", sa.String(length=ID_LEN), False),
        ),
        (("ix_user_category_rules_user_ledger", ("user_id", "ledger_id")),),
    ),
}


def _bump_boolean_default(column: sa.Column, type_: sa.types.TypeEngine) -> sa.Column:
    if isinstance(type_, sa.Boolean):
        column.server_default = sa.false()  # 与 0001 风格一致（写入路径本就总是带值）
    return column


def _create_business_tables() -> None:
    for table, (business, indexes) in _BUSINESS.items():
        business_columns = [
            _bump_boolean_default(sa.Column(name, type_, nullable=nullable), type_)
            for name, type_, nullable in business
        ]
        # 列序与 models.py 逐列一致：user_id, id, ledger_id, <业务列…>, created_at_raw,
        # updated_at_raw, updated_at, server_seq, server_updated_at
        columns: list[sa.Column] = [
            sa.Column("user_id", sa.String(length=36), nullable=False),
            sa.Column("id", sa.String(length=ID_LEN), nullable=False),
            sa.Column("ledger_id", sa.String(length=ID_LEN), nullable=False),
            *business_columns,
            sa.Column("created_at_raw", sa.String(length=RAW_CREATED_LEN), nullable=False),
            sa.Column("updated_at_raw", sa.String(length=RAW_TIME_LEN), nullable=False),
            sa.Column("updated_at", UTCDateTime, nullable=False),
            sa.Column("server_seq", sa.BigInteger(), nullable=False),
            sa.Column("server_updated_at", UTCDateTime, nullable=False),
        ]
        op.create_table(
            table,
            *columns,
            sa.ForeignKeyConstraint(["user_id"], ["users.id"], name=f"fk_{table}_user", ondelete="CASCADE"),
            sa.PrimaryKeyConstraint("user_id", "id", name=f"pk_{table}"),
            **MYSQL_TABLE_ARGS,
        )
        for index_name, index_columns in indexes:
            op.create_index(index_name, table, list(index_columns))


def _create_sync_log() -> None:
    op.create_table(
        "sync_log",
        sa.Column("user_id", sa.String(length=36), nullable=False),
        sa.Column("server_seq", sa.BigInteger(), nullable=False),
        sa.Column("entity_type", sa.String(length=32), nullable=False),
        sa.Column("entity_id", sa.String(length=ID_LEN), nullable=False),
        sa.Column("ledger_id", sa.String(length=ID_LEN), nullable=False),
        sa.Column("deleted", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("updated_at_raw", sa.String(length=RAW_TIME_LEN), nullable=False),
        sa.Column("updated_at", UTCDateTime, nullable=False),
        sa.Column("logged_at", UTCDateTime, nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], name="fk_sync_log_user", ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("user_id", "server_seq", name="pk_sync_log"),
        **MYSQL_TABLE_ARGS,
    )
    op.create_index("ix_sync_log_user_entity", "sync_log", ["user_id", "entity_type", "entity_id", "server_seq"])


def _sync_records_table() -> sa.Table:
    """0001 的旧表（供回填/降级读写；显式重建，不 import 应用模型）。"""
    return sa.Table(
        "sync_records",
        sa.MetaData(),
        sa.Column("user_id", sa.String(length=36), nullable=False),
        sa.Column("entity_type", sa.String(length=32), nullable=False),
        sa.Column("entity_id", sa.String(length=64), nullable=False),
        sa.Column("ledger_id", sa.String(length=36), nullable=False),
        sa.Column("updated_at", UTCDateTime, nullable=False),
        sa.Column("updated_at_raw", sa.String(length=64), nullable=False),
        sa.Column("deleted", sa.Boolean(), nullable=False),
        sa.Column("payload", sa.JSON(), nullable=True),
        sa.Column("server_seq", sa.BigInteger(), nullable=False),
        sa.Column("server_updated_at", UTCDateTime, nullable=False),
    )


def _typed_table(name: str) -> sa.Table:
    business, _ = _BUSINESS[name]
    return sa.Table(
        name,
        sa.MetaData(),
        sa.Column("user_id", sa.String(length=36), nullable=False),
        sa.Column("id", sa.String(length=ID_LEN), nullable=False),
        sa.Column("ledger_id", sa.String(length=ID_LEN), nullable=False),
        *[sa.Column(col, type_, nullable=nullable) for col, type_, nullable in business],
        sa.Column("created_at_raw", sa.String(length=RAW_CREATED_LEN), nullable=False),
        sa.Column("updated_at_raw", sa.String(length=RAW_TIME_LEN), nullable=False),
        sa.Column("updated_at", UTCDateTime, nullable=False),
        sa.Column("server_seq", sa.BigInteger(), nullable=False),
        sa.Column("server_updated_at", UTCDateTime, nullable=False),
    )


def _log_table() -> sa.Table:
    return sa.Table(
        "sync_log",
        sa.MetaData(),
        sa.Column("user_id", sa.String(length=36), nullable=False),
        sa.Column("server_seq", sa.BigInteger(), nullable=False),
        sa.Column("entity_type", sa.String(length=32), nullable=False),
        sa.Column("entity_id", sa.String(length=ID_LEN), nullable=False),
        sa.Column("ledger_id", sa.String(length=ID_LEN), nullable=False),
        sa.Column("deleted", sa.Boolean(), nullable=False),
        sa.Column("updated_at_raw", sa.String(length=RAW_TIME_LEN), nullable=False),
        sa.Column("updated_at", UTCDateTime, nullable=False),
        sa.Column("logged_at", UTCDateTime, nullable=False),
    )


def _offline() -> bool:
    """离线模式（`alembic upgrade --sql`）：没有连接，数据搬运必须整体跳过。"""
    return bool(getattr(op.get_context(), "as_sql", False))


def upgrade() -> None:
    _create_business_tables()
    _create_sync_log()
    if _offline():
        print("[0002] 离线模式（--sql）：只渲染 DDL，跳过数据回填")
    else:
        _backfill_from_sync_records()
    op.drop_table("sync_records")


def _backfill_from_sync_records() -> None:
    """旧表 → 业务表 + 日志。脏行只保留日志条目并告警，不让升级整批吞数据。"""
    # 延迟导入：迁移是本仓库的一次性脚本，映射必须与运行时同源（复制一份 COLUMN_MAPS
    # 反而会漂移）。payload key ⇄ 列名在本项目里是稳定契约（有测试钉住）。
    from app.services.entity_store import shred  # noqa: PLC0415

    connection = op.get_bind()
    rows = connection.execute(sa.select(_sync_records_table())).mappings().all()
    if not rows:
        return

    logs: list[dict] = []
    typed: dict[str, list[dict]] = {table: [] for table in _BUSINESS}
    kept = 0

    for row in rows:
        logs.append(
            {
                "user_id": row["user_id"],
                "server_seq": row["server_seq"],
                "entity_type": row["entity_type"],
                "entity_id": row["entity_id"],
                "ledger_id": row["ledger_id"],
                "deleted": bool(row["deleted"]),
                "updated_at_raw": row["updated_at_raw"],
                "updated_at": row["updated_at"],
                "logged_at": row["server_updated_at"],
            }
        )
        table = ENTITY_TO_TABLE.get(row["entity_type"])
        if row["deleted"] or table is None or row["payload"] is None:
            kept += 1
            continue

        payload = row["payload"]
        if isinstance(payload, str):  # SQLite 的 JSON 列经 text 查询可能给字符串
            payload = json.loads(payload)
        try:
            values = shred(row["entity_type"], payload)
        except Exception as exc:  # noqa: BLE001 - 单行脏数据不应中断迁移
            print(f"[0002] 跳过无法拆列的旧行 {row['entity_type']}/{row['entity_id']}: {exc}")
            kept += 1
            continue

        values.update(
            {
                "user_id": row["user_id"],
                "id": row["entity_id"],
                "ledger_id": row["ledger_id"],
                "updated_at_raw": row["updated_at_raw"],
                "updated_at": row["updated_at"],
                "server_seq": row["server_seq"],
                "server_updated_at": row["server_updated_at"],
            }
        )
        typed[table].append(values)

    if logs:
        connection.execute(_log_table().insert(), logs)
    for table, values in typed.items():
        if values:
            connection.execute(_typed_table(table).insert(), values)

    migrated = sum(len(values) for values in typed.values())
    print(f"[0002] 旧行 {len(rows)}：拆列入业务表 {migrated} 行，仅保留日志（墓碑/脏行）{kept} 行")


def downgrade() -> None:
    """回到 0001：重建 `sync_records` 并从业务表 + 日志拼回 payload（尽力而为）。"""
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

    if _offline():
        print("[0002] 离线模式（--sql）：只渲染 DDL，跳过 payload 拼回")
    else:
        _backfill_to_sync_records()

    op.drop_index("ix_sync_log_user_entity", table_name="sync_log")
    op.drop_table("sync_log")
    for table in reversed(list(_BUSINESS)):
        op.drop_table(table)


def _backfill_to_sync_records() -> None:
    """业务表 + 日志 → 旧 `sync_records`（降级用；墓碑保持 deleted=1 且 payload 为空）。"""
    from app.services.entity_store import reassemble  # noqa: PLC0415

    connection = op.get_bind()
    records: list[dict] = []
    live_keys: set[tuple[str, str, str]] = set()

    for table, (business, _) in _BUSINESS.items():
        entity_type = TABLE_TO_ENTITY[table]
        table_obj = _typed_table(table)
        columns = [column.name for column in table_obj.columns]
        for row in connection.execute(sa.select(table_obj)).mappings().all():
            # reassemble() 只按属性名取值，用 SimpleNamespace 冒充一行即可
            fake = SimpleNamespace(**{name: row[name] for name in columns})
            payload = reassemble(entity_type, fake)
            live_keys.add((row["user_id"], entity_type, row["id"]))
            records.append(
                {
                    "user_id": row["user_id"],
                    "entity_type": entity_type,
                    "entity_id": row["id"],
                    "ledger_id": row["ledger_id"],
                    "updated_at": row["updated_at"],
                    "updated_at_raw": row["updated_at_raw"],
                    "deleted": False,
                    "payload": payload,
                    "server_seq": row["server_seq"],
                    "server_updated_at": row["server_updated_at"],
                }
            )

    # 墓碑：日志里该实体的最后一条是 deleted=1，且业务表里没有活行
    tombstones: dict[tuple[str, str, str], dict] = {}
    log_table = _log_table()
    ordered = sa.select(log_table).order_by(
        log_table.c.user_id, log_table.c.entity_type, log_table.c.entity_id, log_table.c.server_seq
    )
    for entry in connection.execute(ordered).mappings():
        key = (entry["user_id"], entry["entity_type"], entry["entity_id"])
        if key in live_keys:
            continue
        tombstones[key] = entry  # 有序扫描 ⇒ 留下的是最后一条
    for key, entry in tombstones.items():
        if not entry["deleted"]:
            continue
        records.append(
            {
                "user_id": entry["user_id"],
                "entity_type": entry["entity_type"],
                "entity_id": entry["entity_id"],
                "ledger_id": entry["ledger_id"],
                "updated_at": entry["updated_at"],
                "updated_at_raw": entry["updated_at_raw"],
                "deleted": True,
                "payload": None,
                "server_seq": entry["server_seq"],
                "server_updated_at": entry["logged_at"],
            }
        )

    if records:
        connection.execute(_sync_records_table().insert(), records)
