# F-15 云端同步 —— 设计（用户系统 + Python API 服务 + 本地↔云端一致）

> 状态：**设计完成，待评审后实现**（本轮只出设计，不动代码）
> 最后更新：2026-10-10
> 关联：DEC-031 / DEC-032 / DEC-033 / DEC-034、TASK-061~068、ARCHITECTURE §6+§19、DATABASE §8、PRD F-15
> 用户原话：「现在云服务已经部署好了，本地开发库正在部署，现在可以开发 api 服务了，让数据上云。1、加入用户系统。
> 2、开发 api 服务，用 python 开发。3、数据上云，但是本地不能丢，保证本地与云端一致，可以手动与云端数据同步，
> 或者本地上云或者云端下载。」

## 0. 四项已确认口径（2026-10-10 问答）

| 问题 | 结论 |
|---|---|
| 部署目标 | **先只在本机跑通**（本地开发库 + 本地 API）；上云（内网/公网）留到下一轮 |
| 数据库 | **MySQL**（用户 2026-10-10 明确「数据库不用 pg 用 mysql」）⇒ 代码**完全 DSN 驱动**：开发与单测走 SQLite（零依赖快速路径），生产/联调切 **MySQL 8**（PyMySQL 驱动），不改一行代码 |
| 用户系统 | **用户名 + 密码**，JWT 访问令牌 + 刷新令牌，**不做邮件验证**（零外部依赖） |
| 同步范围 | **全部核心业务表**：账本 / 分类 / 交易 / 债务 / 预算 / 分类规则 |

## 1. 目标与非目标

**目标**

1. **G1 数据上云**：本地账本可整体上传云端，账号即数据归属，换机/重装可完整恢复。
2. **G2 本地不丢**：任何同步动作都不得让本地数据减少或损坏；断网/中断/重复同步都安全。
3. **G3 本地与云端一致**：同一份数据在两端字段级一致（含金额按「分」、日期、分类、来源）。
4. **G4 三种手动模式**：双向同步 / 仅上传（本地→云）/ 仅下载（云→本地），用户在设置页手动触发。

**非目标（本轮不做，留接口或明确不做）**

- **不做自动后台同步**（WorkManager/SyncAdapter）——用户明确说「可以手动」，首版手动即可，避免后台耗电与权限争议。
- **不做多人共享账本 / 协作**；一个账号 = 一个人的数据。
- **不做云端端到端加密**（云端明文存储是本轮取舍，见 §6；本地仍是 SQLCipher 加密）。
- **不做公网部署与 HTTPS 证书**（本机跑通即可，§9 给出上云路径）。
- **不上云**：短信原文与发送方白名单、邮箱 IMAP 凭据、应用锁与外观等设备本地偏好（DEC-026 / DEC-023 边界不变）。

## 2. 总体架构

```
┌──────────────────────── Android App（离线优先） ────────────────────────┐
│  UI：设置 → 数据 → 云同步（账号 / 三个按钮 / 状态 / 冲突）               │
│  SyncUseCase（三模式编排 + 预览 + 冲突留档 + 同步前本地快照）            │
│  SyncApi（OkHttp + kotlinx-serialization，**仅手动触发**）               │
│  Room(SQLCipher)：业务表 + sync_changes(墓碑) + sync_state(基线)          │
│                    + sync_conflicts(冲突留档)                            │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ HTTPS(上云)/HTTP(本机)，Bearer JWT
┌───────────────────────────────▼─────────────────────────────────────────┐
│  FastAPI（server/app）：routers{auth, sync, health} + services/sync     │
│  SQLAlchemy 2.0 + Alembic；JWT(HS256) + argon2id 口令                  │
│  DB：SQLite（开发/单测）↔ MySQL 8（生产，DSN 切换，PyMySQL）             │
└─────────────────────────────────────────────────────────────────────────┘
```

**离线优先**：读路径永远只读本地 Room；网络只在用户点同步时使用。云端是「异地副本 + 换机恢复 + 多设备一致」，
**不是**任何写路径的前置依赖。

## 3. 服务端设计（Python）

### 3.1 选型与理由

| 项 | 选型 | 理由 |
|---|---|---|
| 框架 | **FastAPI** + Uvicorn | Pydantic 校验天然适配「DTO 即契约」，自带 OpenAPI 文档（联调省事），异步够用 |
| ORM | **SQLAlchemy 2.0**（`Mapped[]` 声明式） | 与 Alembic 同源；SQLite / MySQL 双支持 |
| 迁移 | **Alembic** | 建表与变更可回放，避免手写 DDL |
| MySQL 驱动 | **PyMySQL**（纯 Python） | 免编译，Windows 本机与容器都装得上；DSN `mysql+pymysql://…` |
| 配置 | `pydantic-settings` + `.env` | `DATABASE_URL` / `JWT_SECRET` / TTL 全环境变量驱动 |
| 口令 | **argon2id**（`argon2-cffi`） | 现代默认，抗 GPU 爆破 |
| 令牌 | 自签 **JWT HS256** + 刷新令牌落库 | 无外部依赖；刷新令牌可撤销/轮换 |
| 测试 | **pytest** + `TestClient` | 单测即接口契约验证，SQLite 临时库零依赖 |

**Python 版本**：本机 `3.12.0`（`D:\soft\python\3.12.0`）。**不用 3.14**——部分原生轮子（argon2/pydantic-core）在 3.14 上未必有预编译包，避免踩坑。

### 3.2 目录结构（新增 `server/`，与 Android 工程并列）

```
server/
  README.md                 # 起服务/迁移/测试三步命令
  requirements.txt          # 固定版本
  .env.example              # DATABASE_URL / JWT_SECRET / TOKEN_TTL / PULL_LIMIT
  alembic.ini
  alembic/versions/0001_init.py
  app/
    main.py                 # FastAPI 实例、路由挂载、异常→错误码映射、/healthz
    config.py               # Settings（pydantic-settings）
    db.py                   # engine / SessionLocal / get_db 依赖
    models.py               # ORM：users / refresh_tokens / devices / sync_counters + 6 张业务表 + sync_log
    schemas.py              # Pydantic DTO（含**逐实体 payload 校验表**）
    security.py             # 口令哈希、JWT 签发/校验、刷新令牌轮换
    deps.py                 # current_user（Bearer 解析 + 撤销校验）
    entity_spec.py          # 6 类实体的字段白名单与类型（服务端校验的单一来源）
    routers/{health,auth,sync}.py
    services/sync_service.py# push/pull 的合并逻辑（LWW + 墓碑 + seq）
  tests/
    conftest.py             # 临时 SQLite + 建表 + 客户端夹具
    test_auth.py test_sync_push.py test_sync_pull.py test_isolation.py
    test_conflict.py test_tombstone.py test_idempotency.py test_pagination.py
```

