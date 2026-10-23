"""A1：注册 / 登录 / 刷新（轮换）/ 登出 / me + 唯一性 + 限流（DEC-032）。"""

from app.config import Settings, get_settings
from app.main import app
from app.security import create_access_token
from tests.conftest import (
    DEFAULT_PASSWORD,
    LEDGER_ID,
    auth_headers,
    login,
    make_account,
    push,
    register,
    sync_item,
)


def test_register_then_login_and_me(client):
    created = register(client, "alice")
    assert created["username"] == "alice"  # 统一小写存储
    tokens = login(client, "alice")
    assert tokens["token_type"] == "bearer"
    assert tokens["expires_in"] > 0

    me = client.get("/api/v1/me", headers=auth_headers(tokens))
    assert me.status_code == 200
    body = me.json()
    assert body["user_id"] == created["user_id"]
    assert body["username"] == "alice"
    # 2026-10-12 起额外返回昵称与注册时间（「账号」页展示用），故只断言子集
    assert body["nickname"] is None
    assert body["created_at"]


def test_username_is_case_insensitive_across_engines(client):
    register(client, "Alice")
    duplicate = client.post("/api/v1/auth/register", json={"username": "alice", "password": DEFAULT_PASSWORD})
    assert duplicate.status_code == 409
    assert duplicate.json()["error"]["code"] == "username_taken"
    # 用不同大小写也能登录（与 MySQL 大小写不敏感排序规则对齐）
    assert login(client, "ALICE")["user"]["username"] == "alice"


def test_register_reclaims_device_of_another_user(client, session_factory):
    """同一台设备换账号注册：设备改归新账号，不得因撞 devices 主键被误报「用户名已被占用」。

    回归 TASK-088 ④（2026-10-21 模拟器联调实测）：旧实现见设备「已存在但属于他人」也走 INSERT。
    """
    shared = "device-shared-register"
    first = client.post(
        "/api/v1/auth/register",
        json={"username": "owner_a", "password": DEFAULT_PASSWORD, "device_id": shared},
    )
    assert first.status_code == 201, first.text

    second = client.post(
        "/api/v1/auth/register",
        json={"username": "owner_b", "password": DEFAULT_PASSWORD, "device_id": shared},
    )
    assert second.status_code == 201, second.text
    assert second.json()["username"] == "owner_b"

    with session_factory() as db:
        from app.models import Device, User

        owner_b = db.query(User).filter(User.username == "owner_b").one()
        devices = db.query(Device).filter(Device.id == shared).all()
    assert len(devices) == 1  # 主键只留一行
    assert devices[0].user_id == owner_b.id  # 且已改归新账号


def test_login_reclaims_device_of_another_user(client, session_factory):
    """换账号**登录**同一台设备不得 500：login 也走 _touch_device，但它没有异常兜底。"""
    shared = "device-shared-login"
    register(client, "owner_c")
    other = client.post(
        "/api/v1/auth/register",
        json={"username": "owner_d", "password": DEFAULT_PASSWORD, "device_id": shared},
    )
    assert other.status_code == 201, other.text

    tokens = client.post(
        "/api/v1/auth/login",
        json={"username": "owner_c", "password": DEFAULT_PASSWORD, "device_id": shared},
    )
    assert tokens.status_code == 200, tokens.text
    assert tokens.json()["user"]["username"] == "owner_c"

    with session_factory() as db:
        from app.models import Device, User

        owner_c = db.query(User).filter(User.username == "owner_c").one()
        device = db.query(Device).filter(Device.id == shared).one()
    assert device.user_id == owner_c.id


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


# ── 改资料 / 注销账号（2026-10-12 随「账号」页加入）─────────────────────


def test_me_includes_nickname_and_created_at(client):
    register(client, "dave")
    tokens = login(client, "dave")
    body = client.get("/api/v1/me", headers=auth_headers(tokens)).json()
    assert body["username"] == "dave"
    assert body["nickname"] is None
    assert body["created_at"]  # 注册时间非空


def test_update_nickname_then_clear(client):
    account = make_account(client, "erin")
    response = client.patch("/api/v1/me", json={"nickname": "  小艾  "}, headers=account["headers"])
    assert response.status_code == 200
    assert response.json()["user"]["nickname"] == "小艾"  # 首尾空白被 strip
    assert response.json()["tokens"] is None  # 只改昵称 ⇒ 不动令牌

    cleared = client.patch("/api/v1/me", json={"nickname": ""}, headers=account["headers"])
    assert cleared.status_code == 200
    assert cleared.json()["user"]["nickname"] is None  # 空串 = 清除显示名


def test_me_created_at_is_utc_with_z(client):
    """注册时间/上次登录必须是**带 Z 的 UTC ISO 串**——客户端 `Instant.parse` 依赖它，
    否则真机上「注册时间」会原样显示 UTC（差 8 小时）。"""
    register(client, "ivy")
    tokens = login(client, "ivy")
    body = client.get("/api/v1/me", headers=auth_headers(tokens)).json()
    assert body["created_at"].endswith("Z")
    assert body["last_login_at"] is not None
    assert body["last_login_at"].endswith("Z")


def test_me_includes_profile_fields(client):
    register(client, "jack")
    tokens = login(client, "jack")
    body = client.get("/api/v1/me", headers=auth_headers(tokens)).json()
    assert body["email"] is None
    assert body["bio"] is None
    assert body["region"] is None


