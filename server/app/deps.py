"""FastAPI 依赖：当前用户（Bearer JWT）与请求限流。"""

import jwt
from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from .config import Settings, get_settings
from .db import get_db
from .errors import ApiError
from .models import User
from .ratelimit import check_rate_limit
from .security import decode_access_token

_bearer = HTTPBearer(auto_error=False)


def current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> User:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise ApiError("token_invalid", "缺少访问令牌")
    try:
        claims = decode_access_token(credentials.credentials, settings.jwt_secret, settings.jwt_algorithm)
    except jwt.ExpiredSignatureError as exc:
        raise ApiError("token_expired", "访问令牌已过期，请刷新") from exc
    except jwt.PyJWTError as exc:
        raise ApiError("token_invalid", "访问令牌无效") from exc

    user = db.get(User, claims.get("sub"))
    if user is None or user.disabled:
        raise ApiError("token_invalid", "用户不存在或已禁用")
    return user


def rate_limit(scope: str, key: str, request: Request, settings: Settings) -> None:
    """按 IP + 标识限流（内存实现，单进程有效；见 §3.6）。"""
    client_ip = request.client.host if request.client else "unknown"
    check_rate_limit(f"{scope}:{client_ip}:{key}", settings.auth_rate_limit_per_minute)