### 3.3 数据模型（服务端）

**安全表**

| 表 | 关键列 |
|---|---|
| `users` | `id` VARCHAR(36) PK、`username` VARCHAR(32) UNIQUE、`password_hash` VARCHAR(255)、`created_at`/`updated_at` DATETIME(6) UTC、`disabled` BOOL |
| `refresh_tokens` | `id` VARCHAR(36) PK、`user_id` VARCHAR(36) FK、`token_hash` VARCHAR(128)（仅存哈希）、`issued_at`/`expires_at`/`revoked_at` DATETIME(6)、`device_id` VARCHAR(36)、`rotated_from` VARCHAR(36) |
| `devices` | `id` VARCHAR(36) PK、`user_id` VARCHAR(36) FK、`name` VARCHAR(64)、`platform` VARCHAR(32)、`app_version` VARCHAR(32)、`last_seen_at`/`created_at` DATETIME(6) |

**同步表（6 张类型化业务表 + 1 张变更日志，DEC-037）**

| 表 | 关键列 |
|---|---|
| `ledgers` / `categories` / `transactions` / `debts` / `budgets` / `user_category_rules` | PK(`user_id` VARCHAR(36), `id` VARCHAR(64))；公共列：`ledger_id` VARCHAR(64)、`created_at_raw` VARCHAR(40)、`updated_at_raw` VARCHAR(64)、`updated_at` DATETIME(6)（归一化 UTC naive，LWW 依据）、`server_seq` BIGINT、`server_updated_at` DATETIME(6)；其余为**该实体的真实业务列**（如 `transactions.amount` BIGINT 存「分」、`date` VARCHAR(10)、`description` VARCHAR(512)）；索引按查询形状建（`(user_id,ledger_id,date)`、`(user_id,ledger_id,type)`、`(user_id,category_id)` 等） |
| `sync_log` | PK(`user_id`, `server_seq`)；`entity_type` VARCHAR(16)、`entity_id` VARCHAR(64)、`ledger_id` VARCHAR(64)、`deleted` BOOL、`updated_at` DATETIME(6)、`updated_at_raw` VARCHAR(64)、`logged_at` DATETIME(6)；索引 (`user_id`,`entity_type`,`entity_id`) —— **变更日志是拉取游标的唯一来源** |
| `sync_counters` | `name` VARCHAR(32) PK、`value` BIGINT —— 全局单调 `server_seq` 生成器（`SELECT … FOR UPDATE` 后自增，SQLite/MySQL 通用） |

**为什么改成「6 张真实业务表 + 变更日志」（DEC-037，推翻 DEC-033 的通用记录表）**：
- 通用记录表把业务字段塞进 `payload` JSON ⇒ 云端**无法用 SQL 查账**（连 `SUM(amount) GROUP BY date` 都做不到）。
  用户明确否决：「不要这样，要在云端做明确的表，设计表结构」「你上云的数据都啥，用户的账单数据没有上？」
- 现在每类实体一张表、每个字段一列：云端可直接聚合、可加索引、可接报表/BI。已验证：
  `SELECT date, SUM(amount) FROM transactions GROUP BY date` 在真实 MySQL 上直接可用。
- **写协议零改动**：上行/下行的 `payload` 形状与键名完全不变（DEC-036 仍然有效），Android 端**一行未改**。
  服务端在 `entity_store.py` 里做 `payload ⇄ 列` 的双向搬运（`shred()` / `reassemble()`），
  `entity_spec.py` 的逐实体白名单校验依旧是唯一入口（含**长度上限**，超长在写库前就 422）。
- **墓碑**：业务表**物理删除**该行，`sync_log` 里留一条 `deleted=1` ⇒ 业务表只放活数据，
  云端查账不会被墓碑行污染；拉取时墓碑照旧下发（客户端行为不变）。
- `created_at_raw` / `updated_at_raw` 保留客户端**原始 ISO 串**（下发时逐字回传，`sync_state.base_updated_at`
  的「同 `updated_at` = no-op」判定依赖它），`updated_at` 另存归一化 DATETIME(6) 专供 LWW 比较。
- 业务表之间**不建外键**（同批次的父子实体可能乱序到达）；`ledger_id` 只是普通维度列。
- `status.counts` 口径不变：按 `entity_type` 统计 `sync_log` 里的 **distinct `entity_id`**（含墓碑）⇒
  与旧实现的「记录行数」语义一致，客户端无需改。

**表清单（`entity_type` 枚举）**：`ledger` / `category` / `transaction` / `debt` / `budget` / `user_category_rule`。

**不上云**：短信原文、发送方白名单、IMAP 凭据、应用锁状态与外观偏好（设备本地性质，且属 DEC-026/023 隐私边界）。

### 3.4 双 DSN 与迁移（**生产 = MySQL 8**）

- `DATABASE_URL` 示例：
  - 开发/单测：`sqlite:///./dev.db`（零依赖，跑得快）
  - 生产/联调：`mysql+pymysql://minibook:<pw>@127.0.0.1:3306/minibook?charset=utf8mb4`
- 建表：`alembic upgrade head`；单测用 `Base.metadata.create_all()`（临时文件库，跑完即删）。
- 迁移链：`0001_init`（用户系统 + `sync_counters` + 旧通用记录表 `sync_records`）→ `0002_typed_entities`
  （建 6 张业务表 + `sync_log`，**把旧 `sync_records` 逐行拆列回填**，最后删掉旧表）。
  回填对脏行宽容：拆不出列的行只保留日志条目并打印告警，**绝不因为一行脏数据让整次迁移失败**；
  `downgrade 0001_init` 会把业务表 + 日志**拼回**旧 payload 形状（往返已在真实 MySQL 上真跑验证）。
- 所有时间列统一 **UTC**；客户端 ISO8601 带偏移 → 服务端归一化为 **UTC naive** 后比较（LWW 依据）。

