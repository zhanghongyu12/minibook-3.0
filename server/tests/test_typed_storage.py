"""DEC-037 的核心断言：云端是**六张真正的业务表**，不是一张 JSON blob。

这组用例守住三件事：
1. **映射自洽**：`entity_spec`（字段/类型/长度）↔ `COLUMN_MAPS`（payload key → 列名）↔
   `models`（列宽/主键）三方对齐，谁加了字段忘了改另一边就红；
2. **真列落库**：推送后能用 SQL 直接聚合（`sum(amount)` / 按日期分组）——这是改存储的**全部理由**；
3. **墓碑不落业务表**：删除只留 `sync_log`，业务表里查不到死行，但拉取照样能把删除送达。

另外守住「payload 逐字往返」：拼回的 payload 必须与推上去的**完全相等**（含中文与 emoji）。
"""

from sqlalchemy import text

from app.entity_spec import ENTITY_SPECS
from app.services.entity_store import COLUMN_MAPS, ENVELOPE_KEY_TO_COLUMN, TYPED_MODELS
from tests.conftest import (
    LEDGER_ID,
    ledger_payload,
    make_account,
    pull,
    push,
    sync_item,
    transaction_payload,
)

T1 = "2026-10-10T06:00:00.000000000Z"
T2 = "2026-10-10T06:05:00.000000000Z"
T3 = "2026-10-10T06:10:00.000000000Z"

#: 由信封统一驱动、不进 COLUMN_MAPS 的公共列
_ENVELOPE_COLUMNS = {
    "user_id",
    "id",
    "ledger_id",
    "updated_at_raw",
    "updated_at",
    "server_seq",
    "server_updated_at",
}

#: 每张业务表都还有的公共列（由 payload 的 `createdAt` 驱动，故它在 COLUMN_MAPS 里）
_SHARED_COLUMNS = {"created_at_raw"}

CATEGORY_ID = "55555555-5555-4555-8555-555555555555"
DEBT_ID = "66666666-6666-4666-8666-666666666666"
BUDGET_ID = "77777777-7777-4777-8777-777777777777"
RULE_ID = "88888888-8888-4888-8888-888888888888"


def test_column_maps_match_spec_and_models():
    """三方（白名单 / 列名映射 / ORM 列）必须逐项对齐，且长度与列宽一致。"""
    for entity_type, spec in ENTITY_SPECS.items():
        mapping = COLUMN_MAPS[entity_type]
        # 白名单里的 key 必须全部有落点：业务列走 COLUMN_MAPS，信封 key 走公共列。
        # 注意 `ledger` 的 payload 里**没有** `ledgerId`（账本表里 ledger_id = 自身 id），
        # 所以只要求「白名单 ∩ 信封 key」都有映射，不要求信封 key 全都出现。
        assert set(mapping) | (set(ENVELOPE_KEY_TO_COLUMN) & set(spec)) == set(spec), entity_type

        model = TYPED_MODELS[entity_type]
        columns = {column.name: column for column in model.__table__.columns}

        # 业务列：双向覆盖（映射里的列 = 表里全部非信封列，含 createdAt → created_at_raw）
        assert set(mapping.values()) == set(columns) - _ENVELOPE_COLUMNS, entity_type
        for key, column_name in mapping.items():
            field = spec[key]
            if field.max_length is not None:
                assert columns[column_name].type.length == field.max_length, (entity_type, key)

        # 信封列必须存在，长度也要与白名单一致（`ledger` 的 payload 无 `ledgerId` ⇒ 跳过比对）
        for key, column_name in ENVELOPE_KEY_TO_COLUMN.items():
            assert column_name in columns, (entity_type, key)
            field = spec.get(key)
            if field is not None and field.max_length is not None:
                assert columns[column_name].type.length == field.max_length, (entity_type, key)

        # 公共列 + 主键口径
        assert _ENVELOPE_COLUMNS | _SHARED_COLUMNS <= set(columns), entity_type
        assert [c.name for c in model.__table__.primary_key.columns] == ["user_id", "id"]


