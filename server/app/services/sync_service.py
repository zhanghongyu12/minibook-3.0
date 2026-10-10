"""同步合并逻辑（DEC-033 语义 + DEC-037 存储）：push / pull / status。

三条硬口径（与旧版逐字一致，**没动协议也没动语义**）：
1. **幂等**：`entity_id` = 客户端 UUID 主键；同一 `updated_at` 重复 push = no-op（**不涨 server_seq**）；
2. **整批原子**：任一条校验失败 → 抛 `validation_error`，事务未提交 ⇒ 全部回滚，不留半同步状态；
3. **LWW**：客户端更新则覆盖并分配新 `server_seq`；服务端更新则返回 `conflict`（客户端留档自己的副本，绝不静默丢）。

存储形态（DEC-037）：数据落**六张业务表**（`services/entity_store.py` 负责 payload ⇄ 列的拆/拼），
`sync_log` 只记「谁在第几号变更里被改/被删」——
- 拉取 = 扫 `sync_log`（按 `server_seq` 增量、天然有序），活实体再去业务表取行拼回 payload；
- 墓碑 = `sync_log` 里 `deleted=1` 的一条，业务表**物理删行**（业务表里只有活数据）；
- 「实体当前状态」= 业务表有行就用行，否则看该实体最后一条日志（墓碑）。
"""

from typing import Any, NamedTuple

from sqlalchemy import delete as sa_delete
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from ..entity_spec import ENTITY_TYPES, PayloadError
from ..errors import ApiError
from ..models import SyncCounter, SyncLog, User
from ..schemas import (
    PullResponse,
    PushConflict,
    PushRequest,
    PushResponse,
    ServerItem,
    StatusResponse,
)
from ..timeutil import parse_iso_utc, to_iso_utc, utcnow
from .entity_store import model_for, reassemble, shred

COUNTER_NAME = "server_seq"


class _State(NamedTuple):
    """某实体在云端的当前状态；从未同步过时全部为 None/0。"""

    updated_at_raw: str | None
    updated_at: Any
    deleted: bool
    payload: dict[str, Any] | None
    server_seq: int
    ledger_id: str | None


_UNKNOWN = _State(None, None, False, None, 0, None)


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


def _latest_log(db: Session, user_id: str, entity_type: str, entity_id: str) -> SyncLog | None:
    return (
        db.execute(
            select(SyncLog)
            .where(
                SyncLog.user_id == user_id,
                SyncLog.entity_type == entity_type,
                SyncLog.entity_id == entity_id,
            )
            .order_by(SyncLog.server_seq.desc())
            .limit(1)
        )
        .scalars()
        .first()
    )


def _append_log(
    db: Session,
    *,
    user_id: str,
    entity_type: str,
    entity_id: str,
    ledger_id: str,
    deleted: bool,
    updated_at_raw: str,
    updated_at: Any,
    seq: int,
) -> None:
    db.add(
        SyncLog(
            user_id=user_id,
            server_seq=seq,
            entity_type=entity_type,
            entity_id=entity_id,
            ledger_id=ledger_id,
            deleted=deleted,
            updated_at_raw=updated_at_raw,
            updated_at=updated_at,
            logged_at=utcnow(),
        )
    )


def _current_state(db: Session, user_id: str, entity_type: str, entity_id: str) -> _State:
    """业务表有行 → 活实体；否则看最后一条日志是不是墓碑。

    「日志里有一条活记录但业务表没有行」只可能是历史脏数据 —— 当作**从未同步**处理，
    让客户端重新推一次即可（绝不返回 `deleted=False, payload=None` 这种自相矛盾的状态）。
    """
    model = model_for(entity_type)
    row = db.get(model, (user_id, entity_id))
    if row is not None:
        return _State(
            updated_at_raw=row.updated_at_raw,
            updated_at=row.updated_at,
            deleted=False,
            payload=reassemble(entity_type, row),
            server_seq=row.server_seq,
            ledger_id=row.ledger_id,
        )

    log = _latest_log(db, user_id, entity_type, entity_id)
    if log is not None and log.deleted:
        return _State(
            updated_at_raw=log.updated_at_raw,
            updated_at=log.updated_at,
            deleted=True,
            payload=None,
            server_seq=log.server_seq,
            ledger_id=log.ledger_id,
        )
    return _UNKNOWN


