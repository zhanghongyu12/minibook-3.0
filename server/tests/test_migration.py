"""迁移验证：SQLite 真跑一遍，MySQL 用离线 DDL 渲染做「无服务器」校验（DEC-031）。

MySQL 侧断言 utf8mb4 / InnoDB / DATETIME(6) / BIGINT 都在 DDL 里——
不写清这些，中文备注、毫秒精度都会在真正的 MySQL 上出问题。

DEC-037 起还要守住一条：**升级后的 DDL 里不许再有 JSON 列**（业务数据必须落在真正的列上），
并覆盖「旧 `sync_records` → 业务表 + 日志」的回填与降级往返。
"""

import io
from datetime import datetime

import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from sqlalchemy import inspect, text

from app.db import make_engine

SERVER_ROOT = __file__.rsplit("tests", 1)[0]

BUSINESS_TABLES = {
    "ledgers",
    "categories",
    "transactions",
    "debts",
    "budgets",
    "user_category_rules",
}

LEDGER_ID = "11111111-1111-4111-8111-111111111111"
TX_ID = "22222222-2222-4222-8222-222222222222"
DEAD_ID = "33333333-3333-4333-8333-333333333333"
RAW = "2026-10-10T06:05:00.000000000Z"
MOMENT = datetime(2026, 10, 10, 6, 5, 0)

LEDGER_PAYLOAD = {
    "id": LEDGER_ID,
    "name": "日常账本",
    "icon": "wallet",
    "color": "#3B82F6",
    "isDefault": True,
    "position": 0,
    "createdAt": RAW,
    "updatedAt": RAW,
}
TX_PAYLOAD = {
    "id": TX_ID,
    "ledgerId": LEDGER_ID,
    "type": "expense",
    "amount": 12345,
    "categoryId": "44444444-4444-4444-8444-444444444444",
    "description": "午饭（含中文备注）",
    "date": "2026-10-10",
    "createdAt": RAW,
    "updatedAt": RAW,
    "syncStatus": "local",
    "externalId": None,
    "counterparty": None,
    "paymentMethod": None,
    "source": "manual",
    # 账单转债务关联（DEC-056）：v1 payload 无此键，但「拆列→拼回」必须补回完整 key 集
    # （降级到 0001 时 `transactions.debt_id` 已被 0005 的 downgrade 删除 ⇒ 视为 None）。
    "debtId": None,
}


def _config(url: str, output: io.StringIO | None = None) -> Config:
    config = Config(f"{SERVER_ROOT}alembic.ini")
    config.set_main_option("script_location", f"{SERVER_ROOT}alembic")
    # alembic 的 ConfigParser 走 BasicInterpolation：值里的 `%` 必须写成 `%%`，
    # 否则 `set_main_option` 直接抛 ValueError（真实口令 URL 编码后必带 `%`）。
    config.set_main_option("sqlalchemy.url", url.replace("%", "%%"))
    if output is not None:
        config.output_buffer = output
    return config


def _seed_legacy_rows(engine) -> None:
    """在 0001 的旧表里造 3 行：账本活、交易活、交易墓碑。"""
    old = sa.Table("sync_records", sa.MetaData(), autoload_with=engine)
    shared = {
        "user_id": "u1",
        "updated_at": MOMENT,
        "updated_at_raw": RAW,
        "server_updated_at": MOMENT,
    }
    with engine.begin() as connection:
        connection.execute(
            text(
                "INSERT INTO users (id, username, password_hash, created_at, updated_at, disabled)"
                " VALUES ('u1', 'alice', 'x', '2026-10-10 00:00:00', '2026-10-10 00:00:00', 0)"
            )
        )
        connection.execute(
            old.insert(),
            [
                {
                    **shared,
                    "entity_type": "ledger",
                    "entity_id": LEDGER_ID,
                    "ledger_id": LEDGER_ID,
                    "deleted": False,
                    "payload": LEDGER_PAYLOAD,
                    "server_seq": 1,
                },
                {
                    **shared,
                    "entity_type": "transaction",
                    "entity_id": TX_ID,
                    "ledger_id": LEDGER_ID,
                    "deleted": False,
                    "payload": TX_PAYLOAD,
                    "server_seq": 2,
                },
                {
                    **shared,
                    "entity_type": "transaction",
                    "entity_id": DEAD_ID,
                    "ledger_id": LEDGER_ID,
                    "deleted": True,
                    "payload": None,
                    "server_seq": 3,
                },
            ],
        )


