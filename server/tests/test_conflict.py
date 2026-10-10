"""A4：冲突 —— 服务端更新则拒绝并回传服务端版本（LWW，客户端负责留档输家）。"""

from tests.conftest import make_account, pull, push, sync_item


def test_older_push_conflicts_with_server_version(client):
    account = make_account(client, "conflict_old")
    newest = sync_item(updated_at="2026-10-10T08:00:00.000000000Z")
    newest["payload"]["amount"] = 200
    assert push(client, account["headers"], [newest]).json()["applied"] == 1

    older = sync_item(updated_at="2026-10-10T07:00:00.000000000Z")
    older["payload"]["amount"] = 100
    result = push(client, account["headers"], [older]).json()

    assert result["applied"] == 0
    assert result["skipped"] == 0
    assert len(result["conflicts"]) == 1
    conflict = result["conflicts"][0]
    assert conflict["reason"] == "server_newer"
    assert conflict["server"]["payload"]["amount"] == 200
    assert conflict["server"]["updated_at"] == "2026-10-10T08:00:00.000000000Z"
    assert result["server_seq"] == 1  # 冲突不推进游标

    assert pull(client, account["headers"]).json()["items"][0]["payload"]["amount"] == 200


def test_newer_push_wins_without_conflict(client):
    account = make_account(client, "conflict_new")
    push(client, account["headers"], [sync_item(updated_at="2026-10-10T08:00:00.000000000Z")])

    newer = sync_item(updated_at="2026-10-10T09:00:00.000000000Z")
    newer["payload"]["amount"] = 300
    result = push(client, account["headers"], [newer]).json()
    assert result["conflicts"] == []
    assert result["applied"] == 1
    assert pull(client, account["headers"]).json()["items"][-1]["payload"]["amount"] == 300


def test_stale_tombstone_conflicts_with_newer_edit(client):
    """别的设备已经改过（更新），此时旧墓碑不该把新内容删掉。"""
    account = make_account(client, "conflict_tomb")
    push(client, account["headers"], [sync_item(updated_at="2026-10-10T09:00:00.000000000Z")])

    stale_tombstone = sync_item(updated_at="2026-10-10T08:00:00.000000000Z", deleted=True, payload=None)
    result = push(client, account["headers"], [stale_tombstone]).json()
    assert len(result["conflicts"]) == 1
    assert result["conflicts"][0]["server"]["deleted"] is False

    items = pull(client, account["headers"]).json()["items"]
    assert items[-1]["deleted"] is False


def test_conflict_and_new_record_in_one_batch(client):
    account = make_account(client, "conflict_mixed")
    push(client, account["headers"], [sync_item(updated_at="2026-10-10T09:00:00.000000000Z")])

    stale = sync_item(updated_at="2026-10-10T08:00:00.000000000Z")
    fresh = sync_item(entity_id="brand-new", updated_at="2026-10-10T10:00:00.000000000Z")
    result = push(client, account["headers"], [stale, fresh]).json()

    assert result["applied"] == 1
    assert len(result["conflicts"]) == 1
    assert result["conflicts"][0]["entity_id"] != "brand-new"
