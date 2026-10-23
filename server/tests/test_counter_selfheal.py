"""计数器行缺失自愈的回归（2026-10-23 线上事故）。

事故：一次「清空所有表行」把 `sync_counters` 的种子行（和 `alembic_version`）一起删了，
`next_seq()` 的 `UPDATE` 影响 0 行 ⇒ 抛 500「计数器缺失」⇒ pull 正常但**所有账号 push 全废**。
本文件锁两条口径：
1. 行缺失时 push **必须自愈**并继续发号（不能 500，也不能从 0 重发——那会撞 `sync_log` 主键）；
2. 补种值 = `MAX(sync_log.server_seq)`，空库（无日志）时从 1 开始。
"""

from sqlalchemy import text

from app.services.sync_service import current_seq, next_seq
from tests.conftest import make_account, pull, push, sync_item

COUNTER_SQL = "SELECT value FROM sync_counters WHERE name='server_seq'"


def _counter_value(engine) -> int:  # noqa: ANN001
    with engine.connect() as connection:
        return int(connection.execute(text(COUNTER_SQL)).scalar_one())


def _drop_counter(engine) -> None:  # noqa: ANN001
    with engine.begin() as connection:
        connection.execute(text("DELETE FROM sync_counters"))


def test_push_selfheals_missing_counter_and_continues_sequence(client, engine):
    account = make_account(client, "counter_heal")
    first = push(client, account["headers"], [sync_item()])
    assert first.status_code == 200, first.text
    assert first.json()["server_seq"] == 1

    # 模拟「清空所有表行」：计数器行被一起删掉，但 sync_log 里已存在 seq=1
    _drop_counter(engine)
    with engine.connect() as connection:
        assert connection.execute(text(COUNTER_SQL)).scalar_one_or_none() is None

    # 再推一条新变更 ⇒ 自愈；续号到 2（若从 0 重发会拿到 1 并撞 sync_log 主键）
    second = push(
        client,
        account["headers"],
        [
            sync_item(
                entity_id="44444444-4444-4444-8444-444444444444",
                updated_at="2026-10-11T00:00:00.000000000Z",
            )
        ],
    )
    assert second.status_code == 200, second.text
    body = second.json()
    assert body["applied"] == 1
    assert body["conflicts"] == []
    assert body["server_seq"] == 2

    # 补种行已落地且值正确；两条数据都能拉回
    assert _counter_value(engine) == 2
    assert len(pull(client, account["headers"]).json()["items"]) == 2


def test_next_seq_seeds_empty_log_from_one(session_factory, engine):
    """空库补种：无 sync_log 时价值为 0 ⇒ 发号从 1 开始，且读路径同口径。"""
    _drop_counter(engine)
    db = session_factory()
    try:
        assert current_seq(db) == 0  # 读路径容忍缺行（事故时 pull/status 正常的原因）
        assert next_seq(db) == 1
        assert current_seq(db) == 1
        db.commit()
    finally:
        db.close()
    assert _counter_value(engine) == 1
