"""认证路由：注册 / 登录 / 刷新（轮换）/ 登出 / 当前用户（DEC-032）。"""

from datetime import timedelta
from uuid import uuid4

from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..config import Settings, get_settings
from ..db import get_db
from ..deps import current_user, rate_limit
from ..errors import ApiError
from ..models import Device, RefreshToken, User
from ..schemas import (
    LoginRequest,
    LogoutRequest,
    RefreshRequest,
    RegisterRequest,
    TokenPair,
    UserOut,
)
from ..security import (
    create_access_token,
    hash_password,
    hash_refresh_token,
    new_refresh_token,
    normalize_username,
    verify_password,
)
from ..timeutil import utcnow

router = APIRouter(prefix="/auth", tags=["auth"])
me_router = APIRouter(tags=["auth"])


def _touch_device(
    db: Session,
    user: User,
    device_id: str | None,
    *,
    name: str | None = None,
    platform: str | None = None,
    app_version: str | None = None,
) -> None:
    if not device_id:
        return
    device = db.get(Device, device_id)
    if device is None or device.user_id != user.id:
        db.add(
            Device(
                id=device_id,
                user_id=user.id,
                name=name,
                platform=platform,
                app_version=app_version,
                last_seen_at=utcnow(),
                created_at=utcnow(),
            )
        )
        return
    device.last_seen_at = utcnow()
    device.name = name or device.name
    device.platform = platform or device.platform
    device.app_version = app_version or device.app_version


def _issue_tokens(
    db: Session,
    user: User,
    settings: Settings,
    device_id: str | None,
    rotated_from: str | None = None,
) -> TokenPair:
    access = create_access_token(
        user.id, settings.jwt_secret, settings.jwt_algorithm, settings.access_token_ttl_seconds
    )
    refresh_plain = new_refresh_token()
    now = utcnow()
    db.add(
        RefreshToken(
            id=str(uuid4()),
            user_id=user.id,
            token_hash=hash_refresh_token(refresh_plain),
            issued_at=now,
            expires_at=now + timedelta(seconds=settings.refresh_token_ttl_seconds),
            device_id=device_id,
            rotated_from=rotated_from,
        )
    )
    db.commit()
    return TokenPair(
        access_token=access,
        refresh_token=refresh_plain,
        expires_in=settings.access_token_ttl_seconds,
        user=UserOut(user_id=user.id, username=user.username),
    )


@router.post("/register", response_model=UserOut, status_code=201)
def register(
    payload: RegisterRequest,
    request: Request,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> UserOut:
    username = normalize_username(payload.username)
    rate_limit("register", username, request, settings)

    if db.execute(select(User).where(User.username == username)).scalar_one_or_none() is not None:
        raise ApiError("username_taken", "用户名已被占用")

    now = utcnow()
    user = User(
        id=str(uuid4()),
        username=username,
        password_hash=hash_password(payload.password),
        created_at=now,
        updated_at=now,
    )
    db.add(user)
    try:
        db.flush()
        _touch_device(db, user, payload.device_id, name=payload.device_name)
        db.commit()
    except IntegrityError as exc:  # 并发注册同名
        db.rollback()
        raise ApiError("username_taken", "用户名已被占用") from exc

    return UserOut(user_id=user.id, username=user.username)


@router.post("/login", response_model=TokenPair)
def login(
    payload: LoginRequest,
    request: Request,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> TokenPair:
    username = normalize_username(payload.username)
    rate_limit("login", username, request, settings)

    user = db.execute(select(User).where(User.username == username)).scalar_one_or_none()
    # 统一错误：不区分「用户不存在」与「口令错」（DEC-032）
    if user is None or user.disabled or not verify_password(user.password_hash, payload.password):
        raise ApiError("invalid_credentials", "用户名或口令不正确")

    _touch_device(
        db,
        user,
        payload.device_id,
        name=payload.device_name,
        platform=payload.platform,
        app_version=payload.app_version,
    )
    return _issue_tokens(db, user, settings, payload.device_id)


@router.post("/refresh", response_model=TokenPair)
def refresh(
    payload: RefreshRequest,
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> TokenPair:
    row = db.execute(
        select(RefreshToken).where(RefreshToken.token_hash == hash_refresh_token(payload.refresh_token))
    ).scalar_one_or_none()
    if row is None:
        raise ApiError("token_invalid", "刷新令牌无效")
    if row.revoked_at is not None:
        raise ApiError("token_revoked", "刷新令牌已失效，请重新登录")
    if row.expires_at <= utcnow():
        raise ApiError("token_expired", "刷新令牌已过期，请重新登录")

    user = db.get(User, row.user_id)
    if user is None or user.disabled:
        raise ApiError("token_invalid", "用户不存在或已禁用")

    row.revoked_at = utcnow()  # 轮换：旧的立即作废
    return _issue_tokens(db, user, settings, payload.device_id or row.device_id, rotated_from=row.id)


@router.post("/logout")
def logout(payload: LogoutRequest, db: Session = Depends(get_db)) -> dict[str, bool]:
    row = db.execute(
        select(RefreshToken).where(RefreshToken.token_hash == hash_refresh_token(payload.refresh_token))
    ).scalar_one_or_none()
    if row is not None and row.revoked_at is None:
        row.revoked_at = utcnow()
        db.commit()
    return {"ok": True}  # 幂等：已撤销/未知令牌同样返回成功，不泄露令牌是否存在


@me_router.get("/me", response_model=UserOut)
def me(user: User = Depends(current_user)) -> UserOut:
    return UserOut(user_id=user.id, username=user.username)