def test_update_email_bio_region_then_clear(client):
    account = make_account(client, "kate")
    saved = client.patch(
        "/api/v1/me",
        json={"email": "  kate@example.com ", "bio": "记账三年", "region": "北京"},
        headers=account["headers"],
    )
    assert saved.status_code == 200
    user = saved.json()["user"]
    assert user["email"] == "kate@example.com"  # 首尾空白被 strip
    assert user["bio"] == "记账三年"
    assert user["region"] == "北京"
    assert saved.json()["tokens"] is None  # 只改资料 ⇒ 不动令牌

    cleared = client.patch(
        "/api/v1/me",
        json={"email": "", "bio": "", "region": ""},
        headers=account["headers"],
    )
    assert cleared.status_code == 200
    assert cleared.json()["user"]["email"] is None  # 空串 = 清除
    assert cleared.json()["user"]["bio"] is None
    assert cleared.json()["user"]["region"] is None


def test_update_me_partial_keeps_other_fields(client):
    """只传 email 时，nickname / bio / region 必须原样保留（`model_fields_set` 口径）。"""
    account = make_account(client, "luke")
    client.patch("/api/v1/me", json={"nickname": "小路", "bio": "旧签名"}, headers=account["headers"])
    patched = client.patch("/api/v1/me", json={"email": "luke@example.com"}, headers=account["headers"])
    user = patched.json()["user"]
    assert user["email"] == "luke@example.com"
    assert user["nickname"] == "小路"
    assert user["bio"] == "旧签名"


def test_update_me_rejects_bad_email(client):
    account = make_account(client, "mona")
    response = client.patch("/api/v1/me", json={"email": "not-an-email"}, headers=account["headers"])
    assert response.status_code == 422


def test_update_me_rejects_empty_payload(client):
    account = make_account(client, "frank")
    response = client.patch("/api/v1/me", json={}, headers=account["headers"])
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "nothing_to_update"


def test_update_me_requires_auth(client):
    assert client.patch("/api/v1/me", json={"nickname": "x"}).status_code == 401


def test_change_password_needs_old_and_revokes_old_tokens(client):
    account = make_account(client, "grace")
    old_refresh = account["tokens"]["refresh_token"]

    # 原口令不对 ⇒ 拒绝，且口令未变（仍能用旧口令登录）
    wrong = client.patch(
        "/api/v1/me",
        json={"old_password": "wrong-password", "new_password": "brand-new-secret"},
        headers=account["headers"],
    )
    assert wrong.status_code == 401
    assert login(client, "grace")["access_token"]

    ok = client.patch(
        "/api/v1/me",
        json={"old_password": DEFAULT_PASSWORD, "new_password": "brand-new-secret"},
        headers=account["headers"],
    )
    assert ok.status_code == 200
    fresh = ok.json()["tokens"]
    assert fresh and fresh["access_token"]

    # 旧刷新令牌已被撤销；新令牌可正常轮换
    stale = client.post("/api/v1/auth/refresh", json={"refresh_token": old_refresh})
    assert stale.status_code == 401
    rotated = client.post("/api/v1/auth/refresh", json={"refresh_token": fresh["refresh_token"]})
    assert rotated.status_code == 200
    # 旧口令登不上，新口令可以
    old_login = client.post(
        "/api/v1/auth/login", json={"username": "grace", "password": DEFAULT_PASSWORD}
    )
    assert old_login.status_code == 401
    assert login(client, "grace", "brand-new-secret")["access_token"]


def test_short_new_password_rejected(client):
    account = make_account(client, "heidi")
    response = client.patch(
        "/api/v1/me", json={"old_password": DEFAULT_PASSWORD, "new_password": "short"},
        headers=account["headers"],
    )
    assert response.status_code == 422


def test_delete_account_wipes_cloud_copy_and_login_fails(client, session_factory):
    account = make_account(client, "ivan")
    uid = account["user"]["user_id"]
    items = [
        sync_item("ledger", entity_id=LEDGER_ID, ledger_id=LEDGER_ID),
        sync_item(),
    ]
    pushed = push(client, account["headers"], items)
    assert pushed.status_code == 200 and pushed.json()["applied"] == 2

    # 口令不对 ⇒ 拒绝注销
    refused = client.post(
        "/api/v1/auth/delete-account", json={"password": "nope-nope-nope"}, headers=account["headers"]
    )
    assert refused.status_code == 401
    assert login(client, "ivan")["access_token"]

    # 正确口令 ⇒ 云端副本清空、账号消失
    ok = client.post(
        "/api/v1/auth/delete-account", json={"password": DEFAULT_PASSWORD}, headers=account["headers"]
    )
    assert ok.status_code == 200 and ok.json() == {"ok": True}

    assert client.post("/api/v1/auth/login", json={"username": "ivan", "password": DEFAULT_PASSWORD}).status_code == 401
    # 旧 access token 对应的用户已不存在 ⇒ 401
    assert client.get("/api/v1/me", headers=account["headers"]).status_code == 401

    with session_factory() as db:
        from app.models import Ledger, SyncLog, Transaction

        for model in (Ledger, Transaction, SyncLog):
            assert db.query(model).filter_by(user_id=uid).count() == 0

    # 同名可重新注册（用户行确实删了）
    recreated = register(client, "ivan")
    assert recreated["user_id"] != uid