**MySQL 特有口径（不写清就会踩的坑）**

| 项 | 口径 |
|---|---|
| 字符集 | 库/表/连接一律 **utf8mb4**（DSN `charset=utf8mb4` + `mysql_charset='utf8mb4'`），否则中文备注与 emoji 会坏 |
| 引擎 | **InnoDB**（`mysql_engine='InnoDB'`）——`sync_counters` 的行锁依赖它 |
| 字符串长度 | MySQL 索引列必须显式长度：`id`/`ledger_id` VARCHAR(64)、`user_id` VARCHAR(36)、`username` VARCHAR(32)、`entity_type` VARCHAR(16)、`entity_id` VARCHAR(64)；复合主键 (36+64)×4B = 400B，远低于 InnoDB 3072B 上限。**每个字段的列宽都与 `entity_spec.py` 的 `max_length` 逐一对齐**，由 `test_typed_storage.py` 断言——防「白名单放行、MySQL 悄悄截断」 |
| 业务列类型 | 金额 `BIGINT`（存「分」，禁用浮点）、日期 `VARCHAR(10)`、月份 `VARCHAR(7)`、布尔 `BOOL`；**升级后的 DDL 里不再有 JSON 列**（由 `test_typed_tables_ddl_has_no_json_column` 断言） |
| **时间精度** | `updated_at` 用 **DATETIME(6)**：`sa.DateTime().with_variant(mysql.DATETIME(fsp=6), "mysql")`——MySQL 默认 DATETIME **无小数秒**，不指定 `fsp` 会截掉毫秒，导致「同 `updated_at` = no-op」判定失真 |
| **大小写** | MySQL 默认排序规则大小写**不敏感**、SQLite **敏感** ⇒ 用户名在注册/登录时**统一转小写**，两端行为一致（否则单测绿、MySQL 上「Admin 与 admin 撞车」） |
| 游标自增 | `SELECT value … FOR UPDATE` 再 `UPDATE value+1`（InnoDB 行锁；SQLite 单写者天然串行） |
| 时区 | 不依赖服务器时区：Python 侧写 UTC naive；容器启动带 `--default-time-zone=+00:00` |

### 3.5 API 契约（前缀 `/api/v1`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/healthz` | 存活探针（不鉴权） |
| POST | `/auth/register` | `{username, password}` → `{user_id, username}`；用户名 3~32 位 `[A-Za-z0-9_.-]`；口令 ≥ 8 位 |
| POST | `/auth/login` | → `{access_token, refresh_token, token_type, expires_in, user}` |
| POST | `/auth/refresh` | `{refresh_token}` → 新 access + **新 refresh（轮换，旧的作废）** |
| POST | `/auth/logout` | `{refresh_token}` → 撤销该刷新令牌（幂等） |
| GET | `/me` | 当前用户信息 |
| POST | `/sync/push` | `{device_id, mode?, items:[{entity_type, entity_id, ledger_id, updated_at, deleted, payload}]}` → `{applied, conflicts:[{entity_type, entity_id, server:{...}}], server_seq}` |
| GET | `/sync/pull?since_seq=&limit=` | → `{items:[{实体行原样}], next_seq, has_more}`（`limit` 默认 200，上限 500） |
| GET | `/sync/status` | → `{server_seq, counts:{entity_type: n}, server_time}` |

约定：
- **`entity_id` 由客户端生成**（UUID 文本，本地表主键即 UUID）⇒ 幂等天然成立，服务端不重编 ID。
- 每条记录自带 `ledger_id`；`ledger` 实体自身的 `ledger_id` = 其 `entity_id`（自指，便于统一过滤）。
- 错误体统一 `{"error":{"code":"...","message":"..."}}`；错误码：`invalid_credentials` / `username_taken` / `validation_error` /
  `token_expired` / `token_invalid` / `token_revoked` / `not_found` / `rate_limited` / `server_error`。
- 金额一律整数（分）；服务端对 `amount`/`value` 等字段做整数校验，**拒绝浮点**。

### 3.6 认证与隔离（DEC-032）

- 注册即建用户；**同用户名唯一**（唯一索引 + 冲突转 `username_taken`）。
- 口令 `argon2id` 哈希；登录失败**不区分**「用户不存在/口令错」（统一 `invalid_credentials`）。
- Access Token：JWT HS256，含 `sub=user_id`、`jti`、`iat`、`exp`（默认 30 分钟）。
- Refresh Token：随机 32B（只存哈希），默认 30 天；**每次刷新都轮换**，旧的立即作废；`logout` 撤销。
- **越权隔离是硬约束**：所有同步读写都带 `user_id = current_user.id` 过滤，任何按 `entity_id` 的查询都必须同时带 `user_id`
  （禁止「先按 id 查再判归属」——直接把 `user_id` 写进 WHERE）。
- 限流（轻量）：登录/注册按 IP + 用户名内存计数（如 10 次/分钟），触发返回 `rate_limited`。

### 3.7 幂等、分页与失败语义

- **push 幂等**：业务表主键 (`user_id`,`id`)，重复提交同一 `updated_at` 视为 no-op（不涨 `server_seq`，也不写 `sync_log`）。
- **pull 游标**：`server_seq` 单调递增（同事务内自增），`since_seq` 断点续传；`has_more` 指示继续拉。
- **失败**：任何一条校验失败 → **整批回滚**并返回 `validation_error` + 定位（`index` + `entity_id`）；不做部分写入，
  避免「一半上传成功」造成的两端不一致。

## 4. 同步协议（DEC-033）

### 4.1 三模式

| 模式 | 行为 | 用途 |
|---|---|---|
| **BIDIRECTIONAL 双向同步** | 先 push 本地变更 → 再 pull 云端变更 | 日常主用法 |
| **UPLOAD 仅上传** | 只 push（本地 → 云） | 第一次上云、确认本地是权威时 |
| **DOWNLOAD 仅下载** | 只 pull（云 → 本地） | 换机/重装恢复；另一端是权威时 |

**「仅下载」的硬口径**：只做 upsert + 应用云端墓碑，**绝不因为「云端没有」而删除本地任何行**（防止新账号把本地清空）。

### 4.2 客户端同步状态（本地）

