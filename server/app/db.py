"""数据库引擎与会话。

- SQLite：`check_same_thread=False`（TestClient 跨线程）+ 显式打开外键约束（SQLite 默认关闭）。
- MySQL：`pool_pre_ping` 防连接被服务端掐断；字符集由 DSN 的 `charset=utf8mb4` 指定。
"""

from collections.abc import Iterator

from sqlalchemy import create_engine, event
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings


class Base(DeclarativeBase):
    """所有 ORM 模型的基类（`Base.metadata.create_all` 供单测建表）。"""


def make_engine(database_url: str) -> Engine:
    connect_args: dict[str, object] = {}
    if database_url.startswith("sqlite"):
        connect_args["check_same_thread"] = False

    engine = create_engine(
        database_url,
        connect_args=connect_args,
        pool_pre_ping=True,
        future=True,
    )

    if database_url.startswith("sqlite"):

        @event.listens_for(engine, "connect")
        def _enable_sqlite_fk(dbapi_connection, _connection_record) -> None:  # noqa: ANN001
            cursor = dbapi_connection.cursor()
            cursor.execute("PRAGMA foreign_keys=ON")
            cursor.close()

    return engine


engine = make_engine(get_settings().database_url)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def get_db() -> Iterator[Session]:
    """FastAPI 依赖：每请求一个会话（测试用 dependency_overrides 覆盖）。"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
