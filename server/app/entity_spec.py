"""逐实体字段白名单与类型校验（DEC-033 的「校验不放松」）。

云端用通用记录表存 `payload` JSON，但**绝不接受脏数据**：每个 `entity_type` 的字段名、
类型、可空性都在此声明，照抄 Android Room 实体列（camelCase 原样），未知字段一律拒绝。

对应 Room 表：
- ledger            → data/local/db/entity/LedgerEntity.kt
- category          → CategoryEntity.kt
- transaction       → TransactionEntity.kt（F-12 后为 14 列）
- debt              → DebtEntity.kt
- budget            → BudgetEntity.kt
- user_category_rule→ UserCategoryRuleEntity.kt
"""

from dataclasses import dataclass
from typing import Any

ENTITY_TYPES: tuple[str, ...] = (
    "ledger",
    "category",
    "transaction",
    "debt",
    "budget",
    "user_category_rule",
)


@dataclass(frozen=True)
class FieldSpec:
    type: type
    nullable: bool = False


def _f(type_: type, nullable: bool = False) -> FieldSpec:
    return FieldSpec(type=type_, nullable=nullable)


#: 每类实体的字段白名单（列名 ⇄ Room 实体属性名，原样 camelCase）
ENTITY_SPECS: dict[str, dict[str, FieldSpec]] = {
    "ledger": {
        "id": _f(str),
        "name": _f(str),
        "icon": _f(str),
        "color": _f(str),
        "isDefault": _f(bool),
        "position": _f(int),
        "createdAt": _f(str),
        "updatedAt": _f(str),
    },
    "category": {
        "id": _f(str),
        "ledgerId": _f(str),
        "name": _f(str),
        "icon": _f(str),
        "color": _f(str),
        "type": _f(str),
        "isBuiltIn": _f(bool),
        "order": _f(int),
        "createdAt": _f(str),
        "updatedAt": _f(str),
    },
    "transaction": {
        "id": _f(str),
        "ledgerId": _f(str),
        "type": _f(str),
        "amount": _f(int),  # 分；整数，绝不接受浮点
        "categoryId": _f(str),
        "description": _f(str, nullable=True),
        "date": _f(str),  # yyyy-MM-dd
        "createdAt": _f(str),
        "updatedAt": _f(str),
        "syncStatus": _f(str),
        "externalId": _f(str, nullable=True),
        "counterparty": _f(str, nullable=True),
        "paymentMethod": _f(str, nullable=True),
        "source": _f(str),
    },
    "debt": {
        "id": _f(str),
        "ledgerId": _f(str),
        "partyName": _f(str),
        "amount": _f(int),
        "kind": _f(str),
        "direction": _f(str),
        "date": _f(str),
        "remark": _f(str, nullable=True),
        "createdAt": _f(str),
        "updatedAt": _f(str),
        "syncStatus": _f(str),
    },
    "budget": {
        "id": _f(str),
        "ledgerId": _f(str),
        "month": _f(str),  # yyyy-MM
        "categoryId": _f(str, nullable=True),
        "amount": _f(int),
        "createdAt": _f(str),
        "updatedAt": _f(str),
    },
    "user_category_rule": {
        "id": _f(str),
        "ledgerId": _f(str),
        "source": _f(str),
        "keyword": _f(str),
        "categoryId": _f(str),
        "createdAt": _f(str),
        "updatedAt": _f(str),
    },
}


class PayloadError(ValueError):
    """payload 校验失败（由同步服务包装成带条目下标的 validation_error）。"""


def validate_payload(entity_type: str, payload: Any) -> None:
    """校验一条记录的 payload，失败抛 [PayloadError]（附具体原因）。"""
    spec = ENTITY_SPECS.get(entity_type)
    if spec is None:
        raise PayloadError(f"未知实体类型: {entity_type}")

    if payload is None:
        raise PayloadError("payload 不能为空（墓碑请显式 deleted=true 并省略 payload）")
    if not isinstance(payload, dict):
        raise PayloadError(f"payload 必须是对象，实际为 {type(payload).__name__}")

    unknown = sorted(set(payload) - set(spec))
    if unknown:
        raise PayloadError(f"未知字段: {', '.join(unknown)}")

    missing = sorted(name for name, field in spec.items() if not field.nullable and name not in payload)
    if missing:
        raise PayloadError(f"缺少必填字段: {', '.join(missing)}")

    for name, value in payload.items():
        field = spec[name]
        if value is None:
            if not field.nullable:
                raise PayloadError(f"字段 {name} 不可为 null")
            continue
        if field.type is int:
            # bool 是 int 的子类：金额/序号绝不接受 true/false，也不接受浮点
            if isinstance(value, bool) or not isinstance(value, int):
                raise PayloadError(f"字段 {name} 必须是整数（分），实际为 {type(value).__name__}")
        elif field.type is bool:
            if not isinstance(value, bool):
                raise PayloadError(f"字段 {name} 必须是布尔，实际为 {type(value).__name__}")
        elif field.type is str:
            if not isinstance(value, str):
                raise PayloadError(f"字段 {name} 必须是字符串，实际为 {type(value).__name__}")


def assert_envelope_consistency(
    entity_type: str,
    entity_id: str,
    ledger_id: str,
    updated_at: str,
    payload: dict[str, Any] | None,
) -> None:
    """信封与 payload 必须一致（防客户端 bug 造成「元数据与内容两套说法」）。"""
    if payload is None:
        return
    checks = (
        ("id", entity_id),
        ("ledgerId", ledger_id),
        ("updatedAt", updated_at),
    )
    for key, expected in checks:
        actual = payload.get(key)
        if actual is not None and actual != expected:
            raise PayloadError(f"信封与 payload 不一致: {key}={actual!r} 而信封为 {expected!r}")