| 状态 | 存放 | 说明 |
|---|---|---|
| `base_url` / `username` / `user_id` | DataStore | 非敏感 |
| `access_token` / `refresh_token` | DataStore（**Android Keystore 包裹后 base64**） | 复用现有 Keystore 能力；绝不明文落盘 |
| `access_expires_at` | DataStore | 过期前自动静默刷新一次 |
| `device_id` | DataStore | 首次生成 UUID，随请求上报，用于设备列表与令牌管理 |
| `server_cursor` | DataStore | 上次成功 pull 的 `server_seq` |
| `last_pushed_at` | DataStore | 上次成功 push 的时刻（ISO） |
| `last_sync_at` / `last_sync_mode` / `conflict_count` | DataStore | UI 展示 |
| `sync_state(entity_type, entity_id, base_updated_at, synced_at)` | **Room 新表** | 每次成功同步后记「已达成一致的版本」，三路冲突检测的 base |
| `sync_conflicts(...)` | **Room 新表** | 冲突输家副本留档（可查看、后续可切换保留） |
| `sync_changes`（**既有表**） | Room | ⚠️ **已修订（DEC-035）**：本表本轮**不写**；墓碑改由 `sync_state` 差分补发。保留该表作二期变更审计 |

### 4.3 push 流程

1. 取待上行集合：`updated_at >= last_pushed_at` 的业务行（`>=` 而非 `>`：同秒多次编辑不漏，多余重推幂等无害）
    + `sync_changes` 中未发送的 `delete` 墓碑。
2. 服务端逐条：不存在 → 插入（分配 `server_seq`）；存在且 `updated_at` 相同 → no-op；
   存在且**客户端更新** → 覆盖并分配新 `server_seq`；存在且**服务端更新** → 判定冲突（见 §4.5），返回服务端版本。
3. 客户端按响应：`applied` 的行写 `sync_state.base_updated_at`；`conflicts` 的行**保留赢家**并写 `sync_conflicts` 一条（留输家副本）。
4. 成功后才推进 `last_pushed_at`（失败不推进 ⇒ 下次可重试，不丢变更）。

### 4.4 pull 流程

1. `GET /sync/pull?since_seq=server_cursor&limit=200` 循环直到 `has_more=false`。
2. 每条：`deleted=true` → 本地**删对应行**（含多账本作用域）；否则 upsert（按 `entity_id`）。
3. 三路比较决定本地覆盖：本地未改过（`updated_at == base`）→ 直接写云端版本；本地改过且云端更新 → 写云端版本 + `sync_conflicts` 留本地副本；
   本地更新 → 保留本地（下次 push 上行）。
4. 推进 `server_cursor = next_seq`，刷新 `sync_state`。

### 4.5 冲突策略（LWW + 留档，绝不静默丢）

- 判据：`base`（上次一致版本）、`local.updated_at`、`remote.updated_at` 三路比较。
- 结果：`updated_at` 较新者胜；**输家写入 `sync_conflicts`**（本地 JSON + 云端 JSON + 检测时间），
  UI 显示「冲突 N 条」，可查看两条内容（本轮只留档 + 计数，不做逐条选择 UI）。
- 已知局限（如实写）：客户端时钟偏移会让 LWW 判错——所以**必须留档**，用户可人工纠正；上云二期可加服务端时间仲裁。

### 4.6 删除与墓碑

- ⚠️ **已修订（DEC-035）**：本地删除**不**再写 `sync_changes`，而是由「`sync_state` 有、本地已无」的
  差分在下次上行补发墓碑（`deleted=true`），成功后清基线。理由与硬约束见 DEC-035。
- 云端墓碑同样通过 pull 下发 → 对端本地删除该行（这是**唯一**会减少本地数据的情形，且是用户自己的删除意图）。
- 墓碑保留策略：服务端把该实体从业务表**物理删除**，并在 `sync_log` 留一条 `deleted=1`（业务表只放活数据，
  云端查账不被墓碑行污染；DEC-037）；客户端 `sync_changes` 中已上行成功的墓碑在**成功后清理**。

### 4.7 「本地不丢」的五道保险

1. **同步前自动本地快照**：复用 F-04 明文 JSON 备份，写到 `files/sync_backup/<yyyyMMdd-HHmmss>.json`（保留最近 3 份）。
2. **删除只走墓碑**，且「仅下载」不因云端缺失而删本地。
3. **冲突留档**（输家副本不丢）。
4. **幂等 + 失败不推进游标**：断网/中断后重试不产生重复、不跳过变更。
5. **dry-run 预览**：点同步先算「将上传 N 条 / 将下载 M 条 / 冲突 K 条 / 将删除 D 条」，用户确认后才执行（D>0 时二次确认）。

## 5. Android 端设计

### 5.1 新增依赖（唯一新增：网络栈）

- **OkHttp**（新）+ **kotlinx-serialization-json 1.8.1**（**已有**，连同 serialization 插件）+ DataStore（**已有**）。
- 不引 Retrofit/Ktor/WorkManager：接口只有 8 个，手写一个 `SyncApi` 更省；手动同步不需要 WorkManager。

### 5.2 分层落点（延续 ARCHITECTURE §6「Repository 零感知」）

```
data/remote/
  SyncApi.kt            # OkHttp 调用 + kotlinx-serialization DTO
  SyncDtos.kt           # 与服务端 schemas.py 一一对应
  SyncAuthStore.kt      # DataStore + Keystore 包裹（token/游标/账号/device_id）
  SyncTokenAuthenticator.kt # 401 → 刷新一次 → 重试
domain/usecase/sync/
  SyncPlan.kt           # 纯函数：本地 diff → 待上行/待下行/冲突（可 JVM 单测）
  SyncUseCase.kt        # 三模式编排 + 快照 + 墓碑 + 冲突留档 + 结果汇总
ui/settings/
  SettingsCloudScreen.kt# 设置 → 数据 → 云同步（账号/服务器地址/三按钮/状态/冲突）
  SyncPresenter.kt      # 文案与计数（纯函数，可单测）
```

### 5.3 Room v3 → v4 迁移（**不改既有表**）

| 变更 | 内容 |
|---|---|
| 新表 `sync_state` | `entityType`+`entityId` 复合主键、`baseUpdatedAt`、`syncedAt` |
| 新表 `sync_conflicts` | `id` PK、`entityType`、`entityId`、`ledgerId`、`localJson`、`remoteJson`、`detectedAt`、`resolvedAt`、`resolution` |
| 既有表 | **零改动**（`sync_changes` 直接复用为墓碑日志；`syncStatus` 列本轮不接线，避免无谓写放大） |

