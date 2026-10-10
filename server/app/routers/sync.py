"""同步路由：push / pull / status（全部要求 Bearer；数据按 user_id 隔离）。"""

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from ..config import Settings, get_settings
from ..db import get_db
from ..deps import current_user
from ..models import User
from ..schemas import PullResponse, PushRequest, PushResponse, StatusResponse
from ..services import sync_service

router = APIRouter(prefix="/sync", tags=["sync"])


@router.post("/push", response_model=PushResponse)
def push(
    payload: PushRequest,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> PushResponse:
    return sync_service.push(db, user, payload)


@router.get("/pull", response_model=PullResponse)
def pull(
    since_seq: int = Query(default=0, ge=0),
    limit: int | None = Query(default=None, ge=1),
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> PullResponse:
    effective_limit = min(limit or settings.pull_limit_default, settings.pull_limit_max)
    return sync_service.pull(db, user, since_seq=since_seq, limit=effective_limit)


@router.get("/status", response_model=StatusResponse)
def status(user: User = Depends(current_user), db: Session = Depends(get_db)) -> StatusResponse:
    return sync_service.status(db, user)
