"""A1：注册 / 登录 / 刷新（轮换）/ 登出 / me + 唯一性 + 限流（DEC-032）。"""

from app.config import Settings, get_settings
from app.main import app
from app.security import create_access_token
from tests.conftest import DEFAULT_PASSWORD, auth_headers, login, register


def test_register_then_login_and_me(client):
    created = register(client, "alice")
    assert created["username"] == "alice"  # 统一小写存储
    tokens = login(client, "alice")
    assert tokens["token_type"] == "bearer"
    assert tokens["expires_in"] > 0

    me = client.get("/api/v1/me", headers=auth_headers(tokens))
    assert me.status_code == 200
    assert me.json() == {"user_id": created["user_id"], "username": "alice"}


def test_username_is_case_insensitive_across_engines(client):
    register(client, "Alice")
    duplicate = client.post("/api/v1/auth/register", json={"username": "alice", "password": DEFAULT_PASSWORD})
    assert duplicate.status_code == 409
    assert duplicate.json()["error"]["code"] == "username_taken"
    # 用不同大小写也能登录（与 MySQL 大小写不敏感排序规则对齐）
    assert login(client, "ALICE")["user"]["username"] == "alice"


def test_short_password_rejected(client):
    response = client.post("/api/v1/auth/register", json={"username": "bob", "password": "short"})
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "validation_error"


def test_invalid_username_rejected(client):
    response = client.post("/api/v1/auth/register", json={"username": "有中文", "password": DEFAULT_PASSWORD})
    assert response.status_code == 422


def test_wrong_password_and_unknown_user_share_one_error(client):
    register(client, "carol")
    wrong = client.post("/api/v1/auth/login", json={"username": "carol", "password": "nope-nope-nope"})
    unknown = client.post("/api/v1/auth/login", json={"username": "nobody", "password": "nope-nope-nope"})
    assert wrong.status_code == unknown.status_code == 401
    assert wrong.json()["error"]["code"] == unknown.json()["error"]["code"] == "invalid_credentials"


def test_password_is_stored_as_argon2_hash(client, session_factory):
    register(client, "dave")
    with session_factory() as db:
        from app.models import User

        user = db.query(User).filter(User.username == "dave").one()
    assert user.password_hash.startswith("$argon2")
    assert DEFAULT_PASSWORD not in user.password_hash


def test_missing_or_bad_token_rejected(client):
    assert client.get("/api/v1/me").status_code == 401
    bad = client.get("/api/v1/me", headers={"Authorization": "Bearer not-a-jwt"})
    assert bad.status_code == 401
    assert bad.json()["error"]["code"] == "token_invalid"
    forged = client.get("/api/v1/me", headers={"X-User-Id": "whatever"})
    assert forged.status_code == 401


def test_expired_access_token_returns_token_expired(client):
    created = register(client, "erin")
    settings = get_settings()
    expired = create_access_token(
        created["user_id"], settings.jwt_secret, settings.jwt_algorithm, ttl_seconds=-10
    )
    response = client.get("/api/v1/me", headers={"Authorization": f"Bearer {expired}"})
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "token_expired"


def test_refresh_rotates_and_revokes_old_token(client):
    register(client, "frank")
    tokens = login(client, "frank")

    refreshed = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert refreshed.status_code == 200
    new_tokens = refreshed.json()
    assert new_tokens["refresh_token"] != tokens["refresh_token"]

    reuse = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert reuse.status_code == 401
    assert reuse.json()["error"]["code"] == "token_revoked"

    # 新令牌仍可用
    again = client.post("/api/v1/auth/refresh", json={"refresh_token": new_tokens["refresh_token"]})
    assert again.status_code == 200


def test_logout_revokes_refresh_token_and_is_idempotent(client):
    register(client, "grace")
    tokens = login(client, "grace")

    first = client.post("/api/v1/auth/logout", json={"refresh_token": tokens["refresh_token"]})
    assert first.status_code == 200 and first.json()["ok"] is True
    second = client.post("/api/v1/auth/logout", json={"refresh_token": tokens["refresh_token"]})
    assert second.status_code == 200  # 幂等

    after = client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert after.status_code == 401
    assert after.json()["error"]["code"] == "token_revoked"


def test_login_rate_limited(client):
    register(client, "henry")
    app.dependency_overrides[get_settings] = lambda: Settings(auth_rate_limit_per_minute=2)
    try:
        codes = [
            client.post("/api/v1/auth/login", json={"username": "henry", "password": "wrong-pass"}).status_code
            for _ in range(3)
        ]
    finally:
        app.dependency_overrides.pop(get_settings, None)
    assert codes[:2] == [401, 401]
    assert codes[2] == 429
