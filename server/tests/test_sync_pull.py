"""A5 / A6：pull 增量、分页、墓碑下发。"""

from tests.conftest import make_account, pull, push, sync_item


def _seed(client, headers, count: int) -> None:
    items = [
        sync_item(entity_id=f"tx-{index:03d}", updated_at=f"2026-10-10T06:{index:02d}:00.000000000Z")
        for index in range(count)
    ]
    assert push(client, headers, items).json()["applied"] == count


def test_incremental_pull_uses_server_seq(client):
    account = make_account(client, "pull_incr")
    _seed(client, account["headers"], 3)

    page = pull(client, account["headers"], since_seq=0).json()
    assert [item["server_seq"] for item in page["items"]] == [1, 2, 3]
    assert page["next_seq"] == 3
    assert page["has_more"] is False

    assert pull(client, account["headers"], since_seq=page["next_seq"]).json()["items"] == []

    push(client, account["headers"], [sync_item(entity_id="tx-099", updated_at="2026-10-10T09:00:00.000000000Z")])
    after = pull(client, account["headers"], since_seq=3).json()
    assert [item["entity_id"] for item in after["items"]] == ["tx-099"]
    assert after["next_seq"] == 4


def test_pagination_has_no_gaps_or_duplicates(client):
    account = make_account(client, "pull_pages")
    _seed(client, account["headers"], 5)

    seen: list[str] = []
    cursor = 0
    for _ in range(10):  # 最多 10 页，防死循环
        page = pull(client, account["headers"], since_seq=cursor, limit=2).json()
        seen.extend(item["entity_id"] for item in page["items"])
        cursor = page["next_seq"]
        if not page["has_more"]:
            break
    assert seen == ["tx-000", "tx-001", "tx-002", "tx-003", "tx-004"]
    assert len(seen) == len(set(seen))


def test_limit_is_clamped_to_max(client):
    account = make_account(client, "pull_clamp")
    _seed(client, account["headers"], 2)
    page = pull(client, account["headers"], limit=99999).json()
    assert len(page["items"]) == 2  # 不报错，取到全部（上限 500 由服务端钳制）


def test_tombstone_is_delivered_to_other_device(client):
    account = make_account(client, "pull_tomb")
    _seed(client, account["headers"], 1)
    assert push(
        client,
        account["headers"],
        [sync_item(updated_at="2026-10-10T08:00:00.000000000Z", deleted=True, payload=None)],
    ).json()["applied"] == 1

    items = pull(client, account["headers"]).json()["items"]
    assert len(items) == 2
    assert items[0]["deleted"] is False
    assert items[1]["deleted"] is True


def test_pull_requires_auth(client):
    assert client.get("/api/v1/sync/pull").status_code == 401
