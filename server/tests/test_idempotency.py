"""A3：幂等 —— 同一批重复 push 不得产生重复行、不得推进 server_seq。"""

from tests.conftest import LEDGER_ID, make_account, pull, push, sync_item


def test_repeated_push_is_noop(client):
    account = make_account(client, "idem_same")
    items = [sync_item(entity_type="ledger", entity_id=LEDGER_ID), sync_item()]

    first = push(client, account["headers"], items).json()
    second = push(client, account["headers"], items).json()

    assert first["applied"] == 2 and first["skipped"] == 0
    assert second["applied"] == 0 and second["skipped"] == 2
    assert second["server_seq"] == first["server_seq"] == 2
    assert len(pull(client, account["headers"]).json()["items"]) == 2


def test_same_updated_at_with_changed_content_is_still_noop(client):
    """客户端若改了内容却没推进 updatedAt（客户端 bug），服务端按同版本处理，不覆盖。"""
    account = make_account(client, "idem_guard")
    push(client, account["headers"], [sync_item()])
    changed = sync_item()
    changed["payload"]["amount"] = 999

    result = push(client, account["headers"], [changed]).json()
    assert result["applied"] == 0 and result["skipped"] == 1
    assert pull(client, account["headers"]).json()["items"][0]["payload"]["amount"] == 12345


def test_newer_updated_at_advances_seq_once(client):
    account = make_account(client, "idem_newer")
    push(client, account["headers"], [sync_item()])
    updated = sync_item(updated_at="2026-10-10T07:30:00.000000000Z")
    updated["payload"]["amount"] = 777
    result = push(client, account["headers"], [updated]).json()

    assert result["applied"] == 1 and result["skipped"] == 0
    assert result["server_seq"] == 2
    item = pull(client, account["headers"]).json()["items"][-1]
    assert item["payload"]["amount"] == 777
    assert item["updated_at"] == "2026-10-10T07:30:00.000000000Z"