配套：`MIGRATION_3_4` + `app/schemas/4.json`（入库）+ `MigrationTestHelper` 迁移测试（沿用既有基建）。

### 5.4 同步 UseCase 与 UI

- 入口：**设置 → 数据 → 云同步**（新增二级页 `settings-cloud`），页内：服务器地址、用户名/口令（登录/注册/退出）、
  三个按钮（双向同步 / 仅上传 / 仅下载）、状态行（上次同步时间、云端记录数、冲突数）、冲突入口。
- 交互：点按钮 → 预览卡（上传 N / 下载 M / 冲突 K / 删除 D）→ 确认执行 → 结果卡（成功/失败原因 + 数字）。
- 文案只出现计数与时间，**不出现账目明细**（沿用 F-14 的隐私口径）。

### 5.5 写路径唯一侵入点

只有「删除」需要额外动作：在现有 `deleteById` 的事务内追加一条 `sync_changes(action='delete')`（tx/debt/category/budget/rule + ledger）。
**新增/修改不动写路径**（靠 `updatedAt` 差分 + `>=` 游标覆盖）。

## 6. 安全与隐私边界（DEC-034）

| 项 | 口径 |
|---|---|
| 传输 | 本机/内网开发允许 HTTP（明示风险）；**公网必须 HTTPS**（Nginx + 证书，二期） |
| 云端存储 | 明文（服务端数据库）。**取**：实现难度与「本地仍加密」；**舍**：云端被拖库即明文暴露 ⇒ 上公网前必须做磁盘加密 + 最小权限 + 备份加密 |
| 口令 | argon2id；服务端永不解密回原文 |
| 令牌 | Access 短寿（30 min）；Refresh 只存哈希、轮换、可撤销；客户端用 Keystore 包裹后落 DataStore |
| 上云数据 | 6 类业务实体（含交易备注）；**不含**短信原文、白名单、IMAP 凭据、应用锁/外观偏好 |
| 日志 | 服务端只记 `user_id` / 计数 / 错误码；**不记 payload 内容**（避免账目落到日志） |

## 7. 验收标准（可测断言）

| 编号 | 断言 |
|---|---|
| A1 | 注册/登录/刷新（轮换）/登出/`/me` 全通；重复用户名 → `username_taken`；错口令 → `invalid_credentials` |
| A2 | **越权隔离**：用户 A 的 token 拉取/推送永不触及 B 的行（pytest 断言两端数据互不可见） |
| A3 | **幂等**：同一批 push 连发两次 → 云端行数不变、`server_seq` 不重复增长 |
| A4 | **冲突**：两端改同一行 → 后 push 者得 `conflicts`，本地多一条 `sync_conflicts`，最终两端同持赢家版本 |
| A5 | **墓碑**：A 删一条 → push → B pull → B 本地该行消失，且 B 不会回推它 |
| A6 | **分页**：> 500 行时 pull 分页无重复无遗漏，游标正确推进 |
| A7 | **本地不丢**：同步前后本地条数与金额合计一致（或仅按用户自己的墓碑减少）；`files/sync_backup/` 有快照 |
| A8 | **断网/中断**：请求失败时本地数据不变、游标不推进，重试后结果正确 |
| A9 | **仅下载不删本地**：新账号（云端空）+ 本地有数据 → 执行「仅下载」后本地条数不变 |
| A10 | **金额精度**：全程整数「分」传输，JSON 中无浮点；汇总与本地一致 |
| A11 | **Room 迁移**：3→4 迁移测试通过，既有数据保留，新表可用 |

## 8. 测试计划

- **服务端 pytest**：认证 8 例、隔离 4 例、push 6 例（新增/更新/no-op/冲突/校验失败回滚/幂等）、pull 5 例（空/增量/分页/墓碑/游标）、
  状态 2 例；覆盖 A1~A6。
- **Android JVM 单测**：`SyncPlan` 纯函数（diff/冲突判定/三模式分支/墓碑合并）+ `SyncPresenter` 文案 → 覆盖 A9/A10 的判定逻辑。
- **Room 迁移测试**：`MigrationTestHelper` 3→4 → A11。
- **手工端到端**：本机起 uvicorn → 真机 `A1B2C3D4` 登录 → 首次上传 → 再用第二台设备/清库设备「仅下载」→ 条数与金额一致 →
  两端各改一条再双向同步 → 冲突留档可见；全程核对 A7/A8。
- **双 DSN 验证（MySQL 为交付口径）**：SQLite 跑一遍全量测试（快速回归）；**再用 MySQL 8 跑同一套测试**——
  本机没有 MySQL，跑法见 §10「未决 1」（等你给 DSN / 确认安装方式 / 用容器起一个测试库）。
  **MySQL 未跑通 = 本轮不算完成**（F-14 教训：JVM 全绿而真机静默失效，引擎差异同理）。

## 9. 部署与上云路径

| 阶段 | 做法 |
|---|---|
| **本轮（本机）** | `python -m venv .venv` → 装 `requirements.txt` → `alembic upgrade head` → `uvicorn app.main:app --host 0.0.0.0 --port 8000`；真机同 Wi-Fi 用 `http://<PC 内网 IP>:8000` |
| 内网（`192.168.190.130`） | Docker compose：**`mysql:8`**（`--character-set-server=utf8mb4 --default-time-zone=+00:00`，数据卷独立）+ api 容器（独立目录/端口/网络，**不动现存的 Nexus 与 `/opt/dt-mw`**）+ systemd 或 compose restart |
| 公网 | 域名 + Nginx 反代 + Let's Encrypt HTTPS；服务端加固（数据库不裸露公网、备份加密、日志脱敏） |

## 10. 风险与未决

