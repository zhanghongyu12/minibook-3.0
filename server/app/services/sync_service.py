"""同步合并逻辑（DEC-033）：push / pull / status。

三条硬口径：
1. **幂等**：`entity_id` = 客户端 UUID 主键；同一 `updated_at` 重复 push = no-op（**不涨 server_seq**）；
2. **整批原子**：任一条校验失败 → 抛 `validation_error`，事务未提交 ⇒ 全部回滚，不留半同步状态；
3. **LWW**：客户端更新则覆盖并分配新 `server_seq`；服务端更新则返回 `conflict`（客户端留档自己的副本，绝不静默丢）。
"""

from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from ..entity_spec import PayloadError
from ..errors import ApiError
from ..models import SyncCounter, SyncRecord, User
from ..schemas import (
    PullResponse,
    PushConflict,
    PushRequest,
    PushResponse,
    ServerItem,
    StatusResponse,
)
from ..timeutil import parse_iso_utc, to_iso_utc, utcnow

COUNTER_NAME = "server_seq"


def next_seq(db: Session) -> int:
    """原子自增并返回新 `server_seq`（`UPDATE … value+1` 取行锁；SQLite/MySQL 通用）。"""
    result = db.execute(
        update(SyncCounter).where(SyncCounter.name == COUNTER_NAME).values(value=SyncCounter.value + 1)
    )
    if result.rowcount != 1:
        raise ApiError("server_error", "server_seq 计数器缺失，请先执行 alembic upgrade head")
    return int(db.execute(select(SyncCounter.value).where(SyncCounter.name == COUNTER_NAME)).scalar_one())


def current_seq(db: Session) -> int:
    value = db.execute(select(SyncCounter.value).where(SyncCounter.name == COUNTER_NAME)).scalar_one_or_none()
    return int(value or 0)


def to_server_item(record: SyncRecord) -> ServerItem:
    return ServerItem(
        entity_type=record.entity_type,
        entity_id=record.entity_id,
        ledger_id=record.ledger_id,
        updated_at=record.updated_at_raw,
        deleted=record.deleted,
        payload=record.payload,
        server_seq=record.server_seq,
    )


def push(db: Session, user: User, request: PushRequest) -> PushResponse:
    # 1) 先整批校验：错误在写入前暴露，保证「整批回滚」语义干净
    for index, item in enumerate(request.items):
        try:
            item.validate_against_spec()
        except PayloadError as exc:
            raise ApiError(
                "validation_error",
                f"第 {index} 条（{item.entity_type}/{item.entity_id}）校验失败：{exc}",
                extra={"index": index, "entity_id": item.entity_id},
            ) from exc

    applied = 0
    skipped = 0
    conflicts: list[PushConflict] = []

    # 2) 逐条应用（每条变更独占一个 server_seq；no-op 不占号）
    for item in request.items:
        existing = db.get(SyncRecord, (user.id, item.entity_type, item.entity_id))
        incoming_dt = parse_iso_utc(item.updated_at)

        if existing is None:
            db.add(
                SyncRecord(
                    user_id=user.id,
                    entity_type=item.entity_type,
                    entity_id=item.entity_id,
                    ledger_id=item.ledger_id,
                    updated_at=incoming_dt,
                    updated_at_raw=item.updated_at,
                    deleted=item.deleted,
                    payload=item.payload,
                    server_seq=next_seq(db),
                    server_updated_at=utcnow(),
                )
            )
            applied += 1
            continue

        if existing.updated_at_raw == item.updated_at:
            skipped += 1  # 幂等：同版本重复提交
            continue

        if incoming_dt > existing.updated_at:
            existing.ledger_id = item.ledger_id
            existing.updated_at = incoming_dt
            existing.updated_at_raw = item.updated_at
            existing.deleted = item.deleted
            existing.payload = item.payload
            existing.server_seq = next_seq(db)
            existing.server_updated_at = utcnow()
            applied += 1
            continue

        conflicts.append(
            PushConflict(
                entity_type=item.entity_type,
                entity_id=item.entity_id,
                reason="server_newer",
                server=to_server_item(existing),
            )
        )

    db.commit()
    return PushResponse(
        applied=applied,
        skipped=skipped,
        conflicts=conflicts,
        server_seq=current_seq(db),
    )


def pull(db: Session, user: User, since_seq: int, limit: int) -> PullResponse:
    rows = (
        db.execute(
            select(SyncRecord)
            .where(SyncRecord.user_id == user.id, SyncRecord.server_seq > since_seq)
            .order_by(SyncRecord.server_seq.asc())
            .limit(limit + 1)
        )
        .scalars()
        .all()
    )
    has_more = len(rows) > limit
    page = rows[:limit]
    next_seq = page[-1].server_seq if page else since_seq
    return PullResponse(
        items=[to_server_item(record) for record in page],
        next_seq=next_seq,
        has_more=has_more,
    )


def status(db: Session, user: User) -> StatusResponse:
    counts = {
        entity_type: int(count)
        for entity_type, count in db.execute(
            select(SyncRecord.entity_type, func.count())
            .where(SyncRecord.user_id == user.id)
            .group_by(SyncRecord.entity_type)
        ).all()
    }
    # 本账号自己的游标上限（客户端拉完后应达到的值）；全局计数器另给字段，避免混淆
    user_max_seq = db.execute(
        select(func.max(SyncRecord.server_seq)).where(SyncRecord.user_id == user.id)
    ).scalar_one_or_none()
    return StatusResponse(
        server_seq=int(user_max_seq or 0),
        server_seq_global=current_seq(db),
        counts=counts,
        server_time=to_iso_utc(utcnow()) or "",
    )