def _apply(db: Session, *, user_id: str, item: Any, incoming_dt: Any, seq: int) -> None:
    """把一条推送落库：活实体写业务表，墓碑删业务表行；两者都追加日志。"""
    model = model_for(item.entity_type)

    if item.deleted:
        db.execute(sa_delete(model).where(model.user_id == user_id, model.id == item.entity_id))
    else:
        values = shred(item.entity_type, item.payload)
        values["ledger_id"] = item.ledger_id
        values["updated_at_raw"] = item.updated_at
        values["updated_at"] = incoming_dt
        values["server_seq"] = seq
        values["server_updated_at"] = utcnow()

        row = db.get(model, (user_id, item.entity_id))
        if row is None:
            db.add(model(user_id=user_id, id=item.entity_id, **values))
        else:
            for column, value in values.items():
                setattr(row, column, value)

    _append_log(
        db,
        user_id=user_id,
        entity_type=item.entity_type,
        entity_id=item.entity_id,
        ledger_id=item.ledger_id,
        deleted=item.deleted,
        updated_at_raw=item.updated_at,
        updated_at=incoming_dt,
        seq=seq,
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
        incoming_dt = parse_iso_utc(item.updated_at)
        state = _current_state(db, user.id, item.entity_type, item.entity_id)

        if state.updated_at_raw is None:
            _apply(db, user_id=user.id, item=item, incoming_dt=incoming_dt, seq=next_seq(db))
            applied += 1
            continue

        if state.updated_at_raw == item.updated_at:
            skipped += 1  # 幂等：同版本重复提交
            continue

        if incoming_dt > state.updated_at:
            _apply(db, user_id=user.id, item=item, incoming_dt=incoming_dt, seq=next_seq(db))
            applied += 1
            continue

        conflicts.append(
            PushConflict(
                entity_type=item.entity_type,
                entity_id=item.entity_id,
                reason="server_newer",
                server=ServerItem(
                    entity_type=item.entity_type,
                    entity_id=item.entity_id,
                    ledger_id=state.ledger_id or item.ledger_id,
                    updated_at=state.updated_at_raw,
                    deleted=state.deleted,
                    payload=state.payload,
                    server_seq=state.server_seq,
                ),
            )
        )

    db.commit()
    return PushResponse(
        applied=applied,
        skipped=skipped,
        conflicts=conflicts,
        server_seq=current_seq(db),
    )


def _live_rows_by_key(db: Session, user_id: str, page: list[SyncLog]) -> dict[tuple[str, str], Any]:
    """按 `entity_type` 批量取活行（避免每条日志一次查询的 N+1）。"""
    ids_by_type: dict[str, set[str]] = {}
    for entry in page:
        if not entry.deleted:
            ids_by_type.setdefault(entry.entity_type, set()).add(entry.entity_id)

    rows: dict[tuple[str, str], Any] = {}
    for entity_type, ids in ids_by_type.items():
        if entity_type not in ENTITY_TYPES or not ids:
            continue
        model = model_for(entity_type)
        for row in db.execute(
            select(model).where(model.user_id == user_id, model.id.in_(sorted(ids)))
        ).scalars():
            rows[(entity_type, row.id)] = row
    return rows


def pull(db: Session, user: User, since_seq: int, limit: int) -> PullResponse:
    rows = (
        db.execute(
            select(SyncLog)
            .where(SyncLog.user_id == user.id, SyncLog.server_seq > since_seq)
            .order_by(SyncLog.server_seq.asc())
            .limit(limit + 1)
        )
        .scalars()
        .all()
    )
    has_more = len(rows) > limit
    page = rows[:limit]
    next_seq = page[-1].server_seq if page else since_seq

    live = _live_rows_by_key(db, user.id, page)
    items: list[ServerItem] = []
    for entry in page:
        row = live.get((entry.entity_type, entry.entity_id))
        if entry.deleted:
            items.append(
                ServerItem(
                    entity_type=entry.entity_type,
                    entity_id=entry.entity_id,
                    ledger_id=entry.ledger_id,
                    updated_at=entry.updated_at_raw,
                    deleted=True,
                    payload=None,
                    server_seq=entry.server_seq,
                )
            )
            continue
        if row is None:
            # 活记录但业务表已无行：说明该实体在本次分页窗口之后又被删了。
            # 跳过即可——它的墓碑日志序号更大，会在下一页（或下一轮）照样送到客户端，
            # 客户端那时才知道它被删；此处若硬编一份 payload 反而会造出脏数据。
            continue
        items.append(
            ServerItem(
                entity_type=entry.entity_type,
                entity_id=entry.entity_id,
                ledger_id=row.ledger_id,
                # 用**行当前**的 updatedAt（而不是这条较旧日志的时间），保证 payload 与
                # updatedAt 描述同一版本；同实体在同一页出现两次也只是幂等重放。
                updated_at=row.updated_at_raw,
                deleted=False,
                payload=reassemble(entry.entity_type, row),
                server_seq=entry.server_seq,
            )
        )

    return PullResponse(items=items, next_seq=next_seq, has_more=has_more)


def status(db: Session, user: User) -> StatusResponse:
    # 口径与旧版一致：每类实体「见过多少个」（活的 + 墓碑），而不是业务表当前行数
    # —— 墓碑已从业务表物理删除，必须从日志数 distinct entity_id。
    counts = {
        entity_type: int(count)
        for entity_type, count in db.execute(
            select(SyncLog.entity_type, func.count(func.distinct(SyncLog.entity_id)))
            .where(SyncLog.user_id == user.id)
            .group_by(SyncLog.entity_type)
        ).all()
    }
    # 本账号自己的游标上限（客户端拉完后应达到的值）；全局计数器另给字段，避免混淆
    user_max_seq = db.execute(
        select(func.max(SyncLog.server_seq)).where(SyncLog.user_id == user.id)
    ).scalar_one_or_none()
    return StatusResponse(
        server_seq=int(user_max_seq or 0),
        server_seq_global=current_seq(db),
        counts=counts,
        server_time=to_iso_utc(utcnow()) or "",
    )