def test_migration_creates_schema_and_counter(tmp_path):
    url = f"sqlite:///{tmp_path / 'migrated.db'}"
    command.upgrade(_config(url), "head")

    engine = make_engine(url)
    try:
        tables = set(inspect(engine).get_table_names())
        assert BUSINESS_TABLES | {"users", "refresh_tokens", "devices", "sync_log", "sync_counters"} <= tables
        assert "sync_records" not in tables  # 旧的 JSON blob 表已下线
        with engine.connect() as connection:
            value = connection.execute(
                text("SELECT value FROM sync_counters WHERE name = 'server_seq'")
            ).scalar_one()
        assert value == 0  # next_seq() 依赖该行必须存在
    finally:
        engine.dispose()


def test_migration_is_reversible(tmp_path):
    url = f"sqlite:///{tmp_path / 'downgraded.db'}"
    config = _config(url)
    command.upgrade(config, "head")
    command.downgrade(config, "base")

    engine = make_engine(url)
    try:
        tables = set(inspect(engine).get_table_names())
        assert "sync_records" not in tables
        assert not (BUSINESS_TABLES & tables)
        assert "sync_log" not in tables
    finally:
        engine.dispose()


def test_backfill_splits_payloads_into_columns(tmp_path):
    """0001 → 0002：旧 JSON 行必须被拆进业务表；墓碑只留日志、不进业务表。"""
    url = f"sqlite:///{tmp_path / 'backfill.db'}"
    config = _config(url)
    command.upgrade(config, "0001_init")

    engine = make_engine(url)
    try:
        _seed_legacy_rows(engine)
        command.upgrade(config, "head")

        with engine.connect() as connection:
            assert "sync_records" not in set(inspect(engine).get_table_names())

            ledger = connection.execute(
                text("SELECT name, icon, color, is_default, position, created_at_raw FROM ledgers WHERE user_id = 'u1'")
            ).one()
            assert ledger.name == "日常账本"
            assert ledger.icon == "wallet"
            assert ledger.color == "#3B82F6"
            assert bool(ledger.is_default) is True
            assert ledger.position == 0
            assert ledger.created_at_raw == RAW  # 原始 ISO 串逐字保留

            tx = connection.execute(
                text("SELECT amount, description, date, category_id, source, sync_status FROM transactions WHERE user_id = 'u1'")
            ).one()
            assert tx.amount == 12345
            assert tx.description == "午饭（含中文备注）"
            assert tx.date == "2026-10-10"
            assert tx.category_id == "44444444-4444-4444-8444-444444444444"
            assert tx.source == "manual" and tx.sync_status == "local"

            # 墓碑不落业务表：transactions 只有活的那一行
            assert connection.execute(text("SELECT count(*) FROM transactions")).scalar_one() == 1
            log = connection.execute(
                text("SELECT entity_type, entity_id, deleted FROM sync_log WHERE user_id = 'u1' ORDER BY server_seq")
            ).all()
            assert len(log) == 3
            assert {row.entity_id for row in log if row.deleted} == {DEAD_ID}
    finally:
        engine.dispose()


