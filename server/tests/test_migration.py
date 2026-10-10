"""迁移验证：SQLite 真跑一遍，MySQL 用离线 DDL 渲染做「无服务器」校验（DEC-031）。

MySQL 侧断言 utf8mb4 / InnoDB / DATETIME(6) / JSON / BIGINT 都在 DDL 里——
不写清这些，中文备注、毫秒精度、JSON 列都会在真正的 MySQL 上出问题。
"""

import io

from alembic import command
from alembic.config import Config
from sqlalchemy import inspect, text

from app.db import make_engine

SERVER_ROOT = __file__.rsplit("tests", 1)[0]


def _config(url: str, output: io.StringIO | None = None) -> Config:
    config = Config(f"{SERVER_ROOT}alembic.ini")
    config.set_main_option("script_location", f"{SERVER_ROOT}alembic")
    # alembic 的 ConfigParser 走 BasicInterpolation：值里的 `%` 必须写成 `%%`，
    # 否则 `set_main_option` 直接抛 ValueError（真实口令 URL 编码后必带 `%`）。
    config.set_main_option("sqlalchemy.url", url.replace("%", "%%"))
    if output is not None:
        config.output_buffer = output
    return config


def test_migration_creates_schema_and_counter(tmp_path):
    url = f"sqlite:///{tmp_path / 'migrated.db'}"
    command.upgrade(_config(url), "head")

    engine = make_engine(url)
    try:
        tables = set(inspect(engine).get_table_names())
        assert {"users", "refresh_tokens", "devices", "sync_records", "sync_counters"} <= tables
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
        assert "sync_records" not in set(inspect(engine).get_table_names())
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
    assert "JSON" in ddl
    assert "BIGINT" in ddl
    assert "VARCHAR(36)" in ddl and "VARCHAR(32)" in ddl and "VARCHAR(64)" in ddl
    assert "PRIMARY KEY (user_id, entity_type, entity_id)" in ddl


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