def test_push_lands_in_real_columns_and_sql_aggregates(client, engine):  # noqa: ANN001
    """改存储的全部理由：云端能直接用 SQL 查账。"""
    account = make_account(client, "columnsuser")
    user_id = account["user"]["user_id"]
    amounts = {"aaaa0001": 1000, "aaaa0002": 2000, "aaaa0003": 3000}
    items = [
        sync_item(
            "ledger",
            entity_id=LEDGER_ID,
            ledger_id=LEDGER_ID,
            updated_at=T1,
            payload=ledger_payload(updated_at=T1),
        )
    ]
    for index, (entity_id, amount) in enumerate(amounts.items()):
        updated_at = f"2026-10-10T06:0{index + 2}:00.000000000Z"
        items.append(
            sync_item(
                "transaction",
                entity_id=entity_id,
                ledger_id=LEDGER_ID,
                updated_at=updated_at,
                payload=transaction_payload(entity_id=entity_id, updated_at=updated_at, amount=amount),
            )
        )

    response = push(client, account["headers"], items)
    assert response.status_code == 200, response.text
    assert response.json()["applied"] == 4

    with engine.connect() as connection:
        rows = connection.execute(
            text(
                "SELECT id, amount, description, date, category_id, source FROM transactions"
                " WHERE user_id = :u ORDER BY amount"
            ),
            {"u": user_id},
        ).all()
        assert [row.amount for row in rows] == [1000, 2000, 3000]
        assert all(row.description == "午饭（含中文备注）" for row in rows)
        assert all(row.date == "2026-10-10" and row.source == "manual" for row in rows)

        total = connection.execute(
            text("SELECT coalesce(sum(amount), 0) FROM transactions WHERE user_id = :u AND date = '2026-10-10'"),
            {"u": user_id},
        ).scalar_one()
        assert total == 6000

        by_day = connection.execute(
            text("SELECT date, count(*) FROM transactions WHERE user_id = :u GROUP BY date"),
            {"u": user_id},
        ).all()
        assert [(row.date, row[1]) for row in by_day] == [("2026-10-10", 3)]

        # 账本也落在自己的表里（账本表的 ledger_id = 自身 id）
        ledger = connection.execute(
            text("SELECT id, ledger_id, name, is_default FROM ledgers WHERE user_id = :u"), {"u": user_id}
        ).one()
        assert ledger.id == ledger.ledger_id == LEDGER_ID
        assert ledger.name == "日常账本" and bool(ledger.is_default) is True


def test_tombstone_removes_business_row_and_stays_in_log(client, engine):  # noqa: ANN001
    account = make_account(client, "tombstoneuser")
    user_id = account["user"]["user_id"]
    entity_id = "bbbb0001"
    live = sync_item(
        "transaction",
        entity_id=entity_id,
        updated_at=T2,
        payload=transaction_payload(entity_id=entity_id, updated_at=T2),
    )
    assert push(client, account["headers"], [live]).status_code == 200

    with engine.connect() as connection:
        assert connection.execute(
            text("SELECT count(*) FROM transactions WHERE user_id = :u AND id = :i"), {"u": user_id, "i": entity_id}
        ).scalar_one() == 1

    # 墓碑：显式 deleted=true、不带 payload、时间更晚
    tombstone = sync_item("transaction", entity_id=entity_id, updated_at=T3, deleted=True, payload=None)
    response = push(client, account["headers"], [tombstone])
    assert response.status_code == 200, response.text
    assert response.json()["applied"] == 1

    with engine.connect() as connection:
        # 业务表里没有死行
        assert connection.execute(
            text("SELECT count(*) FROM transactions WHERE user_id = :u"), {"u": user_id}
        ).scalar_one() == 0
        # 删除只活在日志里
        log = connection.execute(
            text("SELECT deleted, updated_at_raw FROM sync_log WHERE user_id = :u ORDER BY server_seq"),
            {"u": user_id},
        ).all()
        assert [(bool(row.deleted), row.updated_at_raw) for row in log] == [(False, T2), (True, T3)]

    # 拉取照样能把删除送达客户端
    pulled = pull(client, account["headers"]).json()["items"]
    assert [(item["entity_id"], item["deleted"], item["payload"]) for item in pulled] == [
        (entity_id, True, None)
    ]

    # status 口径不变：墓碑也算「见过的实体」（旧实现是 sync_records 的行数）
    status = client.get("/api/v1/sync/status", headers=account["headers"]).json()
    assert status["counts"].get("transaction") == 1


