"""payload ⇄ 业务表列的映射与「拆列 / 拼回」（DEC-037）。

云端存储从「一张通用表 + JSON」换成**六张业务表**后，需要一层薄映射把
线上协议（`payload` = 本地 Room 行的 camelCase 原样 JSON）与业务表列对上：

- **拆列** `shred()`：校验通过的 payload → 列值（入库）；
- **拼回** `reassemble()`：业务表行 → payload（拉取时回给客户端，**逐字回原样**）。

这样「云端有明确的表」（能用 SQL 查账）与「Android 零改动」（仍发/收 payload）同时成立。

**单一事实来源**：字段白名单/类型/长度在 `entity_spec.ENTITY_SPECS`，payload key → 列名在本模块
`COLUMN_MAPS`，列本身在 `models`。三者由
`tests/test_typed_storage.py::test_column_maps_match_spec_and_models` 钉死：谁加了字段忘了改另一边，
测试直接红。

信封驱动的三个 key（`id` / `ledgerId` / `updatedAt`）**不**进 `COLUMN_MAPS`：它们分别对应
主键列、`ledger_id` 列、`updated_at_raw` 列，且必须与 HTTP 信封逐字一致
（`entity_spec.assert_envelope_consistency` 已强制），由 `sync_service` 统一写入。
"""

from typing import Any

from ..entity_spec import ENTITY_SPECS, PayloadError
from ..models import Budget, Category, Debt, Ledger, Transaction, UserCategoryRule

#: entity_type → ORM 模型
TYPED_MODELS: dict[str, type[Any]] = {
    "ledger": Ledger,
    "category": Category,
    "transaction": Transaction,
    "debt": Debt,
    "budget": Budget,
    "user_category_rule": UserCategoryRule,
}

#: 由信封统一驱动的 payload key（不在 COLUMN_MAPS 里）
ENVELOPE_KEY_TO_COLUMN: dict[str, str] = {
    "id": "id",
    "ledgerId": "ledger_id",
    "updatedAt": "updated_at_raw",
}

#: entity_type → {payload key: ORM 属性名}（**不含**信封 key）
COLUMN_MAPS: dict[str, dict[str, str]] = {
    "ledger": {
        "name": "name",
        "icon": "icon",
        "color": "color",
        "isDefault": "is_default",
        "position": "position",
        "createdAt": "created_at_raw",
    },
    "category": {
        "name": "name",
        "icon": "icon",
        "color": "color",
        "type": "type",
        "isBuiltIn": "is_built_in",
        "order": "sort_order",  # `order` 是 MySQL 保留字，列名改 sort_order
        "createdAt": "created_at_raw",
    },
    "transaction": {
        "type": "type",
        "amount": "amount",
        "categoryId": "category_id",
        "description": "description",
        "date": "date",
        "syncStatus": "sync_status",
        "externalId": "external_id",
        "counterparty": "counterparty",
        "paymentMethod": "payment_method",
        "source": "source",
        "createdAt": "created_at_raw",
    },
    "debt": {
        "partyName": "party_name",
        "amount": "amount",
        "kind": "kind",
        "direction": "direction",
        "date": "date",
        "remark": "remark",
        "syncStatus": "sync_status",
        "createdAt": "created_at_raw",
    },
    "budget": {
        "month": "month",
        "categoryId": "category_id",
        "amount": "amount",
        "createdAt": "created_at_raw",
    },
    "user_category_rule": {
        "source": "source",
        "keyword": "keyword",
        "categoryId": "category_id",
        "createdAt": "created_at_raw",
    },
}


def model_for(entity_type: str) -> type[Any]:
    model = TYPED_MODELS.get(entity_type)
    if model is None:
        raise PayloadError(f"未知实体类型: {entity_type}")
    return model


def shred(entity_type: str, payload: dict[str, Any]) -> dict[str, Any]:
    """payload → 列值（**只含业务列**；`ledger_id`/时间戳/序号由调用方填）。

    调用前必须已过 `entity_spec.validate_payload`（类型/长度都验过），这里只做搬运。

    **必须用 `.get` 而不是 `[...]`**：可空字段客户端可能整个省略不写
    （Kotlin 侧 `explicitNulls = false` ⇒ null 不进 JSON），缺省即 None。
    实测踩坑（2026-10-10）：历史 payload 缺 `externalId`，用下标取值让迁移回填把整行
    判成脏数据跳过。
    """
    mapping = COLUMN_MAPS[entity_type]
    return {column: payload.get(key) for key, column in mapping.items()}


def reassemble(entity_type: str, row: Any) -> dict[str, Any]:
    """业务表行 → payload（按 spec 顺序、含全部 key；可空列为 None）。

    顺序固定为 `ENTITY_SPECS` 的声明顺序 —— 与本地实体构造顺序一致，便于人工比对。
    `bool` 列显式转 `bool`：SQLite 存 0/1，回 JSON 必须是 true/false（客户端是 Boolean 列）。
    """
    spec = ENTITY_SPECS[entity_type]
    mapping = COLUMN_MAPS[entity_type]
    payload: dict[str, Any] = {}
    for key in spec:
        if key in ENVELOPE_KEY_TO_COLUMN:
            payload[key] = getattr(row, ENVELOPE_KEY_TO_COLUMN[key])
            continue
        value = getattr(row, mapping[key])
        if spec[key].type is bool and value is not None:
            value = bool(value)
        payload[key] = value
    return payload
