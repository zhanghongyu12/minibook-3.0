# MiniBook Cloud API（F-15 服务端）

Python（**FastAPI + SQLAlchemy 2 + Alembic**）实现的云端同步服务：用户系统 + 三模式数据同步。
设计与口径的权威文档：`../docs/14_cloud_sync.md`；决策记录：`../docs/07_decisions.md` 的 **DEC-031~034**。

## 能力

- **用户系统**：注册 / 登录 / 刷新（令牌轮换）/ 登出 / `me`；口令 argon2id；**账号间强隔离**（所有读写强制 `user_id`）
- **同步**：`POST /api/v1/sync/push`、`GET /api/v1/sync/pull`、`GET /api/v1/sync/status`
- **幂等**：`entity_id` = 客户端 UUID 主键，重复 push 同版本 = no-op（不推进 `server_seq`）
- **墓碑**：删除不物理删（`deleted=true`），随 pull 下发给其他设备
- **冲突**：LWW —— 服务端较新则回传服务端版本（客户端负责留档输家，绝不静默丢）
- **校验**：6 类实体逐条字段白名单（`app/entity_spec.py`），任一条失败 → **整批回滚**

## 三步起服务（Windows / PowerShell）

```powershell
cd server
python -m venv .venv                      # 用 Python 3.12；不要 3.14（argon2/pydantic-core 轮子未必齐）
.venv\Scripts\python.exe -m pip install -r requirements.txt
copy .env.local.example .env              # 本地开发模板（SQLite + 开发密钥）；服务器上用 .env.remote.example
.venv\Scripts\python.exe -m alembic upgrade head
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

- OpenAPI 文档：<http://127.0.0.1:8000/docs>　存活探针：<http://127.0.0.1:8000/healthz>
- 手机联调：手机与电脑**同一 Wi-Fi**，服务器地址填 `http://<电脑内网 IP>:8000`
  （明文 HTTP 仅限开发；公网必须 HTTPS，见 `../docs/14_cloud_sync.md §9`）

## 测试

```powershell
.venv\Scripts\python.exe -m pytest -q     # SQLite（默认，零依赖，快）
```

**MySQL 上跑同一套**（交付口径要求，DEC-031；2026-10-10 已在真 MySQL 8.0.46 上跑通 **52 例 / 0 失败**，
含 TASK-064 新增 3 例「日志不得含 payload」隐私断言）：

```powershell
$env:TEST_DATABASE_URL = "mysql+pymysql://user:pw@127.0.0.1:3306/minibook_test?charset=utf8mb4"
.venv\Scripts\python.exe -m pytest -q
```

> 护栏：`TEST_DATABASE_URL` 的**库名必须含 `test`**，否则夹具直接拒绝（测试会 `drop_all`，防误伤真实库）。
> 口令里的 `@ : / %` 必须 **URL 编码**（如 `user@123` → `user%40123`），否则 DSN 会被解析错。

## 双 DSN（切换数据库只改一行）

`.env` 里换 `DATABASE_URL`（`.env` 已被 `.gitignore` 忽略，**口令只落这一处：不入库、不打日志**）：

| 场景 | DSN |
|---|---|
| 开发 / 单测 | `sqlite:///./dev.db` |
| 生产 / 联调 | `mysql+pymysql://user:pw@127.0.0.1:3306/minibook?charset=utf8mb4` |

MySQL 侧的硬要求由迁移与 `__table_args__` 同口径保证，并由 `tests/test_migration.py` 校验 DDL：
**InnoDB + utf8mb4 + `COLLATE utf8mb4_unicode_ci` + `DATETIME(6)`**；**6 张业务表 + `sync_log` 升级后不再有 JSON 列**
（业务字段一律落在真实列上，DEC-037）。
（① `DATETIME(6)` 不是洁癖：MySQL 默认 DATETIME 无小数秒，会截掉毫秒，令「同 `updated_at` = no-op」判定失真。
② `COLLATE` 必须显式写：MySQL 8 对 `CHARSET=utf8mb4` 默认给 `utf8mb4_0900_ai_ci`，与部署口径不一致。
③ 每个字段的列宽都要与 `entity_spec.py` 的 `max_length` 对齐——`tests/test_typed_storage.py` 会逐字段断言，
防「白名单放行、MySQL 悄悄截断」。）

