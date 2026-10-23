"""配置：一切由环境变量 / .env 驱动（12-Factor）。

双 DSN 是核心口径（DEC-031）：开发与单测用 SQLite（零依赖），生产用 MySQL 8，
切换只改 `DATABASE_URL`，不改一行代码。
"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

DEV_JWT_SECRET = "dev-insecure-change-me-please-rotate-32b"  # ≥32 字节，避免 PyJWT 短密钥告警


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    database_url: str = "sqlite:///./dev.db"

    jwt_secret: str = DEV_JWT_SECRET
    jwt_algorithm: str = "HS256"
    access_token_ttl_seconds: int = 30 * 60
    refresh_token_ttl_seconds: int = 30 * 24 * 3600

    pull_limit_default: int = 200
    pull_limit_max: int = 500

    auth_rate_limit_per_minute: int = 10

    #: 是否暴露 `/docs`、`/redoc`、`/openapi.json`（2026-10-23 起支持关闭）。
    #: 公网明文入口上少一份完整接口清单：线上 `.env` 里设 `ENABLE_DOCS=false`；
    #: 开发与单测保持默认 `True`（`tests/test_health.py` 有两条测试分别覆盖开 / 关）。
    enable_docs: bool = True

    @property
    def is_sqlite(self) -> bool:
        return self.database_url.startswith("sqlite")

    @property
    def is_mysql(self) -> bool:
        return self.database_url.startswith("mysql")

    @property
    def uses_dev_jwt_secret(self) -> bool:
        return self.jwt_secret == DEV_JWT_SECRET


@lru_cache
def get_settings() -> Settings:
    return Settings()
