"""FastAPI 应用装配：路由挂载 + 统一错误体（见 docs/14_cloud_sync.md §3.5）。

启动前请先建表：`alembic upgrade head`（不在此处 `create_all`——只保留一条建表路径）。
"""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import SQLAlchemyError

from .config import get_settings
from .errors import ApiError, error_body
from .routers import auth, health, sync

logger = logging.getLogger("minibook.api")

API_PREFIX = "/api/v1"


def _safe_validation_details(exc: RequestValidationError) -> list[dict[str, object]]:
    """只保留位置与原因：**绝不回显 input**（可能含口令/备注）。"""
    details: list[dict[str, object]] = []
    for error in exc.errors():
        details.append(
            {
                "loc": [str(part) for part in error.get("loc", [])],
                "msg": str(error.get("msg", "")),
                "type": str(error.get("type", "")),
            }
        )
    return details


def create_app() -> FastAPI:
    settings = get_settings()
    if settings.uses_dev_jwt_secret and not settings.is_sqlite:
        logger.warning("JWT_SECRET 仍是开发默认值，生产环境必须更换（见 .env.remote.example）")

    app = FastAPI(
        title="MiniBook Cloud API",
        version=health.APP_VERSION,
        description="MiniBook 3.0 云端同步服务（F-15）。设计见 docs/14_cloud_sync.md。",
        # 线上公网入口（明文 HTTP）按 .env 的 ENABLE_DOCS=false 收起文档与 schema
        docs_url="/docs" if settings.enable_docs else None,
        redoc_url="/redoc" if settings.enable_docs else None,
        openapi_url="/openapi.json" if settings.enable_docs else None,
    )

    app.include_router(health.router)
    app.include_router(auth.router, prefix=API_PREFIX)
    app.include_router(auth.me_router, prefix=API_PREFIX)
    app.include_router(sync.router, prefix=API_PREFIX)

    @app.exception_handler(ApiError)
    async def _handle_api_error(_request: Request, exc: ApiError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=error_body(exc.code, exc.message, exc.extra))

    @app.exception_handler(RequestValidationError)
    async def _handle_validation_error(_request: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content=error_body(
                "validation_error", "请求体校验失败", {"details": _safe_validation_details(exc)}
            ),
        )

    @app.exception_handler(SQLAlchemyError)
    async def _handle_db_error(_request: Request, exc: SQLAlchemyError) -> JSONResponse:
        logger.exception("数据库错误: %s", type(exc).__name__)  # 只记类型，不记 payload
        return JSONResponse(
            status_code=500, content=error_body("server_error", "服务端数据库错误，请稍后重试")
        )

    return app


app = create_app()