### MySQL 实测踩到的两个坑（已修，勿改回去）

1. **`alembic/env.py` 不能把 URL 回写进 alembic 配置**：`Config.set_main_option()` 经 configparser 的
   `BasicInterpolation`，值里带 `%` 直接抛 `ValueError: invalid interpolation syntax` —— 而 URL 编码后的口令必带 `%`。
   现在只**读取** URL 并原样交给 `create_engine`。
2. **`downgrade()` 只能 `DROP TABLE`，不能先 `DROP INDEX`**：MySQL 上 `DROP INDEX ix_refresh_tokens_user`
   会因该索引是外键支撑索引而失败（`errno 1553`），且中途失败留下**半删库**；先删子表、最后删父表即可。

### 清库 / 重置数据：只删业务行，别碰这两张表

- **可删**：`ledgers` / `categories` / `transactions` / `debts` / `budgets` / `user_category_rules` / `sync_log` / `devices` / `refresh_tokens` / `users`。
- **永不删**：`sync_counters`（`server_seq` 发号状态）、`alembic_version`（迁移版本状态）—— 它们是**部署状态**，不是业务数据。
- 2026-10-23 真实事故：一次「清空所有表行」把两者一起删掉 ⇒ `push` 全 500（`server_seq 计数器缺失`），而 `pull` 正常，极难自查。现 `next_seq()` 已能按 `MAX(sync_log.server_seq)` 自愈（DEC-055），但**仍不要**依赖它。
- 恢复顺序：① `SELECT COALESCE(MAX(server_seq),0) FROM sync_log`；② 用①的值补种 `sync_counters`；③ `alembic current` 为空则 `alembic stamp <最新 revision>`（**不要** `alembic upgrade head`）；④ `alembic current` 复核。

## 目录

```
app/
  main.py          FastAPI 装配 + 统一错误体（错误码见 docs §3.5）
  config.py        pydantic-settings（一切环境变量驱动）
  db.py            engine/session（SQLite 开外键；MySQL pool_pre_ping）
  models.py        users / refresh_tokens / devices / sync_counters + 6 张业务表（ledgers/categories/transactions/debts/budgets/user_category_rules）+ sync_log
  schemas.py       Pydantic DTO（信封 snake_case + payload 原样 camelCase）
  entity_spec.py   6 类实体的字段白名单、类型与长度上限（云端「校验不放松」的唯一来源）
  security.py      argon2id / JWT / 刷新令牌哈希
  deps.py          current_user（Bearer）+ 限流
  routers/         health / auth / sync
  services/        sync_service（push / pull / status）、entity_store（payload ⇄ 列 双向搬运）
alembic/versions/0001_init.py, 0002_typed_entities.py
tests/             61 例：认证 / 隔离 / push 契约 / pull 分页 / 幂等 / 冲突 / 迁移（含回填与降级往返）/ 类型化存储 / 时间解析 / 日志隐私（3 例）
```

## 禁止入库

`.env`（DSN 口令与 JWT 密钥）、`*.db`、`.venv/`、以及 App 侧的 `sync.properties`（远程入口地址）—— 根 `.gitignore` 已覆盖。
配置模板只提交 `.env.local.example` / `.env.remote.example`，**里面只有占位符与开发默认值**。
**提交前体检**：`powershell -NoProfile -File ../tools/check-secrets.ps1`（默认只查暂存区，命中公网 IP / 真实口令 / 私钥即退出码 1）。
该脚本**刻意只写 ASCII**：Windows PowerShell 5.1 会把无 BOM 的 UTF-8 当 ANSI 读，中文注释会导致解析失败（已实测），故中文说明放在 `../docs/14_cloud_sync.md §17`。
服务端日志只记 `user_id`、计数与错误码，**不记 payload 内容**（DEC-034）。