def test_all_six_entity_types_roundtrip_byte_exact(client):  # noqa: ANN001
    """六类实体各推一条，拉回来必须**逐字相等**（含中文、emoji、null 字段、布尔、整数）。"""
    account = make_account(client, "roundtripuser")
    headers = account["headers"]

    payloads = {
        "ledger": ledger_payload(updated_at=T1),
        "category": {
            "id": CATEGORY_ID,
            "ledgerId": LEDGER_ID,
            "name": "餐饮🍜",
            "icon": "restaurant",
            "color": "#4CAF50",
            "type": "expense",
            "isBuiltIn": False,
            "order": 3,
            "createdAt": T1,
            "updatedAt": T1,
        },
        "transaction": transaction_payload(entity_id="cccc0001", updated_at=T2),
        "debt": {
            "id": DEBT_ID,
            "ledgerId": LEDGER_ID,
            "partyName": "张三",
            "amount": 8800,
            "kind": "borrow",
            "direction": "in",
            "date": "2026-10-09",
            "remark": "借款备注（含中文）",
            "createdAt": T1,
            "updatedAt": T1,
            "syncStatus": "local",
        },
        "budget": {
            "id": BUDGET_ID,
            "ledgerId": LEDGER_ID,
            "month": "2026-10",
            "categoryId": None,
            "amount": 500000,
            "createdAt": T1,
            "updatedAt": T1,
        },
        "user_category_rule": {
            "id": RULE_ID,
            "ledgerId": LEDGER_ID,
            "source": "sms",
            "keyword": "星巴克☕",
            "categoryId": CATEGORY_ID,
            "createdAt": T1,
            "updatedAt": T1,
        },
    }

    items = [
        sync_item(entity_type, entity_id=payload["id"], ledger_id=LEDGER_ID, updated_at=payload["updatedAt"], payload=payload)
        for entity_type, payload in payloads.items()
    ]
    response = push(client, headers, items)
    assert response.status_code == 200, response.text
    assert response.json()["applied"] == 6

    pulled = pull(client, headers, since_seq=0, limit=200).json()["items"]
    assert len(pulled) == 6
    by_id = {item["entity_id"]: item for item in pulled}
    for entity_type, payload in payloads.items():
        item = by_id[payload["id"]]
        assert item["entity_type"] == entity_type
        assert item["deleted"] is False
        assert item["payload"] == payload, entity_type  # 逐字相等（含中英文/emoji/null）
        assert item["updated_at"] == payload["updatedAt"]


def test_payload_may_omit_nullable_fields(client, engine):  # noqa: ANN001
    """可空字段被**整个省略**（客户端 explicitNulls=false）也必须能入库。

    回归用例（2026-10-10 实测）：真实历史 payload 里没有 `externalId` 等键，
    回填时用下标取值 ⇒ 整行被判成脏数据跳过（数据只在日志里剩个壳）。
    """
    account = make_account(client, "omittednulls")
    user_id = account["user"]["user_id"]
    payload = transaction_payload(entity_id="eeee0001", updated_at=T2)
    for key in ("externalId", "counterparty", "paymentMethod", "description"):
        payload.pop(key)
    assert "externalId" not in payload

    response = push(
        client, account["headers"], [sync_item("transaction", entity_id="eeee0001", updated_at=T2, payload=payload)]
    )
    assert response.status_code == 200, response.text
    assert response.json()["applied"] == 1

    with engine.connect() as connection:
        row = connection.execute(
            text("SELECT amount, description, external_id, counterparty FROM transactions WHERE user_id = :u"),
            {"u": user_id},
        ).one()
        assert row.amount == 12345
        assert row.description is None and row.external_id is None and row.counterparty is None

    # 拼回时按 spec 补成显式 null（客户端解码侧 explicitNulls=false，接受显式 null）
    pulled = pull(client, account["headers"]).json()["items"][0]["payload"]
    assert pulled["externalId"] is None
    assert pulled["counterparty"] is None
    assert pulled["description"] is None
    assert pulled["amount"] == 12345


def test_oversized_field_is_rejected_before_write(client, engine):  # noqa: ANN001
    """超长字段必须在入口变成带下标的 422，绝不落到 MySQL 去截断/报 500。"""
    account = make_account(client, "oversizeuser")
    user_id = account["user"]["user_id"]
    payload = transaction_payload(entity_id="dddd0001", updated_at=T2, description="长" * 600)

    response = push(client, account["headers"], [sync_item("transaction", entity_id="dddd0001", updated_at=T2, payload=payload)])
    assert response.status_code == 422, response.text
    body = response.json()
    assert body["error"]["code"] == "validation_error"
    assert "超长" in body["error"]["message"]
    assert body["error"].get("index") == 0

    with engine.connect() as connection:
        assert connection.execute(
            text("SELECT count(*) FROM transactions WHERE user_id = :u"), {"u": user_id}
        ).scalar_one() == 0
