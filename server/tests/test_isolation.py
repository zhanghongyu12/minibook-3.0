"""A2：越权隔离 —— 账号之间必须完全看不见彼此的行（DEC-032 硬约束）。"""

from tests.conftest import LEDGER_ID, make_account, pull, push, sync_item


def test_pull_never_returns_other_users_rows(client):
    a = make_account(client, "iso_a")
    b = make_account(client, "iso_b")

    assert push(client, a["headers"], [sync_item()]).status_code == 200

    a_items = pull(client, a["headers"]).json()["items"]
    b_items = pull(client, b["headers"]).json()["items"]
    assert len(a_items) == 1
    assert b_items == []


def test_same_entity_id_stays_separate_per_user(client):
    a = make_account(client, "dup_a")
    b = make_account(client, "dup_b")

    assert push(client, a["headers"], [sync_item()]).json()["applied"] == 1
    assert push(client, b["headers"], [sync_item()]).json()["applied"] == 1

    a_items = pull(client, a["headers"]).json()["items"]
    b_items = pull(client, b["headers"]).json()["items"]
    assert len(a_items) == 1 and len(b_items) == 1
    # 各行独立：A 只能看到自己那条（entity_id 相同但属于不同用户）
    assert a_items[0]["entity_id"] == b_items[0]["entity_id"]
    # server_seq 是**全局**单调计数器（跨用户共享），故游标只会前进；关键是各自只看到自己的行
    assert a_items[0]["server_seq"] == 1
    assert b_items[0]["server_seq"] == 2


def test_forged_identity_header_is_ignored(client):
    a = make_account(client, "forge_a")
    b = make_account(client, "forge_b")

    forged = {**b["headers"], "X-User-Id": a["user"]["user_id"]}
    push(client, a["headers"], [sync_item(entity_id="aaaa-1")])
    assert pull(client, forged).json()["items"] == []


def test_status_counts_are_per_user(client):
    a = make_account(client, "stat_a")
    b = make_account(client, "stat_b")

    push(client, a["headers"], [sync_item(entity_id="s1")])
    push(client, a["headers"], [sync_item(entity_type="ledger", entity_id=LEDGER_ID)])

    a_status = client.get("/api/v1/sync/status", headers=a["headers"]).json()
    b_status = client.get("/api/v1/sync/status", headers=b["headers"]).json()
    assert a_status["counts"] == {"transaction": 1, "ledger": 1}
    assert b_status["counts"] == {}
    # 本账号游标上限：A 有 2 条 → 2；B 没有 → 0（全局计数器两边相同，仅诊断用）
    assert a_status["server_seq"] == 2
    assert b_status["server_seq"] == 0
    assert a_status["server_seq_global"] == b_status["server_seq_global"] == 2
