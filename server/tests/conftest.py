"""pytest 夹具：临时库（SQLite 默认 / MySQL 可切）+ TestClient + 常用构造器。

双 DSN（DEC-031）：
- 默认：每个测试一个临时 SQLite 文件（零依赖，快）；
- MySQL：`TEST_DATABASE_URL=mysql+pymysql://…/minibook_test?charset=utf8mb4` 跑同一套用例。
  **安全护栏**：该 URL 的库名必须含 `test`，否则直接拒绝（防止把真实库 drop 掉）。
"""

import os
import sys
from collections.abc import Iterator
from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

SERVER_ROOT = Path(__file__).resolve().parents[1]
if str(SERVER_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVER_ROOT))

from app.db import Base, get_db, make_engine  # noqa: E402
from app.main import app  # noqa: E402
from app.models import SyncCounter  # noqa: E402
from app.ratelimit import reset as reset_rate_limit  # noqa: E402

COUNTER_NAME = "server_seq"

DEFAULT_PASSWORD = "sup3r-secret"


def _database_url(tmp_path: Path) -> str:
    override = os.environ.get("TEST_DATABASE_URL")
    if not override:
        return f"sqlite:///{tmp_path / 'minibook-test.db'}"
    if "test" not in override.lower():
        raise RuntimeError(
            "TEST_DATABASE_URL 的库名必须包含 'test'（测试会 drop_all，防误伤真实库）"
        )
    return override


@pytest.fixture()
def engine(tmp_path: Path):  # noqa: ANN201
    url = _database_url(tmp_path)
    engine = make_engine(url)
    if not url.startswith("sqlite"):
        Base.metadata.drop_all(engine)  # 专用测试库：先清干净，保证与 SQLite 同起点
    Base.metadata.create_all(engine)
    with engine.begin() as connection:
        connection.execute(
            SyncCounter.__table__.insert().values(name=COUNTER_NAME, value=0, shard=0)
        )
    yield engine
    engine.dispose()


@pytest.fixture()
def session_factory(engine):  # noqa: ANN201
    return sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


@pytest.fixture()
def client(engine, session_factory) -> Iterator[TestClient]:  # noqa: ANN001
    def override_get_db() -> Iterator[Session]:
        db = session_factory()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    reset_rate_limit()
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


# ── 便捷构造器（测试可读性）────────────────────────────────────────────


def register(client: TestClient, username: str, password: str = DEFAULT_PASSWORD) -> dict[str, Any]:
    response = client.post("/api/v1/auth/register", json={"username": username, "password": password})
    assert response.status_code == 201, response.text
    return response.json()


def login(client: TestClient, username: str, password: str = DEFAULT_PASSWORD) -> dict[str, Any]:
    response = client.post("/api/v1/auth/login", json={"username": username, "password": password})
    assert response.status_code == 200, response.text
    return response.json()


def auth_headers(tokens: dict[str, Any]) -> dict[str, str]:
    return {"Authorization": f"Bearer {tokens['access_token']}"}


def make_account(client: TestClient, username: str) -> dict[str, Any]:
    register(client, username)
    tokens = login(client, username)
    return {"tokens": tokens, "headers": auth_headers(tokens), "user": tokens["user"]}


LEDGER_ID = "11111111-1111-4111-8111-111111111111"


def ledger_payload(ledger_id: str = LEDGER_ID, updated_at: str = "2026-10-10T06:00:00.000000000Z") -> dict:
    return {
        "id": ledger_id,
        "name": "日常账本",
        "icon": "wallet",
        "color": "#3B82F6",
        "isDefault": True,
        "position": 0,
        "createdAt": updated_at,
        "updatedAt": updated_at,
    }


def transaction_payload(
    entity_id: str = "22222222-2222-4222-8222-222222222222",
    ledger_id: str = LEDGER_ID,
    updated_at: str = "2026-10-10T06:05:00.000000000Z",
    amount: int = 12345,
    **overrides: Any,
) -> dict:
    payload = {
        "id": entity_id,
        "ledgerId": ledger_id,
        "type": "expense",
        "amount": amount,
        "categoryId": "33333333-3333-4333-8333-333333333333",
        "description": "午饭（含中文备注）",
        "date": "2026-10-10",
        "createdAt": updated_at,
        "updatedAt": updated_at,
        "syncStatus": "local",
        "externalId": None,
        "counterparty": None,
        "paymentMethod": None,
        "source": "manual",
        # 账单转债务关联（DEC-056）：未转债务时为 null（客户端 explicitNulls=false 时可能整个省略）。
        "debtId": None,
    }
    payload.update(overrides)
    return payload


def sync_item(
    entity_type: str = "transaction",
    entity_id: str = "22222222-2222-4222-8222-222222222222",
    ledger_id: str = LEDGER_ID,
    updated_at: str = "2026-10-10T06:05:00.000000000Z",
    deleted: bool = False,
    payload: dict | None = None,
) -> dict:
    if payload is None and not deleted:
        payload = (
            transaction_payload(entity_id=entity_id, ledger_id=ledger_id, updated_at=updated_at)
            if entity_type == "transaction"
            else ledger_payload(ledger_id=ledger_id, updated_at=updated_at)
        )
    if payload is not None and deleted:
        # 墓碑也带最后内容时，保证信封与内容一致
        payload = {**payload, "updatedAt": updated_at}
    item: dict[str, Any] = {
        "entity_type": entity_type,
        "entity_id": entity_id,
        "ledger_id": ledger_id,
        "updated_at": updated_at,
        "deleted": deleted,
        "payload": payload,
    }
    return item


def push(client: TestClient, headers: dict[str, str], items: list[dict], device_id: str | None = None):  # noqa: ANN201
    body: dict[str, Any] = {"items": items}
    if device_id:
        body["device_id"] = device_id
    return client.post("/api/v1/sync/push", json=body, headers=headers)


def pull(client: TestClient, headers: dict[str, str], since_seq: int = 0, limit: int | None = None):  # noqa: ANN201
    params: dict[str, Any] = {"since_seq": since_seq}
    if limit is not None:
        params["limit"] = limit
    return client.get("/api/v1/sync/pull", params=params, headers=headers)
