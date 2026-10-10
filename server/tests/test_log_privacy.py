"""TASK-064 挂账项：**服务端日志不得出现 payload 内容**（隐私红线，DEC-034）。

为什么值得单独测：本地库是 SQLCipher 加密的、短信原文与邮箱凭据都不上云，但**日志**是
最容易被忽略的第三个出口——一次「顺手把 request body 打出来」的调试代码，就能让全部账目
（含交易备注）以明文形式落到磁盘与采集系统里。这条断言把该风险钉在测试里。

口径：
- 记录值（备注、金额）**绝不出现**在日志中；
- 字段名（如 ``"description"``）出现即等价于「整个 body 被打了」，同样算违规。
"""

import logging

from tests.conftest import make_account, push, sync_item, transaction_payload

#: 唯一的哨兵串：只要日志里出现它，就说明 payload 被打印了。
SENTINEL = "隐私哨兵-8f3a2b1c9d"
#: 用一个不会自然出现在 id / 序号 / 时间里的金额，作为"数值泄漏"哨兵。
SENTINEL_AMOUNT = 987654321


def _logged_text(caplog) -> str:  # noqa: ANN001
    return "\n".join(f"{r.name}|{r.levelname}|{r.getMessage()}" for r in caplog.records)


def test_push_does_not_log_payload_values_or_field_names(client, caplog):  # noqa: ANN001
    account = make_account(client, "privacy_push_user")
    headers = account["headers"]
    item = sync_item(
        "transaction",
        "44444444-4444-4444-8444-444444444444",
        payload=transaction_payload(
            entity_id="44444444-4444-4444-8444-444444444444",
            description=SENTINEL,
            amount=SENTINEL_AMOUNT,
        ),
    )

    with caplog.at_level(logging.DEBUG):
        response = push(client, headers, [item])

    # 先确认这条路径**真的成功**了，否则"日志里没有"可能只是"根本没走到"。
    assert response.status_code == 200, response.text
    assert response.json()["applied"] == 1

    logged = _logged_text(caplog)
    assert SENTINEL not in logged, "交易备注被写进日志了（隐私红线）"
    assert str(SENTINEL_AMOUNT) not in logged, "交易金额被写进日志了（隐私红线）"
    assert '"description"' not in logged, "疑似把整个 request body 打进日志了"
    assert '"payload"' not in logged, "疑似把整个 request body 打进日志了"


def test_pull_does_not_log_payload_values(client, caplog):  # noqa: ANN001
    account = make_account(client, "privacy_pull_user")
    headers = account["headers"]
    item = sync_item(
        "transaction",
        "55555555-5555-4555-8555-555555555555",
        payload=transaction_payload(
            entity_id="55555555-5555-4555-8555-555555555555",
            description=SENTINEL,
        ),
    )
    assert push(client, headers, [item]).status_code == 200

    with caplog.at_level(logging.DEBUG):
        response = client.get("/api/v1/sync/pull", params={"since_seq": 0, "limit": 200}, headers=headers)

    assert response.status_code == 200, response.text
    # 响应里当然要有（那是给本人看的）；日志里不能有。
    assert SENTINEL in response.text
    logged = _logged_text(caplog)
    assert SENTINEL not in logged, "下行数据被写进日志了（隐私红线）"


def test_validation_error_does_not_log_rejected_values(client, caplog):  # noqa: ANN001
    account = make_account(client, "privacy_invalid_user")
    headers = account["headers"]
    # amount 必须是整数：故意传字符串，走"整批回滚 + 报错"这条最容易顺带打日志的路径。
    bad_payload = transaction_payload(
        entity_id="66666666-6666-4666-8666-666666666666",
        description=SENTINEL,
    )
    bad_payload["amount"] = "not-an-int"
    item = sync_item("transaction", "66666666-6666-4666-8666-666666666666", payload=bad_payload)

    with caplog.at_level(logging.DEBUG):
        response = push(client, headers, [item])

    assert response.status_code >= 400, response.text
    logged = _logged_text(caplog)
    assert SENTINEL not in logged, "被拒条目的备注被写进日志了（隐私红线）"
    assert "not-an-int" not in logged, "被拒条目的原始值被写进日志了（隐私红线）"
