"""存活探针（不鉴权、不查库，纯 liveness）。"""

from fastapi import APIRouter

from ..db import engine
from ..timeutil import to_iso_utc, utcnow

APP_VERSION = "0.1.0"

router = APIRouter(tags=["health"])


@router.get("/healthz")
def healthz() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "minibook-cloud-api",
        "version": APP_VERSION,
        "db": engine.dialect.name,
        "server_time": to_iso_utc(utcnow()) or "",
    }
