"""逐实体字段白名单与类型/长度校验（DEC-033「校验不放松」+ DEC-037 列长同源）。

线上协议里 `payload` 是本地 Room 行的 camelCase 原样 JSON；云端把它**拆进业务表的列**
（`models.py`），所以这里除了类型还要卡**长度**：不卡的话超长字符串到 MySQL 上要么被静默
截断（非严格 sql_mode，数据损坏）、要么 500（严格模式）——都该在入口变成带下标的 400。

字段与列长保持同源：`tests/test_typed_storage.py` 会把本文件的 `max_length` 与 ORM 列宽
逐项比对，改列不忘改这里。

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
    #: 字符串最大长度（= 对应列宽）；None 表示不限
    max_length: int | None = None


def _f(type_: type, nullable: bool = False, max_length: int | None = None) -> FieldSpec:
    return FieldSpec(type=type_, nullable=nullable, max_length=max_length)


#: 每类实体的字段白名单（列名 ⇄ Room 实体属性名，原样 camelCase）
ENTITY_SPECS: dict[str, dict[str, FieldSpec]] = {
    "ledger": {
        "id": _f(str, max_length=64),
        "name": _f(str, max_length=128),
        "icon": _f(str, max_length=64),
        "color": _f(str, max_length=32),
        "isDefault": _f(bool),
        "position": _f(int),
        "createdAt": _f(str, max_length=40),
        "updatedAt": _f(str, max_length=64),
    },
    "category": {
        "id": _f(str, max_length=64),
        "ledgerId": _f(str, max_length=64),
        "name": _f(str, max_length=128),
        "icon": _f(str, max_length=64),
        "color": _f(str, max_length=32),
        "type": _f(str, max_length=16),
        "isBuiltIn": _f(bool),
        "order": _f(int),
        "createdAt": _f(str, max_length=40),
        "updatedAt": _f(str, max_length=64),
    },
    "transaction": {
        "id": _f(str, max_length=64),
        "ledgerId": _f(str, max_length=64),
        "type": _f(str, max_length=16),
        "amount": _f(int),  # 分；整数，绝不接受浮点
        "categoryId": _f(str, max_length=64),
        "description": _f(str, nullable=True, max_length=512),
        "date": _f(str, max_length=10),  # yyyy-MM-dd
        "createdAt": _f(str, max_length=40),
        "updatedAt": _f(str, max_length=64),
        "syncStatus": _f(str, max_length=16),
        "externalId": _f(str, nullable=True, max_length=191),
        "counterparty": _f(str, nullable=True, max_length=128),
        "paymentMethod": _f(str, nullable=True, max_length=64),
        "source": _f(str, max_length=16),
    },
    "debt": {
        "id": _f(str, max_length=64),
        "ledgerId": _f(str, max_length=64),
        "partyName": _f(str, max_length=128),
        "amount": _f(int),
        "kind": _f(str, max_length=16),
        "direction": _f(str, max_length=16),
        "date": _f(str, max_length=10),
        "remark": _f(str, nullable=True, max_length=512),
        "createdAt": _f(str, max_length=40),
        "updatedAt": _f(str, max_length=64),
        "syncStatus": _f(str, max_length=16),
    },
    "budget": {
        "id": _f(str, max_length=64),
        "ledgerId": _f(str, max_length=64),
        "month": _f(str, max_length=7),  # yyyy-MM
        "categoryId": _f(str, nullable=True, max_length=64),
        "amount": _f(int),
        "createdAt": _f(str, max_length=40),
        "updatedAt": _f(str, max_length=64),
    },
    "user_category_rule": {
        "id": _f(str, max_length=64),
        "ledgerId": _f(str, max_length=64),
        "source": _f(str, max_length=16),
        "keyword": _f(str, max_length=128),
        "categoryId": _f(str, max_length=64),
        "createdAt": _f(str, max_length=40),
        "updatedAt": _f(str, max_length=64),
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
            if field.max_length is not None and len(value) > field.max_length:
                raise PayloadError(
                    f"字段 {name} 超长：{len(value)} 字符 > 上限 {field.max_length}"
                    f"（列宽，见 entity_spec.ENTITY_SPECS）"
                )


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
