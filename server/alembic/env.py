"""Alembic 环境：URL 由 `.env`/环境变量注入（双 DSN，DEC-031）。"""

from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine, pool

from app.config import get_settings
from app.db import Base
from app import models  # noqa: F401  —— 导入以注册所有表到 Base.metadata

config = context.config
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def database_url() -> str:
    """优先级：显式传入的 URL（测试/CI）> `.env`/环境变量（运行期）。

    刻意**不**用 `config.set_main_option()` 把 URL 回写进 alembic 配置：alembic 的
    ConfigParser 用 `BasicInterpolation`，值里出现 `%` 会当场抛
    `ValueError: invalid interpolation syntax` —— 而口令经 URL 编码后**必然**带 `%`
    （如 `user@123` → `user%40123`，实测在 MySQL 8 上直接炸掉 `alembic upgrade`）。

    这里只读取、不回写，URL 原样交给 `create_engine`，`%xx` 由 SQLAlchemy 自己解码。
    """
    return config.get_main_option("sqlalchemy.url") or get_settings().database_url


def run_migrations_offline() -> None:
    context.configure(
        url=database_url(),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = create_engine(database_url(), poolclass=pool.NullPool)
    with connectable.connect() as connection:
        context.configure(
            connection=connection,
            target_metadata=target_metadata,
            compare_type=True,
            # SQLite 的 ALTER 能力弱，后续加列迁移需 batch 模式
            render_as_batch=connection.dialect.name == "sqlite",
        )
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