| 项 | 说明 / 处置 |
|---|---|
| **MySQL 环境未就绪（唯一阻塞项）** | 本机无 MySQL（无服务/无客户端/3306 未监听）。三条路：**① 你给 MySQL DSN（推荐，直接用你正在部署的库）② 你允许我在本机装 MySQL 8 ③ 允许我在 `192.168.190.130` 的 Docker 上起一个 `mysql:8` 仅作测试库**。未定之前：代码与 SQLite 测试照常推进 |
| 引擎差异（SQLite ↔ MySQL） | 大小写敏感、DATETIME 精度、索引长度、JSON 类型、DDL 语义都不同 ⇒ §3.4 已列口径，且 **MySQL 必跑同套测试**才算交付 |
| 客户端时钟偏移影响 LWW | 冲突留档兜底 + 文档明示；二期可加服务端时间仲裁 |
| 交易备注上云（隐私） | 默认上云（账目完整性优先），同步页明示；如需排除备注，二期加开关 |
| 公网 HTTPS 与加固 | 本轮不做，§9 已列清单（上公网前必须完成） |
| 多设备并发写同一账本 | LWW + 冲突留档可接受；不做 CRDT（个人记账场景不值得） |

## 11. 决策与任务索引

- 决策：**DEC-031**（服务端选型与双 DSN）、**DEC-032**（用户系统与令牌）、**DEC-033**（同步协议/墓碑/LWW+留档）、
  **DEC-034**（上云隐私边界与同步前快照）、**DEC-036**（payload = Room 行原样 JSON）、**DEC-037**（云端 = 6 张真实业务表 + `sync_log`）。
- 任务：**TASK-061**~**TASK-068**（见 `06_tasks.md`）。
- 架构：`02_architecture.md §19`（本节指针）。
- 数据库：`03_database.md §8`（本地 v4 迁移与云端 schema 指针）。

## 12. 实现状态与实现差分（2026-10-10）

### 12.1 已实现（服务端 `server/` + Android 云端网络栈）

| 项 | 状态 | 证据 |
|---|---|---|
| 用户系统（注册 / 登录 / 刷新轮换 / 登出 / `me`、argon2id、JWT、登录限流） | ✅ | `server/tests/test_auth.py`（11 例） |
| 同步接口（push / pull / status、墓碑、幂等、冲突、整批回滚） | ✅ | `test_sync_push.py`(10) + `test_sync_pull.py`(5) + `test_idempotency.py`(3) + `test_conflict.py`(4) |
| 账号隔离（只认 Bearer；伪造头无效） | ✅ | `test_isolation.py`（4 例） |
| 6 类实体字段白名单校验 | ✅ | `server/app/entity_spec.py` + `test_sync_push.py` |
| Alembic 建表（**MySQL 8 真连库**） | ✅ | 2026-10-10 于 `192.168.190.130:49779/minibook` 建 5 表：InnoDB + **utf8mb4_unicode_ci** + `DATETIME(6)` + `JSON`；`downgrade base ⇄ upgrade head` 双向真跑通过；`test_migration.py`（5 例） |
| 时间解析（Android 纳秒 ISO） | ✅ | `test_timeutil.py`（5 例） |
| 中文经 `HTTP API → MySQL → 拉回`逐字一致 | ✅ | 冒烟脚本（临时，已删）：`name` 23 字符 / 44 UTF-8 字节，`equal=True`；另有直连 SQL 探针（9 字符中文 + `DATETIME(6)` 微秒 `123456` + JSON 列） |
| Android 云端网络栈（TASK-065：OkHttp + DTO + 令牌 Keystore 包裹 + 401→刷新一次→重试） | ✅ | `app/src/test/java/com/minibook/data/remote/`：**30 例 / 0 失败**（DTO 口径 7 + 错误码 5 + 地址校验 6 + AuthStore 6 + 令牌编排 6） |
| Android 同步核心（TASK-066：Room v3→v4 两新表 + `SyncPlan` 三模式/三路比较/LWW + 快照 + 墓碑差分 + 冲突留档） | ✅ | `SyncPlanTest`（15 例）+ `SyncPayloadContractTest`（跨语言白名单守卫）+ `SyncEntityAdapter`/`SyncUseCase` |
| Android 同步 UI（TASK-067：设置 → 数据 → 云同步；账号 / 三模式 / 预览确认 / 结果卡 / 冲突留档入口） | ✅ | `SyncPresenterTest` 21 例；纳入全量 JVM 481 例 / 0 失败 |
| **日志不含 payload 的自动化断言** | ✅ 已完成 | `server/tests/test_log_privacy.py` 3 例（TASK-064 闭合） |

测试结果（2026-10-10 收口；DEC-037 存储改版后复跑同样双绿）：
- **双 DSN 双绿**：同一套 `pytest` **61 例 / 0 失败**（含 TASK-064 的 3 例日志隐私断言、DEC-037 的 `test_typed_storage.py`
  与 `test_migration.py` 回填/降级往返）分别跑在 **MySQL 8**（`minibook_test`）与 **SQLite** 上，两次 `exit=0`。
- MySQL 侧不再只是「离线渲染 DDL」：`alembic upgrade head` 真连库建表，`downgrade base ⇄ upgrade head` 真跑通；
  **真实开发库**上还做了 `0001_init ⇄ 0002_typed_entities` 的降级→回填往返（5 行旧数据全部拆列，0 行跳过）。
- Android 侧 `:app:testDebugUnitTest --tests "com.minibook.data.remote.*"` → **30 例 / 0 失败**（`BUILD SUCCESSFUL`，junit XML 逐类核验）。

### 12.2 实现差分（与 §3/§4 设计原文的出入，均已落进代码注释）

