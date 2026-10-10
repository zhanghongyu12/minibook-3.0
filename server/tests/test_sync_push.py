"""A3 相关：push 的契约、白名单校验、整批回滚与中文备注往返。"""

from tests.conftest import LEDGER_ID, make_account, pull, push, sync_item, transaction_payload


def test_push_new_records_and_pull_round_trip_chinese(client):
    account = make_account(client, "push_ok")
    items = [
        sync_item(entity_type="ledger", entity_id=LEDGER_ID),
        sync_item(),
    ]
    response = push(client, account["headers"], items)
    assert response.status_code == 200
    body = response.json()
    assert body["applied"] == 2
    assert body["skipped"] == 0
    assert body["conflicts"] == []
    assert body["server_seq"] == 2

    items_back = pull(client, account["headers"]).json()["items"]
    transaction = next(item for item in items_back if item["entity_type"] == "transaction")
    assert transaction["payload"]["description"] == "午饭（含中文备注）"
    assert transaction["payload"]["amount"] == 12345
    # 回传的是客户端原始 ISO 串（纳秒保留），避免客户端误判冲突
    assert transaction["updated_at"] == "2026-10-10T06:05:00.000000000Z"


def test_unknown_field_rejected_and_nothing_written(client):
    account = make_account(client, "push_unknown")
    broken = sync_item(payload={**transaction_payload(), "hacker": "x"})
    response = push(client, account["headers"], [sync_item(entity_id="ok-1"), broken])
    assert response.status_code == 422
    error = response.json()["error"]
    assert error["code"] == "validation_error"
    assert error["index"] == 1
    assert "hacker" in error["message"]

    # 整批回滚：合法的那一条也不许落库，server_seq 不推进
    assert pull(client, account["headers"]).json()["items"] == []
    assert client.get("/api/v1/sync/status", headers=account["headers"]).json()["server_seq"] == 0


def test_amount_must_be_integer(client):
    account = make_account(client, "push_float")
    for amount in (12.5, True, "123"):
        response = push(client, account["headers"], [sync_item(payload=transaction_payload(amount=amount))])
        assert response.status_code == 422, f"amount={amount!r} 应被拒绝"


def test_missing_required_field_rejected(client):
    account = make_account(client, "push_missing")
    payload = transaction_payload()
    payload.pop("categoryId")
    response = push(client, account["headers"], [sync_item(payload=payload)])
    assert response.status_code == 422
    assert "categoryId" in response.json()["error"]["message"]


def test_envelope_payload_mismatch_rejected(client):
    account = make_account(client, "push_mismatch")
    # 信封 id 与 payload 内容 id 不一致 → 必须被拒（否则元数据与内容两套说法）
    mismatched = sync_item(entity_id="envelope-id", payload={**transaction_payload(), "id": "other-id"})
    response = push(client, account["headers"], [mismatched])
    assert response.status_code == 422
    assert "不一致" in response.json()["error"]["message"]


def test_nullable_fields_accept_null_but_required_do_not(client):
    account = make_account(client, "push_null")
    ok = push(client, account["headers"], [sync_item(payload=transaction_payload(counterparty=None))])
    assert ok.status_code == 200

    bad_payload = transaction_payload()
    bad_payload["description"] = None  # description 可空 → 允许
    assert push(client, account["headers"], [sync_item(payload=bad_payload)]).status_code == 200

    bad_payload2 = transaction_payload()
    bad_payload2["date"] = None  # date 不可空 → 拒绝
    assert push(client, account["headers"], [sync_item(payload=bad_payload2)]).status_code == 422


def test_unknown_entity_type_rejected(client):
    account = make_account(client, "push_badtype")
    response = push(client, account["headers"], [sync_item(entity_type="spaceship")])
    assert response.status_code == 422


def test_empty_push_is_noop(client):
    account = make_account(client, "push_empty")
    response = push(client, account["headers"], [])
    assert response.status_code == 200
    assert response.json() == {"applied": 0, "skipped": 0, "conflicts": [], "server_seq": 0}


def test_tombstone_without_payload_accepted(client):
    account = make_account(client, "push_tomb")
    push(client, account["headers"], [sync_item()])
    tomb = sync_item(updated_at="2026-10-10T07:00:00.000000000Z", deleted=True, payload=None)
    response = push(client, account["headers"], [tomb])
    assert response.status_code == 200
    assert response.json()["applied"] == 1

    item = pull(client, account["headers"]).json()["items"][-1]
    assert item["deleted"] is True
    assert item["payload"] is None


def test_push_batch_size_capped(client):
    account = make_account(client, "push_big")
    items = [sync_item(entity_id=f"e{i}") for i in range(1001)]
    response = push(client, account["headers"], items)
    assert response.status_code == 422