def test_downgrade_reassembles_payloads(tmp_path):
    """0002 → 0001：业务表要能拼回旧 `sync_records` 的 payload（墓碑保持 deleted=1/无 payload）。"""
    url = f"sqlite:///{tmp_path / 'roundtrip.db'}"
    config = _config(url)
    command.upgrade(config, "0001_init")

    engine = make_engine(url)
    try:
        _seed_legacy_rows(engine)
        command.upgrade(config, "head")
        command.downgrade(config, "0001_init")

        assert "sync_log" not in set(inspect(engine).get_table_names())
        old = sa.Table("sync_records", sa.MetaData(), autoload_with=engine)
        with engine.connect() as connection:
            rows = {
                (row.entity_type, row.entity_id): row
                for row in connection.execute(sa.select(old)).all()
            }
        assert set(rows) == {("ledger", LEDGER_ID), ("transaction", TX_ID), ("transaction", DEAD_ID)}

        ledger = rows[("ledger", LEDGER_ID)]
        assert ledger.payload == LEDGER_PAYLOAD  # 逐字拼回
        tx = rows[("transaction", TX_ID)]
        assert tx.payload == TX_PAYLOAD
        dead = rows[("transaction", DEAD_ID)]
        assert dead.deleted is True and dead.payload is None
        assert dead.ledger_id == LEDGER_ID
    finally:
        engine.dispose()


def test_mysql_ddl_contains_required_mysql_clauses():
    output = io.StringIO()
    # 离线模式（--sql）：不连库，只渲染 DDL
    command.upgrade(_config("mysql+pymysql://user:pw@127.0.0.1:3306/minibook", output), "head", sql=True)
    ddl = output.getvalue()

    assert "InnoDB" in ddl
    assert "utf8mb4" in ddl
    assert "DATETIME(6)" in ddl  # 毫秒不被截断（no-op 判定依赖）
    assert "BIGINT" in ddl  # amount（分）与 server_seq
    assert "VARCHAR(36)" in ddl and "VARCHAR(32)" in ddl and "VARCHAR(64)" in ddl
    assert "VARCHAR(512)" in ddl  # 备注列宽度显式声明
    # 六张业务表 + 日志表的主键口径
    assert "PRIMARY KEY (user_id, id)" in ddl
    assert "PRIMARY KEY (user_id, server_seq)" in ddl


def test_typed_tables_ddl_has_no_json_column():
    """DEC-037：拆表这一步的 DDL 里不许再有 JSON 列。

    注意不能查整条链的 DDL —— `sync_records`（带 JSON 列）是 0001 建的、0002 才删，
    整链渲染里必然出现 JSON。所以只渲染 `0001_init:0002_typed_entities` 这一段。
    """
    output = io.StringIO()
    command.upgrade(
        _config("mysql+pymysql://user:pw@127.0.0.1:3306/minibook", output),
        "0001_init:0002_typed_entities",
        sql=True,
    )
    ddl = output.getvalue().upper()

    assert "LEDGERS" in ddl and "TRANSACTIONS" in ddl and "SYNC_LOG" in ddl
    assert " JSON" not in ddl
    assert "DROP TABLE SYNC_RECORDS" in ddl


def test_migration_accepts_percent_encoded_password():
    """口令含 `@`/`%`（URL 编码后必带 `%`）时 alembic 不能炸 configparser 插值。

    回归用例：真实业务口令含 `@`（URL 编码后必带 `%`，如 `user@123` → `user%40123`），
    曾在 `alembic upgrade head` 上直接抛 `invalid interpolation syntax`。
    """
    output = io.StringIO()
    command.upgrade(_config("mysql+pymysql://user:p%40ss%25word@127.0.0.1:3306/minibook", output), "head", sql=True)

    ddl = output.getvalue()
    assert "InnoDB" in ddl and "DATETIME(6)" in ddl


def test_mysql_downgrade_drops_tables_not_indexes_first():
    """回归：MySQL 上先 `DROP INDEX` 会因外键依赖索引而失败（errno 1553）。

    实测踩坑：`downgrade base` 在 SQLite 上通过、在 MySQL 8.0.46 上抛
    `Cannot drop index 'ix_refresh_tokens_user': needed in a foreign key constraint`，
    中途失败还留下半删状态。故 downgrade 只允许 `DROP TABLE`（连带索引/外键）。
    """
    output = io.StringIO()
    command.downgrade(
        _config("mysql+pymysql://user:pw@127.0.0.1:3306/minibook", output),
        "0001_init:base",
        sql=True,
    )

    downgrade_ddl = output.getvalue().upper()
    assert "DROP TABLE" in downgrade_ddl
    assert "DROP INDEX" not in downgrade_ddl