| 设计原文 | 实现 | 原因 |
|---|---|---|
| 游标 `SELECT … FOR UPDATE` 后自增 | `UPDATE sync_counters SET value = value + 1` 再读回 | 单条语句即取行锁，SQLite/MySQL 通用；首行 `server_seq = 0` 由迁移写入，`next_seq()` 只做自增（缺行直接 `server_error`，不静默重建） |
| 只用 `updated_at` | 增加 `updated_at_raw VARCHAR(64)` | 回传/比对用**客户端原始 ISO 串**。若回传服务端归一化格式，客户端 `sync_state.base_updated_at` 与往返值不相等，会把**重复 push 的 no-op 误判成冲突** |
| `GET /sync/status` 返回 `server_seq` | 拆为 `server_seq`（本账号游标上限）+ `server_seq_global`（全局计数器） | 计数器**全局共享**（跨账号），客户端若拿全局值当拉取游标会困惑 |
| 时间解析未细化 | `parse_iso_utc()` 先把小数位截断到 6 位 | Android `TimeFormatter.nowIso()` 是**纳秒**（9 位小数），Python `fromisoformat` 只稳吃 6 位 |
| JWT epoch 未细化 | 一律用 **aware** UTC 计算 `iat`/`exp` | 实测踩坑：naive datetime 的 `.timestamp()` 按**本地时区**解释，+08:00 机器上 `exp` 少算 8 小时 ⇒ **刚签发的令牌立刻「已过期」**（首轮 47 例中 20 例因此 401） |
| 限流 | 单进程内存滑动窗口（`app/ratelimit.py`） | 不引 Redis；单进程 uvicorn 够用，多实例部署需替换（登记为二期） |
| 校验失败错误体 | **不回显 `input`**（只留 `loc`/`msg`/`type`） | Pydantic 默认详情带原始输入，可能含口令/备注（DEC-034 隐私口径） |
| `alembic.ini` | 注释全 ASCII | configparser 按本地编码（cp936）读 ini，中文注释直接 `UnicodeDecodeError` |
| `env.py` 用 `config.set_main_option()` 回写 URL | **只读取、不回写**，URL 原样交给 `create_engine` | alembic 的 ConfigParser 走 `BasicInterpolation`：值里带 `%` 当场 `ValueError: invalid interpolation syntax`。真实口令 URL 编码后必带 `%`（如 `user@123` → `user%40123`），实测 `alembic upgrade head` 因此在 MySQL 上**直接失败** |
| 表参数只给 `CHARSET=utf8mb4` | 显式加 `COLLATE utf8mb4_unicode_ci` | MySQL 8 对 `CHARSET=utf8mb4` 默认给 **`utf8mb4_0900_ai_ci`**，与部署口径（`collation-server=utf8mb4_unicode_ci`）不一致（实测内网 8.0.46） |
| `downgrade()` 显式 `drop_index` 再 `drop_table` | **只 `drop_table`**（先子表、后父表） | MySQL 上 `DROP INDEX ix_refresh_tokens_user` 撞外键支撑索引：`errno 1553 Cannot drop index …: needed in a foreign key constraint`，且中途失败留下**半删库**。SQLite 无此约束 —— 这类差异只有双 DSN 才暴露 |
| health 测试断言 `db == "sqlite"` | 断言与**真实引擎**一致（`app.db.engine.dialect.name` ∈ {sqlite, mysql}） | 双 DSN 下测试自身不能把 SQLite 写死，否则 MySQL 轮必然假红（本轮实测就是它先红） |
| 401 刷新用 OkHttp `Authenticator` | **suspend 包装器**（`SyncTokenAuthenticator`） | `Authenticator` 是同步回调、需阻塞式刷新；suspend 版与协程链一致，且能用假 `SyncRemote` 在 JVM 断言「只重试一次」「网络故障**不清**账号态」「令牌撤销才清」 |
| 令牌包裹复用 `KeyManager` | 新增独立 `SecretCipher` + Keystore 别名 `minibook_sync_token` | 开库路径已过真机验证，**不动它**可把改动半径限制在同步功能内；`SecretCipher` 接口让 AuthStore 在 JVM 用假实现可测 |
| 明文 HTTP | 仅 **debug** 清单开 `usesCleartextTraffic`，release 沿用平台默认（禁明文） | 既满足「本机 / 内网联调」，又不让正式包降级安全；被系统拦截时单独映射 `CLEARTEXT_BLOCKED` 并给出可操作提示 |
| `hasTokens` = 密文非空 | 必须**真的解得开**才算有令牌 | 只看非空会误报「已登录」（Keystore key 失效时每次同步都撞 `NOT_LOGGED_IN`）；实测被单测抓出 |
| 服务器地址只判 scheme | 追加**主机名/端口白名单**校验（含 IPv6 字面量） | `://x`、`a b:8000`、端口 `99999` 这类畸形输入必须在校验层拒掉，而不是留给 OkHttp 抛异常 |
| §4.2/§4.6：删除时写 `sync_changes(action='delete')` 作墓碑 | **改用 `sync_state` 差分补发墓碑**（DEC-035） | 原方案要改 5+ 处写路径（漏一处即静默不同步），且「删账本」的批量清表与「导入替换」根本不经过写路径。差分法**零侵入**且覆盖全部删除来源；硬约束是差分必须用**完整本地集合**（故同步取全量投影而非 `updated_at >= 游标` 的增量，否则未加载的行会被误判为已删、误发墓碑删掉云端数据） |
| §4.3：上行集合取 `updated_at >= last_pushed_at` | 上行判据 = **与 `sync_state.base_updated_at` 不一致**（DEC-036） | 时钟回拨会让 `>=` 漏推；基线比对是精确判据，且与墓碑差分共用同一份本地集合。`last_pushed_at` 退化为纯展示字段 |
| §5.2：`SyncApi` 手写 DTO ↔ 实体 mapper | 6 张实体直接 `@Serializable`，payload = **Room 行原样 JSON**；新增 `SyncPayloadContractTest` 对照服务端白名单（DEC-036） | 手写 mapper 的漂移**不可见**（真机同步才以 `未知字段/缺少字段` 爆炸）。原样 JSON + 跨语言键集断言把漂移挡在 JVM 单测里 |
| 可空列无默认值 | 可空字段补 `= null` 默认值 | 服务端按 `explicitNulls=false` 存 payload 会**省略 null 键**，回传时缺键仍须能反序列化（Room schema 不受影响） |

另：`payload` 内的字段名保持 **camelCase 原样**（不再转 snake_case），并把信封与内容的一致性做成硬校验
（`payload["id"] == entity_id`、`payload["ledgerId"] == ledger_id`、`payload["updatedAt"] == updated_at`）——
少一层命名转换、少一类静默错位。

### 12.3 待补（诚实挂账，不得声称已完成）

1. ~~**日志断言**（TASK-064 未闭合）~~ → **已闭合（2026-10-10）**：`server/tests/test_log_privacy.py` 3 例
   （push / pull / 整批校验失败），断言日志文本中检索不到备注哨兵、金额哨兵与 `"payload"` / `"description"` 字段名；
   双 DSN 各 52 例 / 0 失败。
2. ~~**MySQL 全量测试**~~ → **已完成（2026-10-10）**：`TEST_DATABASE_URL` 指向 `minibook_test`，`pytest` **49 例 / 0 失败**；
   `alembic upgrade head` 与 `downgrade base ⇄ upgrade head` 均在真 MySQL 8.0.46 上跑通（详见 §12.1）。
