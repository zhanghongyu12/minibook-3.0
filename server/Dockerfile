# MiniBook Cloud API —— 运行镜像（在服务器上构建：docker compose build）
#
# 为什么用镜像而不是「服务器上直接装 venv 跑 uvicorn」：
#   ① 宿主机不必装 Python 依赖，环境与本地/CI 可复现；
#   ② 资源上限（mem/cpu/pids）与日志轮转可以在 compose 里统一钉死；
#   ③ 回滚 = 换镜像标签，不需要在宿主机上找残留进程。
#
# 基础镜像固定 3.12：README 明确不要 3.14（argon2-cffi / pydantic-core 轮子未必齐）。
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    TZ=Asia/Shanghai

WORKDIR /app

# 可选：把 pip 源换成就近镜像（不传 = 官方 PyPI，仓库配置保持环境中立）。
# 用法：PIP_INDEX_URL=http://mirrors.tencentyun.com/pypi/simple \
#       PIP_TRUSTED_HOST=mirrors.tencentyun.com docker compose build
# 说明：走 http 的镜像源必须同时给 --trusted-host，否则 pip 会「忽略不安全的源」然后报找不到包。
ARG PIP_INDEX_URL
ARG PIP_TRUSTED_HOST

# 依赖单独一层：之后只改业务代码时不必重装依赖（构建快很多）
COPY requirements.txt ./
RUN pip install --no-cache-dir \
      ${PIP_INDEX_URL:+--index-url "$PIP_INDEX_URL"} \
      ${PIP_TRUSTED_HOST:+--trusted-host "$PIP_TRUSTED_HOST"} \
      -r requirements.txt

# 业务代码与迁移脚本（tests/ 不进镜像：跑单测用宿主机源码 bind-mount，见 README）
COPY app ./app
COPY alembic ./alembic
COPY alembic.ini ./pytest.ini ./

# 非 root 运行（容器内不需要写任何文件；数据库与日志都在容器外）
RUN useradd --create-home --uid 10001 appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

# 存活探针（与 app/routers/health.py 的 /healthz 对齐），供 compose/运维查看状态
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import sys,urllib.request; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/healthz', timeout=3).status==200 else 1)"

# 单进程 uvicorn：登录限流是进程内内存实现（docs/14_cloud_sync.md §11 未决 6），
# 加 --workers 会让限流按进程分裂，故此处刻意保持单进程。
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
