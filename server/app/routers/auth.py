"""认证路由：注册 / 登录 / 刷新（轮换）/ 登出 / 当前用户 / 改资料 / 注销账号（DEC-032、2026-10-12）。"""

from datetime import datetime, timedelta
from uuid import uuid4

from fastapi import APIRouter, Depends, Request
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..config import Settings, get_settings
from ..db import get_db
from ..deps import current_user, rate_limit
from ..errors import ApiError
from ..models import (
    Budget,
    Category,
    Debt,
    Device,
    Ledger,
    RefreshToken,
    SyncLog,
    Transaction,
    User,
    UserCategoryRule,
)
from ..schemas import (
    DeleteAccountRequest,
    LoginRequest,
    LogoutRequest,
    ProfileUpdateRequest,
    ProfileUpdateResponse,
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


def _user_out(user: User, last_login_at: datetime | None = None) -> UserOut:
    """统一的用户资料出参（2026-10-12 起含昵称、邮箱、个性签名、地区、注册时间与上次登录）。"""
    return UserOut(
        user_id=user.id,
        username=user.username,
        nickname=user.nickname,
        email=user.email,
        bio=user.bio,
        region=user.region,
        created_at=user.created_at,
        last_login_at=last_login_at,
    )


def _last_login_at(db: Session, user_id: str) -> datetime | None:
    """最近一次**登录**时间：只看首发的刷新令牌（`rotated_from` 为空），令牌轮换不算新登录。"""
    return db.execute(
        select(RefreshToken.issued_at)
        .where(RefreshToken.user_id == user_id, RefreshToken.rotated_from.is_(None))
        .order_by(RefreshToken.issued_at.desc())
        .limit(1)
    ).scalar_one_or_none()


def _touch_device(
    db: Session,
    user: User,
    device_id: str | None,
    *,
    name: str | None = None,
    platform: str | None = None,
    app_version: str | None = None,
) -> None:
    """登记设备行。`devices.id` 就是客户端上报的 `device_id`（**主键**，同一时刻只属于一个账号）。

    设备已存在但属于**别的账号**时必须改归属，不能再 INSERT —— 否则撞主键抛 IntegrityError，
    register 会把它译成「用户名已被占用」、login（无异常兜底）直接 500（TASK-088 ④）。
    """
    if not device_id:
        return
    now = utcnow()
    device = db.get(Device, device_id)
    if device is None:
        db.add(
            Device(
                id=device_id,
                user_id=user.id,
                name=name,
                platform=platform,
                app_version=app_version,
                last_seen_at=now,
                created_at=now,
            )
        )
        return
    device.user_id = user.id
    device.last_seen_at = now
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
        user=_user_out(user),
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

    return _user_out(user)


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
def me(user: User = Depends(current_user), db: Session = Depends(get_db)) -> UserOut:
    return _user_out(user, last_login_at=_last_login_at(db, user.id))


@me_router.patch("/me", response_model=ProfileUpdateResponse)
def update_me(
    payload: ProfileUpdateRequest,
    request: Request,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> ProfileUpdateResponse:
    """改资料（2026-10-12，第二轮扩充邮箱 / 个性签名 / 地区）：资料字段（可清空）/ 口令（须验原口令）。

    用 `model_fields_set` 判定「用户到底传了哪个字段」——传空串表示**清除**该字段，
    与「没传这个字段」是两件事，只看值会把两者混为一谈。
    """
    rate_limit("profile", user.id, request, settings)
    fields = payload.model_fields_set
    if not ({"nickname", "email", "bio", "region", "new_password"} & fields):
        raise ApiError("nothing_to_update", "没有需要修改的内容")

    tokens: TokenPair | None = None
    if "nickname" in fields:
        user.nickname = payload.nickname  # schema 已把空串归一为 None ⇒ 清除显示名
    if "email" in fields:
        user.email = payload.email
    if "bio" in fields:
        user.bio = payload.bio
    if "region" in fields:
        user.region = payload.region

    if "new_password" in fields:
        if not payload.old_password:
            raise ApiError("invalid_credentials", "修改口令需先填写原口令")
        if not verify_password(user.password_hash, payload.old_password):
            raise ApiError("invalid_credentials", "原口令不正确")
        user.password_hash = hash_password(payload.new_password)
        now = utcnow()
        # 口令变更 ⇒ 该账号所有旧刷新令牌立即失效（其它设备需重新登录）
        for row in db.execute(
            select(RefreshToken).where(
                RefreshToken.user_id == user.id, RefreshToken.revoked_at.is_(None)
            )
        ).scalars():
            row.revoked_at = now
        user.updated_at = now
        db.commit()
        # 当前设备换发新令牌：否则客户端手上那枚刷新令牌刚被自己撤销，下次刷新就被登出。
        tokens = _issue_tokens(db, user, settings, None)
    else:
        user.updated_at = utcnow()
        db.commit()

    return ProfileUpdateResponse(user=_user_out(user), tokens=tokens)


@router.post("/delete-account")
def delete_account(
    payload: DeleteAccountRequest,
    request: Request,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> dict[str, bool]:
    """注销账号（不可撤销）：删账号 + 云端 6 张业务表 + `sync_log` + 令牌 + 设备。

    **只删云端副本**；用户手机上的本地数据不动（退出/注销从不是删本地数据）。
    """
    rate_limit("delete_account", user.id, request, settings)
    if not verify_password(user.password_hash, payload.password):
        raise ApiError("invalid_credentials", "口令不正确，账号未注销")

    uid = user.id
    for model in (Ledger, Category, Transaction, Debt, Budget, UserCategoryRule, SyncLog):
        db.execute(delete(model).where(model.user_id == uid))
    db.execute(delete(RefreshToken).where(RefreshToken.user_id == uid))
    db.execute(delete(Device).where(Device.user_id == uid))
    db.delete(user)
    db.commit()
    return {"ok": True}