3. ~~**真机 / 局域网端到端（TASK-068 剩余部分）未做**~~ → **模拟器部分已完成（2026-10-10）**：
   模拟器 Pixel_7（`emulator-5554`）`SyncDeviceE2ETest` 走生产代码路径（真 Room v4 + 真 OkHttp + 真 UseCase）
   A 注册 → 推 5 实体 → B「仅下载」→ 两端一致 → 冲突留档 → 断网游标不动 → 重试成功；
   `connectedDebugAndroidTest` **18 例 / 0 跳过 / 0 失败**（含 `MigrationV3To4Test`），DEC-037 改版后复跑同样全绿。
4. **真机端到端（TASK-068）仍未做**：真机 `A1B2C3D4` 装着用户真实数据的正式版且签名不同，装 debug 包会要求卸载，
   故**需用户决定**（建议「导出备份 → 装 debug → 验证 → 还原」，或明确维持「真机不测」）。
   客户端↔服务端**真实 HTTP** 端到端已由 `SyncApiE2ETest` 覆盖并通过（2026-10-10，1 例 / 0 跳过）。
5. **并发写同一实体的竞态**：两端同时 push 同一条**尚不存在**的记录时，后到者可能命中主键冲突并收到
   `server_error`（个人单机手动同步场景概率极低，重试即幂等通过）；二期可改为 upsert（`ON DUPLICATE KEY UPDATE`）。
6. **多进程/多实例部署**：登录限流是单进程内存实现，需换 Redis 后才可多实例（当前部署形态为单进程 uvicorn）。
7. **Android Keystore 真机行为未测**：JVM 单测用 `FakeSecretCipher` 替代，「落盘非明文」已用真 DataStore 文件字节断言；
   但 Keystore key 的失效 / 换机恢复路径只能真机验证。

## 13. 存储改版：JSON 通用表 → 6 张真实业务表（DEC-037，2026-10-10）

### 13.1 为什么改

用户验收云端库时明确否决通用记录表：「不要这样，要在云端做明确的表，设计表结构」。
根因很直接：业务字段躺在 `payload` JSON 里，云端**只能用应用层代码查账**，SQL 连
`SELECT date, SUM(amount) FROM transactions GROUP BY date` 都做不到 —— 「数据上云」就只剩搬运、没有可用性。

### 13.2 改了什么（服务端独占，**客户端零改动**）

| 项 | 改前（DEC-033） | 改后（DEC-037） |
|---|---|---|
| 存储 | `sync_records`：PK(`user_id`,`entity_type`,`entity_id`)，业务字段在 `payload` **JSON** | 6 张类型化业务表，PK(`user_id`,`id`)，一字段一列（金额 `BIGINT` 分、`date` VARCHAR(10)、`month` VARCHAR(7)） |
| 变更流 | 同表的 `server_seq` 列 | 独立 `sync_log`（PK `user_id`+`server_seq`），每次 push 追加一条 |
| 墓碑 | 行常驻 `deleted=1` | 业务表**物理删行** + 日志留 `deleted=1` |
| 原始时间串 | `updated_at_raw` | `created_at_raw` + `updated_at_raw` 都留（逐字回传），另存归一化 `updated_at DATETIME(6)` 供 LWW |
| 长度校验 | 白名单只管类型 | 白名单带 `max_length`，与列宽逐一对齐，超长**写库前** 422 |
| 拉取 | 扫 `sync_records` | 扫 `sync_log` 按 `server_seq`，再按类型批量取活行 |
| 协议 | — | **完全不变**：`payload` 形状/键名/DEC-036 语义/三模式/冲突留档/批量回滚 —— Android 一行未改 |

**关键不变量**：push 返回的新 `server_seq` **不得**用来推进客户端游标（同窗口内其它设备的变更会被永久跳过）；
只有 pull 成功才推进（故首次双向同步合法地停在游标 0，`SyncApiE2ETest` 与设备 E2E 都断言了这点）。

### 13.3 迁移 `0002_typed_entities`

建 6 张业务表 + `sync_log` → 逐行拆列回填 → 删旧表；`downgrade 0001_init` 反向把业务表 + 日志**拼回**旧 payload 形状。
回填对缺字段/脏行宽容：拆不出列的行只保留日志条目并打印告警，**绝不因一行脏数据让整次迁移失败**。

### 13.4 验证（全部真跑，非推演）

| 项 | 结果 |
|---|---|
| 双 DSN pytest | SQLite **61 例 / 0 失败**；MySQL 8（`minibook_test`）**61 例 / 0 失败** |
| 真库迁移 | 开发库 `minibook`：`0001_init → 0002_typed_entities`，5 行旧数据全部拆列、**0 行跳过**，旧表删除 |
| 真库往返 | `downgrade 0001_init`（→ 旧 payload 逐字拼回）→ `upgrade head`（5 行重新拆列，0 跳过） |
| 云端 SQL 聚合 | 真 MySQL：`SELECT date, SUM(amount) FROM transactions GROUP BY date` → `2026-10-08` 共 **88.88 元** |
| 端到端 | JVM `SyncApiE2ETest`（真实 HTTP）+ 模拟器 `SyncDeviceE2ETest`（A 上传 → B 下载 → 冲突留档 → 断网重试）改版后复跑通过 |

### 13.5 踩坑（已修，留档）

1. **payload 会省略 null 键**：Kotlin 侧 `explicitNulls=false`，可空字段整个不出现。拆列**必须** `.get(key)`；
   用 `payload[key]` 会把真实历史行判成脏数据跳过（实测：真库 10 行里 6 条交易被跳过）。
   已修 `entity_store.shred()` 并补回归用例 `test_payload_may_omit_nullable_fields`（双 DSN 跑）。
2. **业务表的实体列叫 `id`**，不是 `entity_id`（`entity_id` 只存在于 `sync_log`）；降级拼回时踩过一次。
3. **离线模式（`alembic upgrade --sql`）没有连接**：迁移里的数据搬运必须用 `op.get_context().as_sql` 判掉，
   否则渲染 DDL 直接 `AttributeError: 'NoneType' has no attribute 'mappings'`。
4. **DDL 里查 JSON 列要分步**：`sync_records`（含 JSON 列）是 0001 建的、0002 才删 ⇒ 整链 DDL 里必然出现 `JSON`，
   断言只能渲染 `0001_init:0002_typed_entities` 这一段。
