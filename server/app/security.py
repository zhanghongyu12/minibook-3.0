"""口令哈希、JWT 与刷新令牌（DEC-032）。

- 口令：argon2id（argon2-cffi），服务端永不解密回原文；
- Access：JWT HS256，含 sub/jti/iat/exp；
- Refresh：随机 32B，**只存 sha256 哈希**，轮换 + 可撤销；
- 用户名统一小写（MySQL 排序规则大小写不敏感、SQLite 敏感，必须两端一致）。
"""

import hashlib
import secrets
import uuid
from datetime import datetime, timedelta, timezone

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError, VerifyMismatchError

_hasher = PasswordHasher()

REFRESH_TOKEN_BYTES = 32


def normalize_username(username: str) -> str:
    """统一小写 + 去首尾空白（跨引擎一致性的前置条件，DEC-031）。"""
    return username.strip().lower()


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password_hash: str, password: str) -> bool:
    try:
        return _hasher.verify(password_hash, password)
    except (VerifyMismatchError, VerificationError, InvalidHashError):
        return False


def new_refresh_token() -> str:
    return secrets.token_urlsafe(REFRESH_TOKEN_BYTES)


def hash_refresh_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def create_access_token(user_id: str, secret: str, algorithm: str, ttl_seconds: int) -> str:
    # 坑（本机实测踩过）：epoch 必须用 **aware** UTC 计算。naive datetime 的 `.timestamp()`
    # 会按**本地时区**解释，在 +08:00 机器上把 exp 少算 8 小时 ⇒ 刚签发的令牌立刻「已过期」。
    issued_at = datetime.now(timezone.utc)
    payload = {
        "sub": user_id,
        "jti": uuid.uuid4().hex,
        "iat": int(issued_at.timestamp()),
        "exp": int((issued_at + timedelta(seconds=ttl_seconds)).timestamp()),
    }
    return jwt.encode(payload, secret, algorithm=algorithm)


def decode_access_token(token: str, secret: str, algorithm: str) -> dict:
    """解码 JWT；过期抛 `jwt.ExpiredSignatureError`，其余非法抛 `jwt.PyJWTError`。"""
    return jwt.decode(token, secret, algorithms=[algorithm], options={"require": ["exp", "sub"]})
