"""存活探针。"""

from app.db import engine as app_engine


def test_healthz(client):
    response = client.get("/healthz")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    # 双 DSN（DEC-031）：跑在哪个库就报哪个，断言必须与真实引擎一致，不能写死 sqlite
    assert body["db"] in {"sqlite", "mysql"}
    assert body["db"] == app_engine.dialect.name
    assert body["service"] == "minibook-cloud-api"
    assert body["server_time"].endswith("Z")


def test_openapi_available(client):
    response = client.get("/openapi.json")
    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "/api/v1/auth/register" in paths
    assert "/api/v1/sync/push" in paths
    assert "/api/v1/sync/pull" in paths


def test_docs_can_be_disabled(monkeypatch):
    """`ENABLE_DOCS=false` 时不该挂载 /docs、/redoc、/openapi.json（线上公网明文入口的收敛口径）。"""
    from fastapi.testclient import TestClient

    from app.config import get_settings
    from app.main import create_app

    monkeypatch.setenv("ENABLE_DOCS", "false")
    get_settings.cache_clear()
    try:
        with TestClient(create_app()) as disabled:
            assert disabled.get("/openapi.json").status_code == 404
            assert disabled.get("/docs").status_code == 404
            assert disabled.get("/redoc").status_code == 404
            assert disabled.get("/healthz").status_code == 200  # 探针不受影响
    finally:
        get_settings.cache_clear()
