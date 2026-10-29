# Changelog

本项目所有重要变更均会记录在此文件中。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Changed
- **云端由腾讯云迁至阿里云（2026-10-29）**：新机为「API 容器只发布 `127.0.0.1:8000` + nginx 监听 38080 反代」形态（只放行 `/api/v1/*`，`/healthz` 仅本机可访问，登录类接口单独限流），旧机 `minibook-api` 容器已 `down`、**MySQL 容器保留作回滚副本**。迁移中修复 **MySQL 数据目录属主错误**（数据由 `ubuntu(1000)` 拷贝而来，而容器内 `mysqld` 以 `999:999` 运行 ⇒ 能读不能写、导入报 `1030/1813`、日志刷 InnoDB `error 13`）：停服 → `chown -R 999:999` → 起库，残留孤儿表空间用「整目录 `mv` 保留 + 重建库重新导入」处理。迁移后逐项对齐：`transactions` **2262**、`debt_id` 非空 **2**、`debts` **16**、`users` **3**、`max(server_seq)` **2338**、`alembic_version` **0005**、**12 张表**；四个服务端契约文件（`entity_spec` / `models` / `entity_store` / `0005` 迁移）容器内 sha256 与仓库**逐字节相同**，故无需重建镜像。过程与判据见 `docs/14_cloud_sync.md §20`。
- **公网入口上线：明文 HTTP + 端口 38080（DEC-053，2026-10-23）**：`server/docker-compose.yml` 增加 `"38080:8000"` 端口映射（保留回环映射给隧道联调）；生产关闭 `/docs`、`/redoc`、`/openapi.json`（新增 `ENABLE_DOCS` 开关，默认 `true`，服务器 `.env` 设为 `false`）；App 正式包指向该地址，`SyncUrlValidator` 只放行**内置入口的 host**。
- 外网实测：`/healthz` 200、文档三处 404、`minibook_test` 登录 + push + pull 全绿（重推幂等）；容器日志记录**真实公网 IP**（登录限流仍按真实 IP 生效）。代价：链路明文，用户知情接受。
- **同日修订（DEC-054）**：远程服务器地址**不再写进仓库** —— `app/build.gradle.kts` 的 `SYNC_BASE_URL` 改为构建时从本机 `sync.properties`（已 gitignore）注入，未配置时正式包明确提示而非静默失败；`network_security_config` 只保留默认策略、不再写死 host（平台层 host 级固定让位给应用层 `SyncUrlValidator`，见 DEC-054「已知代价」）；`docs/*.md` 里已落地的真实公网 / 内网 IP 全部替换为 `<远程公网IP>` / `<本机公网IP>` / `<内网主机IP>`。
- **服务端配置拆成两份（用户要求「一份本地开发、一份远程开发」，2026-10-23）**：`server/.env.example` → `server/.env.local.example`（SQLite + 开发密钥 + `ENABLE_DOCS=true`）与 `server/.env.remote.example`（MySQL + **只有占位符** + `ENABLE_DOCS=false`），真实值只落 `server/.env` 或服务器 `/opt/docker/minibook-api/.env`；App 侧新增 `sync.properties.example`；`.gitignore` 加固为「`server/.env*` 只放行 `*.example`」并忽略 `sync.properties`。
- **提交前体检脚本 `tools/check-secrets.ps1`（2026-10-23）**：默认查暂存区、`-All` 查全量；规则 = 公网 IPv4 端点（私有/回环/链路本地/TEST-NET/`0.0.0.0` 不算；只在 `@`、`//` 之后或 `:端口` 之前或配置行赋值位才算，故 User-Agent 与 SVG path 不误报）、DSN 真实口令、`JWT_SECRET` 真实值、私钥块、禁提交文件（`sync.properties` / `keystore.properties` / `*.jks` / `.env` / 手机备份 JSON 等）。**过程如实记录**：初版在 Windows PowerShell 5.1 下按 ANSI 读 UTF-8，乱码解出 2 处假 IP；改 `-Encoding UTF8` 后全量 **518 文件 0 命中**（脚本本身刻意只写 ASCII —— PS 5.1 会把无 BOM 的 UTF-8 脚本当 ANSI 读而直接解析失败，本轮实测踩过；中文说明留在 `docs/14_cloud_sync.md §17`）。
- **正式包真机安装（2026-10-23）**：手机装 `app-release.apk`（`CN=Minibook`；手机内 `base.apk` 与本地产物 sha256 一致 `48887B68…B67198`），**界面已无服务器地址栏**（uiautomator 全量文本无「服务器地址」）；手机自带 `curl` 直连 `http://<远程公网IP>:38080/healthz` **200**。因正式包与旧 debug 签名不同、装正式包必须先卸载，故装前先用 App「导出数据」备份 **（备份大小略）**（若干账本 / 若干分类 / **若干交易** / 若干债务；两份在手机 Download + 一份拉到本机），恢复路径 = 登录后「设置 → 数据 → 导入数据」。

### Fixed

- **换服务器后「同步成功却发到已下线旧机」的地址优先级缺陷（2026-10-29）**：`SyncAuthStore` 的 DataStore 地址（`base_url`）**优先于**安装包内置地址 `BuildConfig.SYNC_BASE_URL`，而正式包不显示地址输入框 ⇒ 换服务器只换内置地址时旧地址继续命中，同步请求一直打旧机；旧机下线后表现为「同步不上」，界面却停在上一次成功的时间。新增纯函数 `SyncBaseUrl.resolve(saved, debug, builtIn)`，在**唯一入口** `SyncAuthStore.toState()` 生效：正式包一律用内置地址（为空才回退已保存地址），调试包保持旧优先级（内网联调可改地址）。新增 `SyncBaseUrlTest` **5 例**，客户端 JVM **585 例 / 0 失败 / 1 跳过**。真机复验：新包发出 `auth/refresh` 200、`sync/pull?since_seq=2338` 200，均落在新机。
- **空计划同步不写「上次同步」时间（2026-10-29）**：本地与云端都没有要传/要收时走「弹一句『没有需要同步的内容』就结束」的分支，**从不更新 `last_sync_at`** ⇒ 「同步成功且无变化」与「同步没跑」在界面上完全一样，用户只能判定为失败。新增 `SyncUseCase.recordNoopSuccess(mode, nextCursor)`：空计划时照样推进游标（仅下载/双向）、`last_pushed_at`（仅上传/双向）与 `last_sync_at`，**不写业务数据、不落快照**；手动与自动同步入口都调用。副作用：自动同步的 5 分钟节流（`isDue` 读 `last_sync_at`）随之真正生效。
- **`_touch_device` 设备归属缺陷修复（2026-10-22，TASK-089）**：`devices.id` 就是客户端上报的 `device_id`（**主键**，同一时刻只属于一个账号），但旧实现在「设备行已存在、`user_id` 属于别人」时**又 `db.add(Device(id=device_id, …))`** ⇒ 撞主键抛 `IntegrityError` ⇒ `register()` 把它一并译成 `username_taken`（App 显示「该用户名已被占用，请换一个」，而该用户名在库里根本不存在），`login()` 走同一个函数且**没有异常兜底**，因此「同一台设备换账号登录」会直接 **500**。修法：`_touch_device()` 遇到已存在的设备行**改归属**（改 `user_id`，并刷新 `last_seen_at` / `name` / `platform` / `app_version`），不再重复 INSERT；register 侧原有的 `IntegrityError → username_taken` 保留给真正的「并发注册同名」。补 2 条回归测试（`test_register_reclaims_device_of_another_user` / `test_login_reclaims_device_of_another_user`），并**先在缺陷版代码上确认两条均失败**（`UNIQUE constraint failed: devices.id`）再确认修复后通过 —— 本地 SQLite 与腾讯云 MySQL 均 **75 passed**。已重打镜像 + `docker compose up -d` 部署，线上用「属于他人账号的 `device_id`」实测登录：旧版 500 → 新版 200 且设备行改归新账号。

- **`sync_counters` 缺行导致 push 全 500 的根因修复（2026-10-23，TASK-093 / DEC-055）**：一次「清空所有表行」把 `sync_counters` 的种子行与 `alembic_version` 一并删掉，`next_seq()` 的 `UPDATE … value+1` 影响 0 行即抛 500「server_seq 计数器缺失」⇒ **所有账号 push 全废**；而 `current_seq()` 对缺行容忍（`or 0`），表现为「pull 200 / push 500」，且该 500 由 `ApiError` 刻意返回 ⇒ 日志无异常行，极难自查。修复分两层：**数据面**按 `MAX(sync_log.server_seq)` 补种计数器 + `alembic stamp 0004_user_profile_fields`（**刻意不跑 `alembic upgrade head`** —— 版本表为空时它会从 0001 起重建已存在的表）；**代码面** `next_seq()` 缺行时自愈（`_seed_counter()` 按 `MAX(sync_log.server_seq)` 补种后重试一次，**绝不用 0** —— `sync_log` 主键是 `(user_id, server_seq)`，从 0 重发会撞已有行）。新增 `server/tests/test_counter_selfheal.py` 2 例并**在缺陷版上确认均失败**；双 DSN 各 **78 例 / 0 失败**（junitxml `failures=0 errors=0`）；重打镜像部署后容器 `healthy`、线上发号实测 `server_seq_global` 2314→2315；用户手机导入备份后同步成功（云端 `demo_user` = 若干账本 / 分类 / 交易 / 债务 / 日志）。清库边界（业务表行可删，`sync_counters` / `alembic_version` 永不删）写入 `docs/14_cloud_sync.md §18` 与 `server/README.md`。

### Added

- **账单转债务关联（F-17 / DEC-056 / TASK-094，2026-10-28）**：账单长按菜单新增「转为债务」→ 跳「记一笔债务」页并**带入金额与日期**（其余手填、**不自动保存**）；点保存时在**同一事务**里落债务并置位 `transactions.debtId`，账单行随即显示绿色「已转债务」，债务行显示「账单转入」；**统计让位** —— 月结余 / 日趋势 / 月趋势 / 分类占比（含预算「已用」）四处聚合 SQL 加 `debtId IS NULL`，交易列表与搜索**不隐藏**（否则看不到标记）；**删除该债务时同一事务把 `debtId` 清回 NULL，账单立刻重新进入统计**。关联只记在账单侧（单一事实来源），债务侧标记由「反查非空 `debtId`」派生。Room **v4→v5**：`ALTER TABLE transactions ADD COLUMN debtId TEXT`（可空、无默认值 ⇒ 存量行自动为「未转」，无需回填；`DatabaseGate` 注册 `MIGRATION_4_5`），导出 schema `5.json` 相对 `4.json` 只多这一列。同步协议新增 `debtId`，服务端四处同步改（白名单 / `models.Transaction.debt_id` / `COLUMN_MAPS` / 迁移 `0005_transaction_debt_link`），由 `SyncPayloadContractTest` + `test_column_maps_match_spec_and_models` 双向锁死；**服务端须先升级，否则带该字段的 push 会 422**。备份 JSON 增可空 `debtId`（旧备份无此键 ⇒ 未转，向后兼容）。测试：客户端 JVM **580 例 / 0 失败 / 1 跳过**（新增 `BillToDebtLinkUseCaseTest` 8 例）、服务端 pytest SQLite **78 例 / 0 失败** + 容器内腾讯云 MySQL **78 例 / 0 失败**；另加 2 份 instrumented 测试（`TransactionDebtLinkDaoTest` 统计口径、`MigrationV4To5Test` 4→5 迁移）**已在 Pixel_7（API 35）执行 7 例 / 0 失败**，并在模拟器完成 **9 步设备端逐条验收**（长按 → 转为债务 → 金额日期预填 → 保存 → 双标记 → 统计让位 → 已转项不可重复转换 → 删债务统计恢复 → 中途返回不落库）。服务端已重建镜像（回滚标签 `minibook-api:pre-debt-20261028`）并 `alembic upgrade head` 到 `0005_transaction_debt_link`，线上 `transactions.debt_id varchar(36) NULL`、2261 行零变更、外网 `/healthz` 200。
- **F-15 上云：数据库迁至腾讯云 + 后台服务「服务器打镜像 + Docker 部署」（2026-10-21）**：
  ① **库迁移**：`<内网主机IP>`（host）的 `minibook` 全量迁到腾讯云 `<远程公网IP>` 上自建 `mysql:8`（两侧同为 8.0.46 / utf8mb4 / `utf8mb4_unicode_ci` / `+08:00`，且业务表**没有任何 TIMESTAMP 列**，不存在「迁完时间偏 8 小时」的风险）。
  校验做到「逐字节」：规范化 dump 归一化两处渲染差异后**哈希相同**、`information_schema.columns` **120 列**物理元数据哈希相同、**12 张表**引擎/排序规则哈希相同、**逐账号行数哈希相同**（`demo_user` / `demo_user_b` 各 若干笔交易、`emu1012a` 1 笔，其余 0）。
  源库 10 个账号里的 4 个 `dev_*` 是历次设备端 E2E 留下的合成数据（每个 3 交易 + 1 分类 + 1 账本 + 7 日志，口令即测试硬编码值 —— 能登入即铁证），连同本轮冒烟脚本残留的 1 个 `smoke_*`，按项目原有约定（`SyncDeviceE2ETest` 注释「收口时用 SQL 清理」）一并删除 ⇒ 现库 6 账号 / 2203 交易；**真实账号分毫未动**（逐账号哈希即证据）。
  ② **容器化部署**：新增 `server/Dockerfile` + `server/docker-compose.yml` + `server/.dockerignore`，在**服务器本机** `docker compose build` 出 `minibook-api:latest`（`python:3.12-slim`、非 root uid 10001、根文件系统只读、`mem_limit 448m` / `cpus 1.0`、日志 `json-file` 20m×3），容器加入 MySQL 容器所在的 `mysql_default` 网络、用容器名 `mysql` 直连 49779，**只发布 `127.0.0.1:8000`**；口令与 `JWT_SECRET` 只落服务器 `.env`（`chmod 600`，`.gitignore` + `.dockerignore` 双重排除）。
  **`JWT_SECRET` 与迁移前保持同值 ⇒ 老设备手里的 access/refresh 令牌继续有效，不需要全员重新登录。**
  ③ **依赖修正（真 bug）**：`requirements.txt` 增加 `cryptography==50.0.2` —— MySQL 8 默认 `caching_sha2_password` 在**非 TLS 的 TCP** 上首次 full-auth 需要用服务端 RSA 公钥加密口令，PyMySQL 缺它会直接抛异常；开发机上一直没暴露，是因为服务端口令缓存已热、走的是 fast-auth。
  ④ **验证（全部真跑）**：线上容器里同一套 `pytest` **73 例 / 0 失败**（SQLite 侧同为 73 例 / 0 失败）；线上 API 冒烟全绿（注册→登录→me→push→pull→status→**令牌轮换**（旧 refresh 复用 401）→**幂等重推 no-op**（`server_seq` 2842→2842 不推进）→注销后登录 401，脚本末尾用 `delete-account` **自清理**）；模拟器 `SyncDeviceE2ETest` 经 SSH 隧道连**线上**服务与线上库跑通。
  ⑤ **公网未开放（需用户决定）**：安全组只放行 3228(SSH)，`80/443/8000` 外网实测不可达，故联调走隧道（走的是**同一个线上进程与同一个线上库**，只是入口不经公网）；要真公网直连需放行端口 + Nginx/HTTPS，并把 `app/build.gradle.kts` 里 release 的 `SYNC_BASE_URL` 从空串改成正式域名。详见 DEC-051 与 `docs/14_cloud_sync.md §14`。
- **F-16l 礼金 / 红包图标去重（用户 2026-10-13 反馈「收入的礼金和红包的图标重复了」）**：
  收入「礼金」此前与「红包」画的是同一张图形——内置模板 `BuiltInCategories.kt` 里礼金 `icon = "redpacket"`，
  历史数据里的 🧧 也映射到同一矢量；而 `CategoryIcons` 的 `Gift`（人情）与 `RedPacket` 路径都是「盒 + 蝴蝶结」，人眼几乎无法区分。修法：
  ① `CategoryIcons` 新增 `GIFT_MONEY = "giftmoney"`（Material Symbols `volunteer_activism` 手捧心矢量）与 `val GiftMoney`；
  ② `resolve(raw, name)` 增加**按名覆盖**：`name == "礼金"` 且旧解析结果是 `RedPacket`（或 `Gift`）时改渲染 `GiftMoney`
  ——**不动用户既有数据**（旧账本里名为「礼金」的分类自动换图形）；
  ③ `CategoryGlyph(icon, tint, modifier, size, name, contentDescription)` 新增 `name` 参数，9 个调用点中凡持有 `Category` 对象的都传 `name = category.name`
  （图标选择器预览**刻意不传**，保持「所见即所选」）；
  ④ 内置模板礼金 `icon` 改为 `"giftmoney"`；⑤ `pickerKeys` 24 → **25**（礼金位换手捧心，「红包」矢量保留在可选列表末尾）；
  `CategoryIconsTest` 新增 2 例（礼金与红包解析结果不同 / 名为「礼金」的旧分类换成新矢量且其它分类不受影响）。
  模拟器实测：分类页收入「礼金」显示手捧心，与「人情」的礼物盒、与可选列表里的红包图形都不再重复。
- **F-16k 首页账号入口改为「首页」标题旁的小链接（用户 2026-10-13 反馈「首页账号设置不用那么大」）**：
  撤掉首页 `LazyColumn` 首项的大卡 `HomeAccountRow`（36dp 圆底头像 + 标题 + 副行 + 行尾箭头/「登录」），
  改为 `HomeTopBar` 里「首页」标题**紧随其后的可点小号文字**：未登录显示「未登录」、已登录显示昵称（昵称缺失回退用户名，均无则「未登录」），
  `labelLarge` / `primary` 配色、`clip(Shapes.small)` + `clickable(role = Role.Button)` + `defaultMinSize(minHeight = 44.dp)`（满足 48dp 触控目标的可点区域）、
  行尾 `TextOverflow.Ellipsis`；点击仍进账号设置页。`HomeDashboardContent` 随之不再需要 `accountName` / `onOpenAccount` 参数。
  证据：模拟器 1080×2400 uiautomator dump 同一行内 `首页 [63,179][177,261]` + `未登录 [203,194][313,247]`，右侧仍是 `默认账本 [810,189][981,252]`，
  首页其余内容（结余卡、较上月环比、收入/支出、快捷入口、空态）不受影响。
- **F-16j 应用锁前置分诊（修复「未录入生物识别时开启应用锁崩溃」，2026-10-13）**：模拟器实测
  （`locksettings set-pin 1234` 设了系统 PIN、**未录入指纹/人脸**）点 设置 → 安全 → 应用锁开关 ⇒
  `FATAL EXCEPTION: main / Process: com.minibook, PID: 4040 / java.lang.RuntimeException: java.lang.reflect.InvocationTargetException`
  → `java.security.InvalidAlgorithmParameterException: At least one biometric must be enrolled to create keys requiring user authentication for every use`
  ——Android 13+ Keystore 对「每次使用都要用户认证」的 key 的硬要求；`KeyManager.createMasterKeyEncryptCipher(useAuth = true)`
  在主线程抛出且无人捕获。修法：
  ① `BiometricHelper.hasEnrolledBiometric()`（`BIOMETRIC_STRONG` / `BIOMETRIC_WEAK` 任一成功即算已录入；
  **刻意不并入 `DEVICE_CREDENTIAL`**——只设 PIN 时它同样返回 SUCCESS，正是崩溃场景）；
  ② `SettingsViewModel.setAppLockEnabled(true)` 在 `isDeviceSecure()` 之后增加该分诊，不满足则发 `needBiometric` 事件，
  **不启用、不降级明文**；③ `SettingsSecurityScreen` 新增生物识别引导卡（跳系统安全设置），与 DEC-003 无锁屏引导卡并列；
  ④ `AppLockController` 四处 cipher 创建（解锁 / 降级 / 升级 / 首启认证）全部 `runCatching`，失败按 `false` / `AUTH_FAILED`
  回调（保持锁定，宁可不进也不错）而不是崩主线程。
  模拟器复测：开关不再崩溃（`pidof` 前后同为 4728）、显示引导卡原文、返回后安全行仍「应用锁未开启」、`logcat -d -b crash` **0 行**；
  JVM 仍 **569 例 / 0 失败 / 1 跳过**（本次无新增用例）；APK 39,508,475 字节 sha256 `C334F7F480F3BD5B4F3D55FA7F67132D162AC81A34C51F9586A4B894DB60DA9D`。
- **F-16i 解密门门控 Flow（修复「重锁瞬间主线程崩溃」，2026-10-13）**：真机 crash buffer 实录
  `java.lang.IllegalStateException: DatabaseGate 未解锁/未打开，无法提供 LedgerDao`（10:11:13，pid 27954）——
  `AppLockController.lock()`（`AppLockController.kt:72-73`）**先 `databaseGate.close()`、后 `_isLocked.value = true`**，
  而 UI 生命周期恢复会让 `collectAsStateWithLifecycle` 立刻重启上游 Flow；旧实现在**调用点** `ledgerDao() ?: error(...)`
  同步抛出 ⇒ `Dispatchers.Main.immediate` 上无人捕获，主线程崩溃。修法：
  ① `DatabaseProvider.openState: Flow<MiniBookDatabase?>`（`DatabaseGate` 用 `MutableStateFlow` 覆写：`open()` 成功发射、失败与 `close()` 归 `null`）；
  ② 新增 `data/local/datasource/GatedFlow.kt`：`gatedFlow { db -> ... }` = `openState.flatMapLatest { if (it == null) emptyFlow() else block(it) }`
  ——**必须把 `null` 换成空流**；写成 `filterNotNull().flatMapLatest(...)` 会让重锁事件到不了 `flatMapLatest`、旧库流留在已关闭实例上（回归测试抓到的点）；
  ③ `RoomDataSourceImpl` 七个 `observe*`（交易四流 / 债务 / 分类 / 预算）+ `RoomDataSource` 适配器 `observeAll` 全部门控：
  解密门关闭期间**不发射、不抛异常**，解锁自动切到新建库的 DAO；
  ④ **一次性 `suspend` 查询/写入仍保持硬失败**（静默返空会把「没解锁」误报成「没有数据」，宁可不记错）；后台接收器（`SmsReceivedReceiver`、自动同步）本就 catch。
  新增 `GatedFlowTest` 3 例（门关不发射 / 解锁发射当前库值 / 重锁取消上游再解锁切新库）；JVM **569 例 / 0 失败 / 1 跳过**；APK 39,196,755 字节 sha256 `B297835C81F0513D9B6DB38392A960BF4064C893B1842171944AFAC739F2AA71`。
- **F-16h 内置商户词典（docs/15 第 7 条，2026-10-13）**：按用户定案「联网搜一波大的当内置，学习留在 App 本地」——
  新建 `app/src/main/res/raw/merchant_dictionary.json`（**1315 条**，70,105 字节，`{version,note,entries:[{keyword,category}]}`，
  只映射 15 个内置支出分类名，0 重复 keyword）；`domain/usecase/category/MerchantDictionary.kt`（接口）+
  `BuiltInMerchantDictionary.kt`（安装后按归一化长度降序，先相等后包含，复用 `CategoryRuleNormalizer`）+
  `data/local/dictionary/MerchantDictionaryJson.kt`（容错解析，坏文件静默降级为空）+ `MerchantDictionaryStore.kt`
  （`@Singleton`，双检只加载一次，失败静默）+ Hilt `@Binds`。`ImportBillUseCase` 在**第 1.5 步**查词典
  （仅 `allowWeakRuleMatch = true` 的手动预览/待确认路径；L2 到达即记账**不查词典**，防静默污染），
  优先级 = 本地学习规则 > 内置词典 > 内置类型映射 > 「其他」。体量：APK 内资源不解压，实测 APK 反而略小
  （38,788,240 字节）；测试 `MerchantDictionaryTest`（解析 / 相等优先 / 最长包含 / 未加载返回 null / 清空）+
  词典文件校验（≥1000 条、分类合法、无重复、归一化后非空）。
- **F-16g 日期区间联动约束（docs/15 第 5 条）**：`ui/components/OptionalDateField.kt` 与
  `ui/transactions/TransactionsScreen.kt` 的 `DateFilterChip` 新增 `minDateMillis` / `maxDateMillis`，
  经 M3 `SelectableDates` 在日期选择器内**把不可选日期直接置灰**，取代「先选完再报错」；两处调用点
  （交易列表筛选、搜索条件）互相传递另一端的值 ⇒ 「结束日期」不能早于「起始日期」，反之亦然。
- **F-16f 退款记为「收入」+ 单独「退款」分类（docs/15 第 6 条）**：根因是 `SmsBillParser` 的
  `EXPENSE_KEYWORDS` 含「退款」⇒ 退款短信被记成支出。改为「退款」入 `INCOME_KEYWORDS`，
  新增 `REFUND_KEYWORD` 在方向判定 `when` 里**优先**判 `TransactionType.INCOME`，并令
  `platformCategory = "退款"`；`domain/model/BillCategoryMapping.kt` 补短信侧「退款」→ 收入「退款」分类
  （微信侧原有两条退款条目保留）。按用户拍板**不做支付/退款配对、不红冲、不动数据模型**，
  退款在收入分析里单列。测试含「消费…已退款到账」等 3 例。
- **F-16e 贷款/授信营销短信过滤（docs/15 第 4 条）**：用户给的两条真实银行原文（398000 元授信）原被判成
  收入/支出各一笔并**静默入库**。① 根因：「回6查利息」命中 `INCOME_KEYWORDS` 的「利息」、「可用于日常消费」
  命中 `EXPENSE_KEYWORDS` 的「消费」，两侧都命中时按**更靠前**者定向，且模板注册表见「银行」锚点即套
  `bank.generic`（允许 L2 自动记账）。② 修复：在方向判定**之前**加三层预筛——回复指令特征
  （`拒收请回复`/`退订`/`回+数字+查办确退了复`）→ `NotBill`；授信语义（`授予您`/`特批您`/`预授信`/`可借`/
  `周转款`/`额度`…）且**无强交易锚点** → `NotBill`；并把「利息」「消费」从单独定向降级为需交易锚点语境。
  **不遗漏优先**：带「尾号+扣款」的真实交易短信即使带「退订回T」也照常入账（有专门用例守住）。
  已入账的脏数据按用户 2026-10-13 决定**不清理**。
- **F-16d 导入前主动申请短信权限（docs/15 第 3 条）**：`ui/billimport/ImportScreen.kt` 新增
  `smsPermissions()`（`READ_SMS` + `RECEIVE_SMS`，Android 13+ 加 `POST_NOTIFICATIONS`）与
  `rememberLauncherForActivityResult(RequestMultiplePermissions())`；两个短信导入入口改走
  `startSmsScan(mode)`（未授权 → 先申请，授权后按**同一 mode** 继续扫，避免权限弹窗把用户意图吞掉）；
  引导页按钮文案改「授予权限并继续」并补系统设置兜底路径说明。
- **F-16c 导入来源清单（docs/15 第 2 条）**：用户「多家银行只导入指定的一家」；因 106 端口复用、发送方
  无法预枚举，改为**从扫描结果反推**：`SmsModels` 增 `SmsWhitelistEntry.importEnabled`、`SmsSenderSummary`
  与 `SmsScanPreview.senders`；`SmsWhitelistStoreCodec` 改 5 段（旧 4 段自动按 `importEnabled = true` 兼容，
  老用户不迁移）；`SmsWhitelistStore` 增 `setImportEnabled` / `rememberSenders`；新增
  `SmsSenderNamer`（按机构词表 + 正文签名猜机构名，猜不出返回 null 绝不硬写）与 `SmsSenderSummaryBuilder`
  （账单数降序、同数按**号码数值**升序）。`scan()` 跳过未勾选来源并计入「非账单」（**不删任何已入账交易**），
  只登记真正出过账单的来源；UI：导入页「导入来源」摘要行 + 弹框逐源勾选（**默认全选**，防漏），
  设置 → 短信账单 → 白名单每源两行开关（上一行自动记账 / 下一行允许导入）。
- **F-16b 分类规则归一化核心词（docs/15 第 1 条）**：新建
  `domain/usecase/category/CategoryRuleNormalizer.kt`——归一化四步（剥括号 → 全角转半角、去空白、小写 →
  按**长词在前**剥后缀表（有限责任公司/股份有限公司/有限公司/分公司/旗舰店/营业部/门店/分店/公司/集团…，
  **刻意不剥裸「店/市/区/县/中心/广场」**，否则「如家酒店」→「如家酒」、「天猫超市」→「天猫超」）→ 去首尾标点）；
  匹配强度 `STRONG`（原串包含 / 归一化相等 / 核心词 ≥3 字被包含）与 `WEAK`（公共前缀 ≥2 字）。
  学习侧 `UpsertUserCategoryRuleUseCase` 改存**核心词**（旧规则不迁移，原串包含仍是 STRONG，行为不退化）；
  `ImportBillUseCase` 用 `matchStrength` 排序（STRONG 优先 → 核心词更长优先 → 更新时间新优先），
  新增 `allowWeakRuleMatch` 参数：**L2 到达即记账只认 STRONG**，只有用户看得见、改得动的手动预览与
  「待确认」路径（`SmsBillImportUseCase.importConfirmed` / `confirmManual`）才允许 WEAK 参与。
  算法与应用口径落在新文档 `docs/19_category_rule_normalization.md`（用户要求「找个地方单独记录以便后续优化」）。
- **F-16a 页面顶部留白修复 + 首页账号信息行（用户即时需求，2026-10-13）**：按用户反馈「页面与上部空白太多」
  「在首页可以增加一个登录信息展示」——① 根因：`ui/main/MainScreen.kt` 的 `Scaffold` 没有 `topBar`，默认
  `contentWindowInsets = systemBars` 把状态栏高度计入 `innerPadding`，而 5 个 Tab 页各自的 `Scaffold` **又加一次**
  ⇒ 内容被顶下约两个状态栏高度；改为 `contentWindowInsets = WindowInsets(0, 0, 0, 0)`（底部不受影响，
  `NavigationBar` 自身消费导航栏 inset）。② 首页 `LazyColumn` 首个 item 新增 `HomeAccountRow`：
  未登录显示「未登录」+ 右侧「登录」链接，已登录显示昵称 +「账号设置 ›」，整行可点跳转账号页
  （`HomeNavigation.openAccount()` → 根图 `settings-account`）。昵称来自服务端缓存副本：
  `SyncAuthStore` 增 `KEY_NICKNAME` / `updateNickname()` / `displayName`（昵称优先、回退用户名、未登录为 null），
  `AccountViewModel` 在 `GET /me` 与保存资料成功后回写，`HomeViewModel.accountDisplay` 暴露给首页。
  测试：`SyncAuthStoreTest` +2 例（昵称优先回退用户名、登出清缓存）。
- **F-15y 账号页「显示名」改称「昵称」+ 不再展示账号 ID（2026-10-13）**：按用户反馈「显示名改一下，叫昵称」
  「账号ID不要展示」——账号页用户信息行与编辑资料页字段的「显示名」全部改称「**昵称**」（编辑资料页说明行、账号页
  「编辑资料」行副标题、`AccountViewModel` KDoc 同步；登录页辅助说明改为「登录用的账号名（不是昵称）」以避开混淆）；
  已登录用户信息由 8 项收为 **7 项**（昵称 / 用户名 / 邮箱 / 个性签名 / 地区 / 注册时间 / 上次登录），
  `SettingsAccountScreen.kt` 删掉「账号 ID」行——`UiState.userId` 保留（内部仍需要），只是不再展示。
- **F-15x 预算：总额可删除 / 清空 + 分类预算删除后立即刷新（2026-10-13）**：按用户反馈「预算填写了不能删除或者清空」
  「分类预算删除后不立马刷新，返回再进来才会删除」——
  ① 新增 `domain/usecase/budget/ClearTotalBudgetUseCase.kt` + `BudgetsViewModel.clearTotalBudget()`；预算页总额弹层在
  **已设过总额**时给出「删除月度总预算」danger 入口（`onDelete` 由恒为 `null` 改为按 `successProgress.totalBudgetFen > 0` 提供），
  `BudgetEditorSheet` 的删除按钮/强确认标题与文案按模式（分类 / 总额）切换，总额模式注明「已记录的交易与分类预算不受影响」。
  ② **根因修复**：分类预算删除原先走 `RoomDataSourceImpl.deleteByCategory` 的裸 `writableDatabase().delete("budgets", …)`，
  **绕过了 Room 的失效通知**（DAO 写入会在进程内刷新失效版本；DB 级 TEMP 触发器只在建触发器的连接上生效）⇒
  `observeByMonth` 的 Flow 不重发 ⇒ 列表不刷新，必须退出页面等 `WhileSubscribed(5s)` 超时重连才更新——这也解释了
  为何「保存」会刷新（走 DAO `upsert`）而「删除」不会。改为新增 `BudgetDao.deleteCategoryBudget()`（`@Query` DAO 删除，
  写入与失效通知同一路径闭合），删完列表**立即**刷新。测试：`FakeBudgetRepository` 记录 `lastClearedTotal` /
  `lastClearedCategory` 供断言，新增 `ClearTotalBudgetUseCaseTest` 1 例 + `ClearCategoryBudgetUseCaseTest` 2 例。
- **F-15w 账本图标换成自带矢量图标集（2026-10-13）**：按用户反馈「账本的图标也换一下」——新建
  `ui/components/LedgerIcons.kt`：16 个**稳定 key**（`book` / `notebook` / `journal` / `stories` / `library` / `notes` /
  `receipt` / `stack` / `work` / `savings` / `bank` / `card` / `chart` / `shopping` / `home` / `car`，路径取自
  Material Symbols Rounded，Iconify 抓取后内联为自带矢量，`addPathNodes` 解析）；渲染统一走新增的 `LedgerGlyph`
  （`ui/components/LedgerIcons.kt`）：key / 历史 emoji → 按 `tint` 着色的矢量，未知串 → 文本兜底，**绝不空白**。
  渲染点全部收口（账本选择器 `Pickers.kt`、账本管理 `LedgerManageScreen.kt`、锁屏 LOGO `LockComponents.kt`）；
  账本编辑器 `LedgerEditorSheet.kt` 的「emoji 快捷行 + 自定义 Emoji 输入框」换成 **6 列图标网格**
  （`LedgerIconPickerGrid` / `LedgerIconChoice`：44dp 圆、选中主题描边 + `primaryContainer` 底色），新建账本预选 `book`；
  默认账本 `BootstrapUseCase` 与导入回退 `ImportAllUseCase` 的 `DEFAULT_ICON` 由 `📒` 改为 `"book"`
  （domain 层写字面量以免依赖 ui，注释指向 `LedgerIcons.BOOK`）。**不做数据迁移**：老账本 emoji 原样留在 DB 里，
  由 `LEGACY_EMOJI`（16 条）兜底映射回同批矢量，用户不重新点选就不会改写。守卫：`LedgerIconsTest` 3 例在 JVM 侧
  逐个解析全部矢量路径（F-15o 教训：编译绿也可能运行时崩）。
- **F-15v 内置分类图标换成自带矢量图标集（TASK-082，2026-10-12）**：24 条内置分类模板的 `icon` 从 emoji
  换成 `ui/components/CategoryIcons.kt` 的**稳定 key**（`"food"` … `"cash"`，路径取自 Material Symbols Rounded，
  Iconify 抓取后内联为自带矢量）——渲染统一走新增的 `CategoryGlyph`（`ui/components/CategoryComponents.kt`）：
  key → 矢量图标、可按分类色 `tint` 着色；未知串 / 历史 emoji → 文本兜底（`LEGACY_EMOJI` 把 24 个旧 emoji
  映射回同一批矢量，**不做数据迁移**，老数据原样保留）。9 处渲染点全部改走 `CategoryGlyph`（分类格、交易列表行、
  记账分类下拉、交易详情、预算进度卡、预算编辑、搜索筛选、搜索条件）；分类编辑器的「手输 Emoji 输入框」换成
  **6×4 图标网格**（44dp 圆、选中主题描边 + `primaryContainer` 底色，不再提供手输 emoji）。守卫：
  `CategoryIconsTest` 3 例在 JVM 侧逐个解析全部路径（F-15o 教训）。JVM 521 例 / 0 失败。
- **F-15u 未登录态收为登录按钮 + 设置页「账号」提第一行（TASK-082，2026-10-12）**：按用户反馈（2026-10-12）
  「用户未登录时候不要展示一个大框，做成一个让用户登录的按钮即可」——撤销上一版 `GuestLoginCard` 资料头卡
  （见被否决的 F-15t），未登录的账号页只留满宽「登录」主按钮 + 「还没有账号？注册新账号」文字链
  （`GuestLoginButtons`）；同时按「账号设置提到设置页面第一行」把设置页「账号」行移到**第一位**（在「账本」
  之前），作为身份 / 跨设备入口最可能的行动点。
- **~~F-15t 账号页「未登录」改成资料头卡（TASK-082，2026-10-12；已被用户否决，见 F-15u）~~**：用户拿主流 App 的未登录态对照后要求「这块的模式」——
  未登录时不再是一行「当前状态 + 未登录」加两个竖排按钮，而是一张**资料头卡**：圆形头像（`MiniBookIcons.Person`）+
  「未登录」标题 + 一句「登录云同步个人账号，本地账目一条不动」，主操作是满宽「**立即登录**」（按钮内带人形图标，
  52dp 高），次操作「注册新账号」为描边按钮（48dp）。**只借结构、不借内容**：不写会员 / 权益 / 签到 / 领现金这类
  我们真没有的东西。已登录态不动（仍是用户信息 8 项 + 账号操作 + 会话三段）。见 `SettingsAccountScreen.kt`
  （`GuestLoginCard`）。实测：模拟器浅色 / 暗色各一张截图（`build-verify-account-guest-light.png` /
  `-guest-dark.png`），点「立即登录」正常进独立登录页；JVM 518 例 / 0 失败不变。
- **F-15s 隐私政策与用户协议落地 + 注册页协议勾选（TASK-076 部分完成，2026-10-12）**：两份法务正文写成
  **纯文本单一副本** `app/src/main/res/raw/privacy_policy.txt`（11 节）与 `terms_of_service.txt`（10 节）——界面直接读，
  上架材料与 App 内展示同源（`LegalTextTest` 3 例守着关键条款与"无 Markdown 记号"）；新增
  `ui/settings/LegalScreen.kt`（枚举 + 滚动正文页）与设置 →「关于」两行入口，路径为**设置 → 关于 → 隐私政策 / 用户协议**
  （2 跳可达）；注册页新增**默认不勾选**的协议勾选框（未勾选时本地拦下并直说原因，`SyncPresenter.agreementError`），
  登录页只放「登录即表示你已阅读并同意」+ 一对可点链接——登录页不放勾选框（满足 191 号文一.2 / 三.2 / 三.4）。
  **运营主体与联系方式按用户要求暂不声明**，正文与 `docs/17` §5 都写明"上线前必须补"。方案与合规对照见 `docs/17`
  `docs/18`。
- **F-15r 首页结余卡新增"较上月"环比（TASK-081，2026-10-12）**：结余金额下方一行——「较上月 +¥X / 较上月 ¥-X /
  与上月持平 / 上月无记录」。**只给可核对的绝对差额，不给百分比**（上月基准为 0 时百分比无定义，硬算等于编造）；
  上月收支全为 0 视为"上月无记录"，不显示对比；涨跌用**符号 + 语义色双编码**（`NetBalanceTrend` + `NetTrendLine`
  在 `ui/components/Cards.kt`，判定纯函数 `HomeViewModel.previousBalanceOrNull` 可单测）。
- **F-15q 视觉现代化：主题 token + 自带矢量图标 + 面板分层（TASK-078 / TASK-079，2026-10-12）**：
  按平台规范与 2025 视觉趋势做了一轮"不换依赖、不改架构"的外观改造（方案与出处见
  `16_auth_ux_and_visual_plan.md` §6）：
  1. **配色与分层**：`ui/theme/Color.kt` 从 2021 默认紫换成青绿「清爽现代」基线，并**补齐 M3 分层表面族**
     （`surfaceContainerLowest/Low/Container/High/Highest`、`surfaceDim/Bright`、`inverse*`）——此前全仓库
     `surfaceContainer*` **0 命中**，层级只能靠 `surfaceVariant + alpha` 硬凑；
  2. **对比度**：`Theme.kt` 的主题色生成改为按背景亮度算 `on*` 色（`onColorFor`），不再无条件
     `onPrimary = Color.White`（浅色强调色底上的白字曾经过不了 WCAG 1.4.3）；
  3. **形状/字号**：`MiniBookShapes` 改为 6/12/16/**20**/28dp、`FullShape` 28dp；新增等宽数字金额样式
     `MoneyLarge` / `MoneyMedium`（`tnum`，28sp/20sp SemiBold），净额卡不再用 36sp Regular；
  4. **图标全部自带矢量**：新增 `ui/components/MiniBookIcons.kt`（18 个 24dp 图标，路径取自 Material
     Icons，Apache-2.0），替换底部导航 5 个 emoji、搜索 🔍、账本行 ✏️/🗑、返回 ←、月切换 ‹›、
     FAB ＋、快捷入口 emoji 等；配套 `MiniBookIconsTest` 遍历全部图标断言可解析
     （矢量路径写错只在运行时崩，参见 F-15o）；
  5. **面板分层**：`surfaceVariant.copy(alpha = …)` 的 8 处面板与 3 处显式 `surface` 卡片改
     `surfaceContainerLow / High / Highest`；筛选 Chip、进度轨道等**语义用色刻意保留** `surfaceVariant`。
  语义色（收入绿 / 支出红）**不随动态取色漂移**；`dynamicColor = true` 仍旧默认（壁纸取色下强调色来自
  系统，静态青绿基线用于 API<31、关闭动态取色与自定义主题色）。
- **F-15p 登录 / 注册页按平台规范补齐交互细节（TASK-075，2026-10-12）**：①口令可见性从"多余的按钮"改成
  输入框**右侧眼睛图标**（NIST SP 800-63B 允许显示口令、Material 的 `password_toggle` 惯例）；②口令 / 用户名
  字段带上 **autofill 语义**（`ContentType.Username / Password / NewUsername / NewPassword`），系统密码管理器
  可自动填充；③键盘：`KeyboardCapitalization.None` + 关闭自动纠错，`imeAction` 串成
  「下一步 → 完成」（最后一项回车即提交）；④错误**落到字段上**（`isError` + `supportingText`），并用
  `LiveRegion` 让读屏播报；⑤注册页「两次口令不一致」精确挂到"确认口令"那一格（不再只在页面底部出现一句）；
  ⑥提交中禁用全部输入；⑦返回键由文本「← 返回」改为矢量箭头并带 `contentDescription`。
- **F-15o 修复「点注册新账号」闪退（TASK-074，2026-10-12）**：新的登录 / 注册页给应用图标用了
  `painterResource(R.mipmap.ic_launcher)`，而 `mipmap-anydpi-v26/ic_launcher.xml` 是**自适应图标 XML**，
  Compose 的 `painterResource` 只认 VectorDrawable 与位图 ⇒ 抛
  `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported ex. PNG, JPG, WEBP`
  并当场结束 Activity（用户点「注册新账号」就是闪退）。**这类缺陷编译绿、JVM 单测也全绿都抓不到**：
  本次是模拟器实测 + `adb logcat -b crash` 定位的，栈首帧 `AuthComponents.kt:68`。已改用矢量
  `R.drawable.ic_launcher_foreground`，外套圆角底衬（`Box(76.dp).clip(RoundedCornerShape(22.dp))` +
  `Image(56.dp)`）。同类隐患：`SmsNotificationHelper`（通知小图标）仍引用 `R.mipmap.ic_launcher`，未改。
- **F-15n 「注册时间」显示错误修复 + 资料字段扩充（TASK-074，2026-10-12）**：服务端 `UTCDateTime` 读出来是
  **naive UTC**，`UserOut.created_at` 序列化后不带时区 ⇒ 客户端 `Instant.parse` 抛异常、把 UTC 原样显示
  （比本地时间早 8 小时且难看）。现在服务端统一输出**带 `Z` 的 UTC**（`UserOut` 的序列化器），客户端
  `humanTime` 再兜一层「没带时区按 UTC 解释」，并新增 `fullTime`（`yyyy-MM-dd HH:mm`）供「注册时间 /
  上次登录」使用。资料同时扩充：`users.email`(254) / `bio`(80) / `region`(32)（迁移
  `0004_user_profile_fields`）；账号页展示 8 项（显示名 / 用户名 / 邮箱 / 个性签名 / 地区 / 注册时间 /
  上次登录 / 账号 ID），其中「上次登录」= 该账号最近一次**首发**刷新令牌时间（令牌轮换不算新登录）。
  新增「编辑资料」独立页，保存时**只提交改动过的字段**（null = 不改，空串 = 清除）⇒ 改邮箱不会顺手
  清掉个性签名。
- **F-15m 登录 / 注册各自独立成页（TASK-074，2026-10-12）**：原先挤在账号页里的内嵌表单改成两个独立整页
  （路由 `settings-login` / `settings-register`）：应用图标 + 大标题 + 满宽主按钮 + **页面内错误文案**
  （不再是一闪而过的 toast）+ 互相切换的文字按钮；注册页带「确认口令」，注册成功即自动登录。新增
  `AuthViewModel` 专管认证，`AccountViewModel` 只管资料与会话；服务器地址输入框只在调试版的登录页出现。
- **F-15l 云同步入口去重（TASK-074，2026-10-12）**：用户指出「账号页有云同步、数据页也有」——按「只保留
  一个」去掉账号页那一行，入口只留在**设置 → 数据 → 云同步**（账号页职责收敛为「账号自己」）。
- **F-15k 修复「注册失败」提示不准（2026-10-12）**：服务端 `RegisterRequest` 要求 `password ≥ 8`、
  `username` 3–32 位且限 `[A-Za-z0-9_.-]`，但客户端原先不做本地校验，服务端 422 又被复用成同步用的
  「数据校验失败，未写入云端」——真机上用户用 6 位口令注册，只看到这句话，完全不知道是口令长度问题。
  现在：① 注册 / 登录**本地前置校验**，不合规直接说「口令至少 8 位」/「用户名不合规：…」且**不发请求**；
  ② 表单常驻规则提示（用户名 3–32 位字母/数字/点/下划线/连字符；口令至少 8 位）；
  ③ 认证类 422 文案改为「输入不符合要求：用户名 3–32 位…，口令至少 8 位」。
  纯逻辑新增 4 例测试（`SyncPresenterTest`）。
- **F-15j 修复 `SyncApi` 在主线程读响应体（`NetworkOnMainThreadException`，2026-10-12）**：
  `callRaw` 把 `resp.body?.string()` 放在 `withContext(ioDispatcher)` **之外**——小响应侥幸已进缓冲区，
  大响应就会在**调用方线程**真读 socket ⇒ 账号页点「登录」报「操作失败：NetworkOnMainThreadException」。
  自动同步从未暴露它（`SyncUseCase` 整段已在 IO 上），属典型「JVM 绿、只在真机特定路径炸」。现已把
  `execute()` 与读 body 一起收进 `withContext(ioDispatcher)`；调试版账号操作失败时打印异常栈便于定位。
- **F-15h 「账号」页：登录 / 改资料 / 改口令 / 退出 / 注销（DEC-039 / TASK-073，2026-10-12）**：
  设置首页「账号」点进去的二级页标题改为「**账号**」（与入口同名；原先错走到「云同步」页所以标题对不上）；
  新增 `settings-account` 路由 + `AccountViewModel` + `SettingsAccountScreen`，并新增「用户信息」区
  （显示名 / 用户名 / 注册时间）。四类操作：改显示名（**留空 = 清除**，回退显示用户名）、改口令（原口令验证，
  成功后服务端撤销全部旧刷新令牌并换发新令牌，客户端立即覆盖本地）、退出登录、注销账号（口令二次确认）。
  **注销与退出都只动云端账号，本机数据一条不删**。服务端新增 `users.nickname`（迁移 `0003_user_nickname`）、
  `PATCH /api/v1/me`、`POST /api/v1/auth/delete-account`（删云端 6 张业务表 + `sync_log` + 刷新令牌 + 设备 + 用户行）。
- **F-15i 服务地址不再暴露给用户（DEC-039 / TASK-073，2026-10-12）**：地址改为 `BuildConfig.SYNC_BASE_URL`
  按构建类型内置（debug = `http://127.0.0.1:8000`；release 留空 = 待填部署域名）；正式版界面**不再出现
  服务器地址**，仅调试版保留输入框供开发临时改指向；内置地址缺失时明确提示「本安装包未内置服务器地址」。
- **F-15f App 账号登录可用 + 自动云同步（DEC-038 / TASK-071，2026-10-12）**：
  按用户纠正的方向落地（原话「**我要的是 app 登录，不是做一个云同步的功能。是 app 能登录，在设置里可以自动云同步或者手动同步**」）：
  设置首页新增一级「**账号**」行（未登录／已登录两态）直达账号与同步页；账号页新增**自动同步**开关（默认开，
  打开／回到 App 时静默**双向**同步一次，**≥5 分钟节流**）。`AutoSyncManager` 由 `MainActivity.onStart()`
  + 解锁后组合的 `LaunchedEffect` 触发（经 `EntryPoint` **延迟取用**，避免解锁前构造 `SyncStateDao` 导致启动即崩）。
  **安全边界**：会删本地数据或有冲突的计划（`SyncPlan.needsConfirm`）**永不自动执行**，失败静默且不推进游标；手动三模式全部保留。
  登录失败改为**可见**：`saveSession` 落盘后**读回验证**，Keystore 失败**重试一次**并抛出可读原因
  （治「服务端 200 但本地没登录、界面毫无提示」）。
  **验证（实跑）**：JVM **489 例 / 0 失败**、模拟器 **21 例 / 0 失败 / 0 跳过**（新增 `AutoSyncManagerTest`、
  `SyncAuthStoreDeviceTest`（真 Keystore 往返 + 会话落盘读回）、`MainActivityStartupTest` 启动冒烟）；
  模拟器实测：登录 → `am force-stop` 后重启仍为「已登录：demo_user_b」→ 回前台自动同步
  （`GET /sync/pull` + 6×`POST /sync/push` 全 200）→ 云端 `ledgers 1 / categories 25 / transactions 若干 / debts 8 /
  sync_log 若干`，与用户手机导出的备份**逐项一致**。
- **F-15g 修复「导入后首页 ¥0.00，看起来像导入失败」（TASK-072，2026-10-12）**：
  导入会重建账本并重新生成 id，`LedgerDataStore` 的 `current_ledger_id` 随即**悬空** ⇒ 首页/明细显示
  「¥0.00 · 暂无交易记录」，而数据其实已全部落库（同一份数据随后能把 若干条推上云）。现改为在**事务成功后**
  用 `ledgerRepository.getDefault()?.id` 把当前账本写回（刻意置于事务外：回滚时指针不动，绝不指向不存在的账本）。
  实测 `pm clear` 后重新导入同一备份，首页**直接**显示 `2026年10月结余 ¥-2,474.02`（修复前为 `¥0.00`）。

- **F-15 云端存储改版：JSON 通用表 → 6 张真实业务表 + `sync_log`（DEC-037，2026-10-10）**：
  用户验收云端库时否决「业务字段塞进 `payload` JSON」的通用记录表（原话「不要这样，要在云端做明确的表，设计表结构」），
  改为按实体建 6 张类型化业务表（`ledgers`/`categories`/`transactions`/`debts`/`budgets`/`user_category_rules`，
  PK `(user_id,id)`，金额 `BIGINT` 存「分」、`date` VARCHAR(10)、`month` VARCHAR(7)，按查询形状建索引）
  + `sync_log` 变更日志（**拉取游标的唯一来源**）；墓碑 = 业务表**物理删行** + 日志 `deleted=1`（云端查账不被墓碑污染）；
  `created_at_raw`/`updated_at_raw` 保留客户端**原始 ISO 串**（逐字回传），`updated_at` 另存归一化 `DATETIME(6)` 供 LWW。
  **写协议零改动 ⇒ Android 一行未改**（`payload` 形状/键名与 DEC-036 语义不变），服务端用
  `entity_store.shred()/reassemble()` 做 `payload ⇄ 列` 双向搬运。
  迁移 `0002_typed_entities`：建表 → 拆列回填 → 删旧表（脏行只留日志 + 告警，**不中断迁移**），`downgrade` 可反向拼回 payload。
  **验证（全部真跑）**：双 DSN `pytest` 各 **61 例 / 0 失败**（新增 `test_typed_storage.py` 6 例 + `test_migration.py` 回填/降级往返）；
  真实开发库 `minibook` 上 `0001_init ⇄ 0002_typed_entities` 往返，5 行旧数据全部拆列、**0 行跳过**；
  云端 SQL 聚合直接可用（`SELECT date, SUM(amount) FROM transactions GROUP BY date` → 88.88 元）；
  JVM `SyncApiE2ETest` 与模拟器 `SyncDeviceE2ETest` 改版后复跑通过。
  修复踩坑：客户端 `explicitNulls=false` ⇒ 可空字段**整个缺键**，拆列必须 `.get()`（用下标会把真实历史行判成脏数据跳过）。
- **F-15 真 MySQL 8 落地验证（2026-10-10，TASK-069 收口，用户提供内网 MySQL 连接信息）**：
  在内网 MySQL **8.0.46**（docker `mysql:8.0`，`<内网主机IP>:49779`，库 `minibook` / 测试库 `minibook_test`）上真连库跑通：
  `alembic upgrade head` 建 5 张业务表 + `alembic_version`（全 `InnoDB` + **`utf8mb4_unicode_ci`** + `DATETIME(6)` + `JSON`）；
  `downgrade base ⇄ upgrade head` 双向真跑通过；**同一套 `pytest` 52 例 / 0 失败，MySQL 与 SQLite 双 DSN 双绿**
  （52 = 原 49 + **TASK-064 新增 3 例「日志不得含 payload」隐私断言**：push / pull / 整批校验失败三条路径，
  断言日志里查不到备注与金额哨兵、以及 `"payload"` / `"description"` 字段名）；
  uvicorn 真进程端到端冒烟（注册 201 → 登录 → push `applied=1` → pull 取回 → status 计数）通过，
  且中文经 `HTTP API → MySQL → 拉回` **逐字一致**（23 字符 / 44 UTF-8 字节）。`server/.env` 落本机 DSN（已被 `.gitignore` 忽略）。
  - 本轮实测暴露并修复 5 个真缺陷：① `alembic/env.py` 把 URL 回写进 configparser → 口令 URL 编码含 `%` 时
    `ValueError: invalid interpolation syntax`，`alembic upgrade` 直接失败（改为只读取、不回写）；
    ② MySQL 8 对 `CHARSET=utf8mb4` 默认给 `utf8mb4_0900_ai_ci`，与部署口径不符（显式 `COLLATE utf8mb4_unicode_ci`）；
    ③ `downgrade()` 先 `DROP INDEX` 撞外键支撑索引（`errno 1553`）且中途失败留下半删库（改为只 `DROP TABLE`，先子表后父表）；
    ④ Android `hasTokens` 只看密文非空 → Keystore key 失效时误报「已登录」（改为必须真的解得开）；
    ⑤ `://x` / 越界端口等畸形服务器地址漏过校验（补主机名+端口白名单）。
    另修 health 测试把 `db == "sqlite"` 写死（改为与真实引擎比对）。
- **F-15 Android 云端网络栈（TASK-065 完成，2026-10-10）**：新增唯一三方依赖 **OkHttp 4.12.0**，
  `data/remote/{SyncDtos,SyncErrors,SyncUrlValidator,SyncApi,SyncRemote,SyncAuthStore,SyncTokenAuthenticator}.kt`
  + `data/local/security/SecretCipher.kt`（Keystore 别名 `minibook_sync_token`，**不动**开库路径的 `KeyManager`）
  + `di/SyncModule.kt`；debug 清单单独开 `usesCleartextTraffic`（release 仍禁明文）。
  401 → 刷新一次 → 重试；网络故障**不清**账号态、令牌撤销才清。**JVM 单测 30 例 / 0 失败**
  （DTO 口径 7 + 错误码 5 + 地址校验 6 + AuthStore 6 + 令牌编排 6；含「落盘字节里没有明文令牌」断言）。

- **F-15 Android 同步 UI（TASK-067 完成，2026-10-10）**：设置 → 数据 → **云同步**二级页落地
  （`ui/settings/{SettingsCloudScreen,SettingsCloudViewModel,SyncPresenter}.kt`，路由 `settings-cloud`，
  数据页新增入口行）。账号区（服务器地址 / 用户名 / 口令，登录 / **注册后自动登录** / 退出时先尽力撤销刷新令牌）、
  三模式按钮（双向同步 / 仅上传 / 仅下载）+ **预览确认卡** + 结果卡（成功给四计数，失败明说「游标未推进」）、
  状态行（上次同步 / 待处理冲突数，**只有计数与时间，不含账目明细**）、冲突留档查看（类型 / 短 id / 处置口径 /
  **两份副本原文**，可手工恢复），并明示上云数据边界（DEC-034）与同步前自动备份。
  业务判定与全部文案抽到 `SyncPresenter` 纯函数层，Composable 不含规则。**`SyncPresenterTest` 21 例 / 0 失败**；
  **全量 JVM 回归 481 例 / 0 失败**。
- **F-15 服务端日志隐私断言（TASK-064 收口，2026-10-10）**：新增 `server/tests/test_log_privacy.py` 3 例——
  推送（成功路径）、拉取（响应里有、日志里没有）、整批校验失败（被拒条目原始值不得进日志）；
  断言口径：**记录值**（备注 / 金额哨兵）与**字段名**（`"payload"` / `"description"`）都不得出现在日志文本。
  双 DSN 各 **52 例 / 0 失败 / 0 跳过**（SQLite 与 MySQL 8，两次 `exit=0`）。

- **F-15 真实服务端端到端（无设备部分，2026-10-10 新增）**：`app/src/test/.../SyncApiE2ETest.kt` ——
  用**生产代码路径**（`SyncApi` 真 OkHttp + 真 DTO + 真错误码映射）打真在跑的 API（真 MySQL），
  断言：注册→登录→`me`→推 3 类实体→拉回（**中文 + emoji 逐字一致、UTF-8 44 字节相等**）→
  重复推送 **no-op**（`applied=0` 且 `server_seq` 不前进）→ 墓碑下行 → `status` 计数 → `refresh` 轮换 →
  **旧 refresh token 被拒** → 坏令牌归 `TOKEN_INVALID`。**1 例 / 0 失败 / 3.05s**；
  另在 MySQL 侧独立核验落库结果（3 条记录 / 1 条墓碑 / `updated_at_raw` 逐字一致 / 墓碑 `payload` 为 JSON null），
  核验后测试数据已清理。默认由 `app/build/e2e-base-url.txt` 开关控制，未配置时如实 skipped（不冒充通过）。

- **F-15 Android 同步核心（TASK-066 完成，2026-10-10）**：本地同步引擎落地——
  `domain/usecase/sync/{SyncModels,SyncPlan,SyncUseCase}.kt`（三模式 双向/仅上传/仅下载；三路比较 base↔local↔remote + LWW + 输家留档）、
  `data/local/sync/SyncEntityAdapter.kt`（6 类实体适配器 + 注册表）、Room **v3 → v4**
  （只加 `sync_state` / `sync_conflicts`，**既有 7 表 CREATE 与索引逐字未变**，`app/schemas/com.minibook.data.local.db.MiniBookDatabase/4.json` 已生成）、
  `di/DatabaseModule.kt` 增 3 个 DAO 提供者。「本地不丢」五道保险全部接线：同步前快照 `files/sync_backup/`（留最近 3 份）、
  删除只走墓碑、冲突留档、失败不推进游标、dry-run 预览 == 执行。
  **JVM 单测 20 例 / 0 失败 / 0 跳过**（`SyncPlanTest` 19 + `SyncPayloadContractTest` 1）；**全量回归 460 例 / 0 失败 / 0 跳过**。
  - 两处设计修订（`07_decisions.md` 记 **DEC-035 / DEC-036**）：墓碑改为「`sync_state` 有、本地已无」的**差分补发**
    （原方案要改 5+ 处删除路径，漏一处即静默不同步；差分零侵入，且覆盖「删账本」批量清表与「导入替换」）；
    上行判据改为「与基线不一致」⇒ 时钟回拨不再漏推；payload 定为 Room 行**原样 JSON** + 跨语言键集断言
    （`SyncPayloadContractTest` **未跳过** ⇒ 真的解析了 `server/app/entity_spec.py` 并逐字一致）。
  - 未闭合：`MigrationV3To4Test` 已写**未跑**（instrumented 需设备；真机 `A1B2C3D4` 有用户真实数据，禁装测试 APK）；
    同步 UI（TASK-067）与真机 E2E（TASK-068）待做。

- **F-15 服务端 API 实现（2026-10-10，本轮落地，用户请求「开发 api 服务，让数据上云」）**：新增 `server/`
  （FastAPI + SQLAlchemy 2 + Alembic + PyMySQL，纯 Python）——用户系统（注册 / 登录 / 刷新轮换 / 登出 / `me`，
  argon2id 口令 + JWT + 登录限流）、同步接口（`POST /sync/push`、`GET /sync/pull`、`GET /sync/status`；
  幂等 no-op / 墓碑 / LWW 冲突回传 / **整批回滚**）、6 类实体字段白名单校验、Alembic `0001_init`
  （SQLite 实跑通过；MySQL 侧离线 DDL 核验 `DATETIME(6)` / JSON / BIGINT / InnoDB / utf8mb4 / 复合主键）、
  `server/README.md`（起服务 / 测试 / 双 DSN 说明）。
  **测试：`pytest` 47 例 / 0 失败（SQLite）**，`alembic upgrade head` 通过。
  仍未闭合：MySQL 上跑同套测试（**TASK-069 等用户给 DSN**）、日志不含 payload 的自动化断言、Android 客户端（TASK-065~067）。
  实现差分（游标原子自增、`updated_at_raw`、`server_seq_global`、JWT aware-UTC 踩坑等）记在 `14_cloud_sync.md §12`。

- **F-15 云端同步设计（2026-10-10，用户请求「开发 api 服务，让数据上云」；本轮仅设计，未动代码）**：新增 `14_cloud_sync.md`
  （完整设计：口径问答、目标/非目标、总体架构、服务端选型与目录、数据模型、API 契约、认证与隔离、同步协议三模式、
  冲突策略、墓碑、Android 落点、Room v3→v4 迁移、隐私边界、11 条可测验收断言、测试计划、上云路径、风险）；
  并回写 `01_prd.md §F-15`（需求 + 验收 + 异常处理）、`02_architecture.md §19`（与 §6「云端预留」的兑现关系 + 口径修正说明）、
  `03_database.md §12`（本地只加两表 + 云端 schema 指针）、`07_decisions.md` **DEC-031~034**、`06_tasks.md` **TASK-061~068**。
  四项已确认口径：**先本机跑通 / 数据库 = MySQL（用户明确「不用 pg 用 mysql」；代码 DSN 驱动，SQLite 走开发与单测）/
  用户名+口令 JWT 不做邮件验证 / 全量核心业务表**；MySQL 特有口径（utf8mb4、索引长度、JSON 列、**DATETIME(6)**、
  用户名大小写归一化、`FOR UPDATE` 游标）已写入 `14_cloud_sync.md §3.4` 与 DEC-031。
  实现待开始（TASK-061 起）；**MySQL 环境未就绪是当前唯一阻塞项**（TASK-069，需用户选定提供 DSN / 本机安装 / 容器测试库）。

## v1.0.0 收尾

P2 技术债清账 + 文档收口（本地 debug 交付，不涉及真机 / release / 云端）。

### Added

- 导入账单平台提示卡可点击直接选文件（4ccaca1）。
- 设置页主题颜色调色盘（d0759b3）。
- 主题色覆盖容器色与底部菜单栏（0f748f7）。

### Changed

- **P2-5 枚举命名**：`GRANULARITY` 改名 `Granularity`（类名大驼峰），同步报表 ViewModel/Screen/UseCase 与测试引用（b9c0e6f）。
- **F-12 P2-4 接口分层**：`BillParserFactory` 接口由 `data/local/bill` 归位 `data/datasource`（与 `TransactionRunner` 对称），实现 `DefaultBillParserFactory` 留 `data/local/bill`（7c42f4c）。
- **P2-2 超限文件拆分**：`SearchScreen.kt` / `BudgetsScreen.kt` / `SearchViewModel.kt` 拆至 ≤300 行——`OptionalDateField` 下沉 `ui/components`、搜索条件区归 `ui/search/components`、预算进度卡收敛为单一可复用组件、搜索过滤纯函数抽独立文件（f4fd92d）。
- **待审项 a 导航统一**：账本管理入口 `openLedgerManage()` 加入 `HomeNavigation`（CompositionLocal），Home / Settings 经 `LocalHomeNavigation` 读取，删除 `MainScreen.onNavigateToLedgerManage` 参数穿透（63eaa26）。
- **文档回写**：`docs/04_api.md §12.3` BudgetProgress 契约对齐实现（`month` 为 `String`(yyyy-MM)、总预算扁平 `totalBudgetFen`/`totalUsedFen` 0 哨兵、`category` 非空 + 孤儿预算跳过）。

## [1.1.0] - 2026-10-10

### Changed

- **短信导入两种扫描方式：增量 / 全量（2026-10-10 下午，用户第三次反馈；未提交；DEC-030 / TASK-060）**：用户要求「我本次导入已经看过这条短信，并且不用处理了，下次再重新导入时候就不要再标记出来了，做两种扫描方式，一种全量扫描，一种增量扫描，比如我 12:00 扫描过了，增量只扫描 12:00 以后的，只有全量的才全部扫描出来」。做法：① 新增 `SmsScanMode`（`INCREMENTAL` / `FULL`）与 `SmsBillReader.readAfter()`（**严格 `date > 水位`**，**刻意不复用** L3 补扫的 `readSince()`——后者为容错故意回退 `OVERLAP_MS`（3 天），拿来做手动增量必然把 3 天前看过的又翻出来）；② `SmsBillImportUseCase.scan(ledgerId, mode)`：增量**读完即推进水位**（读到即算扫描过 ⇒ 下次不再出现），全量**不动水位**（历史永远可由全量重见，含「此前已看过」折叠区），增量首次无水位只立基线并返回空扫描（与 L3 `catchUp` 首次口径一致）；③ 入口卡改两个按钮「增量扫描」（主）/「全量扫描」（次）+ 一行小字说明差别，预览页 stepper 下方新增扫描方式说明行（`全量扫描：列出全部历史短信` / `增量扫描：本次覆盖 MM-dd HH:mm 之后的新短信，起点已更新为 MM-dd HH:mm` / 首次增量 `起点已设为本次扫描时间 …`，**只有时间戳、无任何短信内容**，DEC-026 边界不变）；④ 「重新扫描」沿用当前模式（从 `preview.scanMode` 读回），切模式 = 顶栏 ← 回入口页；`SmsScanPreview` 新增 `scanMode` / `sinceMs` / `scannedAtMs`（均带默认值，既有构造点语义不变，`assertClosed()` 与「未识别 N」全量口径一字未动）。**同日节点纠正（13:4x）**：原把节点写成「这次读到的最新一条短信时间」，用户指出「时间节点……不应该是最后一条短信的时间，应该是当前扫描的时间」——该写法有两个实证毛病（空库时永远立不起节点；节点会显示成一条旧短信的日期），改为**节点 = 本次扫描的墙钟时间（在读之前取）**，`catchUp`（L3）也一并统一为「本次补扫的墙钟时间」（两条增量路径写同一个水位，口径必须一致；补扫是 3 天重叠回扫，故不会漏）。单测 +2 例（F-14 7 类 / **77** 例，全量 52 类 / **410** 例）。真机 `A1B2C3D4`：增量 → `扫描 0 条`（那条已看过的快递短信**完全不出现**）；全量 → `扫描 1 条 = 可导入 0 + 未识别 1 + 非账单 0 + 已存在/重复 0` + `▸ 此前已看过 1 条`；节点复验：进页 L3 写 `13:47` → 增量 `本次覆盖 10-10 13:47 …，起点已更新为 10-10 13:48`，再跑全量节点**不动**，随后增量 `本次覆盖 10-10 13:48 …，起点已更新为 10-10 13:49`。模拟器 `emulator-5554`：注入短信 B 后增量 `扫描 1 条` ⇒ **只读到新到达的 B、A 不重读**。
- **验收后 UI 信息架构改版（2026-10-10 下午，用户三诉求；未提交；DEC-029 / TASK-059）**：
  1. **导入页改「三个入口」**：删除常驻的「微信账单 / 支付宝账单」导出指引卡与顶部引导段（含可点击链接那句），改为三个入口卡（账单文件导入 / 邮箱账单自动导入 / 短信账单导入）——每卡 = 标题 + 一行简短说明 + 全宽按钮；导出路径折叠进「▸ 如何导出账单？」（文案一字未改）。返回语义改为逐层退：功能结果态 ← / 系统返回键 → 回三入口页（新增 `BackHandler`），入口页才退出本页；进行中退出 = 取消本次读取（VM 随返回栈销毁）。
  2. **设置页改「分组汇总 + 二级页」**：首页一张卡 5 行（账本 / 外观 / 安全 / 数据 / 短信账单），每行 = 分组名 + 一行当前状态汇总 + `›`；新增四个根图二级页 `settings-appearance` / `settings-security` / `settings-data` / `settings-sms`（顶栏「← + 标题」），原分区内容逐条搬运（交互语义不变）；账本仍走 `ledger-manage`；版本号降为页脚文本（原「关于」分区只有一行）。跨层导航新增 `LocalSettingsNavigation`（同 `LocalHomeNavigation` 口径）；`MainScreen` 去掉 `onNavigateToImport` / `onNavigateToMailAccount` 参数穿透（账单导入与邮箱凭据入口移入「数据」页，由该页在 NavHost 接线）；`TrailingValue` / `TrailingChevron` / `SettingsSubPageTopBar` 提取为公共组件。

### Fixed

- **设置 · 短信账单：白名单空态不再显示无意义的「清空白名单」按钮（2026-10-10 下午，用户提问「白名单在哪看，就只有清空」；未提交；DEC-030）**：白名单为空时该页只渲染一句「白名单为空…」+ **恒显的清空按钮**，用户看到的是一个清不掉任何东西的按钮，既看不出白名单在哪、也不知道怎么才会有内容（原「对某发送方手动确认入账 3 次后自动晋级」也没说清会自动出现在哪）。改为：空态**不显示清空按钮**，说明改写为「白名单为空：对某个发送方手动确认入账 3 次（期间无纠错）后自动加入；加入后会在这里逐条列出，可单独关闭它的自动记账。」；非空态先给一行「白名单（N 个发送方，打开 = 允许该发送方自动记账）：」，再逐条列出可单独开关的发送方，最后才是清空（danger 强确认语义不变）。真机空态截图 `.claude-runs/f14d-real-settings-wl.png`。
- **「扫描过的做标记」：未识别条目新增「已看过」折叠（2026-10-10 下午，用户诉求①；未提交；DEC-029）**：撤销角标后没有主动提示，噪音全落在清单里——真机上每次重扫都重复冒出同一条未识别快递短信。做法：`SmsSettingsStore` 新增 `unrecognized_ack_ms`（**只存一个时间戳**，DEC-026 隐私边界不变）→ 扫描时 `receivedAtMs <= 水位` 的条目 `SmsPendingItem.seen = true` → 清单上屏后 `acknowledgeUnrecognized()` 推进水位 → `SmsImportPresenter.lines()` 只出未看过条目，已看过进「▸ 此前已看过 N 条（点击展开）」折叠区。**口径不动**：`未识别 N` 仍按全量计（`SmsScanPreview.assertClosed()` 不变）、不删除不过滤条目、待确认清单页仍可逐条看 + 手工记一笔；单测 +2 例（F-14 7 类 / **75** 例，全量 52 类 / **408** 例）。（**同日二次反馈**：「待确认的 1 条还是永远消除不掉」→ `SmsImportPresenter.pendingCount` 改为 = **未看过的**未识别 + 疑似重复；全部已看过时数字归零，底部入口退化为不带数字的「查看未识别 / 重复记录」（记录仍可进入查看 + 手工记一笔）；全量 `未识别 N` 与闭合断言一字未动；在原 `待确认条数` 用例内扩展断言，**用例总数不变**。）
- **F-14 真机/模拟器验收发现并修复 5 个「JVM 全绿但真机静默失效」缺陷（2026-10-10，未提交）**：
  1. `SmsBillReader.selection()` 生成 `type >= 1..6.first` 非法 SQL → 真机首次扫描直接「读取失败」；改为拼 `SmsMessageType` 常量。
  2. `ImportScreen` 权限值取组合期快照 → 系统设置授权后返回仍读旧值、永久卡在引导页；改为**点击时 + `onResume` 当场复查**。
  3. `SmsReceivedReceiver` 只判 `!appLockEnabled`，漏判用户显式开启的「自动记账」总闸（未开启也可能写库）→ 统一口径 `SmsSettings.autoImportUsable`。
  4. 银行模板兜底渠道为 `OTHER`，而 `allowsAutoImport` 只认 `BANK_DIRECT` → **L2 自动记账在最常见场景永不触发**；新增 `SmsTemplate.defaultChannel`，`bank.generic` 兜底 `BANK_DIRECT`，并补回归单测。
  5. `SMS_RECEIVED` 唤起的新进程未走 UI 解锁流程、`DatabaseGate` 未 open，任何 DB 访问抛 `IllegalStateException` 且被接收器 `catch` 吞掉 → 后台链路整体静默失效；改为「自动记账可用时按 DEC-002 静默开库」+「DB 未就绪降级待确认、绝不抛」，并补隐私安全的结果/原因日志。
- **登记 ROM 级能力边界**：MIUI/HyperOS 对非默认短信应用暴露 `sms_restricted` 视图（过滤 `sp_type≠0` 的服务/通知类会话），实测 App 可见 1 条 / shell 可见 9 条 → oracle 对齐不再作为漏读判据（DEC-027）。
- **F-14 验收后口径定案：三层兜底（F1 修复保留；F2 角标方案撤销；2026-10-10，未提交；DEC-028 修订）**：
  1. **F1 补扫触发点名实不符**：设置项写「打开 App 时自动补扫」，实际 `catchUp` 全仓唯一调用点是打开**导入页**（`ImportViewModel.init`）→ 从不进导入页的用户拿不到兜底；改为首页 `HomeViewModel.init` 触发（导入页那次保留，幂等），并补可观测日志（只记计数与策略原因，DEC-026）。
  2. **F2/F3 角标方案撤销（用户决策）**：曾实现「水位不越过待确认 + `SmsPendingTracker` + 首页角标」（F2a/b/c）并验证通过，但真机实测角标**常驻去不掉**——未识别条目的 `msg:<短信_id>` 键没有任何销账出口（F3），每次开 App 补扫都重新计数。用户拍板「**角标不要了，不需要出现已读未读的，能接收到就记账，有 App 打开扫描兜底，还有最后手动导入兜底**」→ 删除 `SmsPendingTracker`（含 7 例单测）与全部销账/记账调用/UI 接线，水位回到「读到即推进」。
  3. **最终模型 = 三层兜底**：① 到达即记账（L1/L2，白名单四条件 + 应用锁门控）→ ② 打开 App 补扫（L3 增量，兜广播丢失）→ ③ 手动导入（L0 全量干跑 + 确认写入，最终兜底，与水位无关）。不再有任何「待确认/已读」状态；补扫的 `pending` 计数仅为日志口径。
- **补做 4 类边界短信复测（2026-10-10）**：通知类（106 端口）与「银行端口但正文无金额」均判**非账单**（零提醒、不猜金额）；未登记模板判**未识别**并在清单页留痕（发送方/时间/预览/原因 + 重新扫描/手工记一笔）；**跨源判重**：手工 ￥77.00 与同额同日、尚未入库的短信 → 复扫由「可导入」转「已存在/重复」，不会重复记账。

### Added

- **F-14 短信账单导入（设计定稿 + 领域/数据层实现，2026-10-09，未提交）**：第三条取件通道（F-12 文件 / F-13 邮件 / F-14 短信）。设计文档 `13_sms_bill_import.md`；PRD §F-14；DEC-025（技术路线）/ DEC-026（权限与隐私边界）；任务 TASK-051~057。
  - 核心能力：`SmsBillReader`（分页/水位/双卡/内容哈希，唯一短信 I/O 边界）、`SmsBillParser`（模板锚点抽取、**零正则**、三态输出、未识别留痕）、`SmsBillMatcher`（跨源按天匹配、双向共用、唯一候选自动判重）、`BalanceChainAuditor`（余额链缺口）、`SmsWhitelistPolicy/Store`（四条件自动记账、3 次确认晋级、纠错降级）、`SmsBillImportUseCase`（扫描→预览→确认入库→L1/L2 实时，计数闭合断言）。
  - **复用零分叉**：入库统一走 `ImportBillUseCase.importParsedRows`（`importPrepared` 同核）；`TransactionSource.SMS_IMPORT`；备份 JSON 无字段变更；**无 DB 迁移**。
  - **如实验证状态**：JVM 单测（解析/匹配/审计/白名单/计数闭合/编解码）**52 类 / 408 例 0 失败**；**真机 + 模拟器端到端验收已完成（2026-10-10，见 `13_sms_bill_import.md §14` 与 DEC-027）**，验收后 UI 改版（TASK-059 / DEC-029）亦已双端实测；仍**无真实银行账单样本**，应用锁开启态、通知逐条模式、L4 分享导入未覆盖（L3 开 App 补扫场景已于 2026-10-10 真机验证）。
- **首页分类联表**：首页最近交易列表显示真实分类名/图标/颜色（经 ObserveCategoriesUseCase 的 id→Category 映射，替代原 null 占位 ❔）。
- **可测性改造**：DatabaseGate 提炼 DatabaseProvider 接口，RoomTransactionRunner/RoomDataSourceImpl 改为依赖接口（加密语义不变），使事务原子性可在 androidTest 用内存库测试。
- **搜索（F-06）**：交易（备注模糊 / 金额区间 / 分类 / 日期区间 / 类型）与债务（姓名 / 方向）组合筛选，搜索词防抖 + min>max 校验，结果分组时间倒序 + 只读明细。
- **报表统计（F-07）**：月/年视图 + 收支趋势 / 分类占比 / 结余走势三图，Compose 原生 Canvas 自绘（折线/柱状/环形/图例/空态，DEC-011 零第三方图表库）。
- **预算管理（F-08）**：月度总预算 + 分类预算，已用/剩余/超支进度，超支页内标红 + 首页入口红点（DEC-012 仅 App 内告警、无系统通知栏）。
- **测试**：JVM 单测 56 → 95 例（预算超支口径 / 搜索组合 / 报表累计跑表 / min>max 校验 / 分类删除级联）。
- **账单导入（F-12，需求）**：微信/支付宝账单导入 PRD 落盘——支付宝 CSV(GBK) / 微信 XLSX 动态表头解析、`transactions` 4 新列（`externalId`/`counterparty`/`paymentMethod`/`source`）、幂等去重与自动分类映射（01_prd.md §F-12 + DEC-015，状态评审中待架构评审）。
- **账单导入（F-12，架构/DB/API 设计）**：`transactions` 加 4 列 + `UNIQUE(ledgerId, externalId)`、DB version 2（`MIGRATION_1_2`，DEC-018，SQLite 唯一索引 NULL 互异⇒手动记录不参与去重）；微信 XLSX 手动 zip+XML 解析（`ZipFile`+`XmlPullParser`，DEC-016 零第三方库）+ 支付宝 GBK CSV 动态表头解析；`BillParser` 抽象 + `ImportBillUseCase`（逐行过滤 + 单事务整批写入，DEC-015/019）；自动分类映射（`BillCategoryMapping`）+ 幂等计数；`import` 路由 + `ImportViewModel` 状态机；测试策略 + TASK-040~044 拆分落盘（02/03/04/06/07 文档）。
- **账单导入（F-12，UI 设计）**：P-15 导入账单页 + C-33~C-37 组件（DEC-020）——首页「导入账单」tile 主入口 + 设置「数据」区可选入口；`import` 根图全屏页四态（Idle 提示 / Busy 忙碌 / Result 结果 / Error 错误）；结果页态（非弹层）+ 解析失败折叠明细；`TransactionListItem`/`TransactionDetailSheet`/`TransactionSheet` 增 `counterparty`/`paymentMethod`/`source` 展示（金额语义色不动）；source 筛选可选增强（C-37）；精确中文文案表（05_ui.md §9 + 07_decisions.md DEC-020）。
- **账单导入（F-12，测试）**：Tester 补测——JVM 单测 111 → 132 例（新增/扩展 `BillParserFactoryTest` 7 / `BillTextTest` 7 / `BillHeaderMatcherTest` 4 / `EnumRoundTripTest` +2 / `MapperRoundTripTest` +1），覆盖 DEC-017 格式探测、金额/日期/备注转义边界、动态表头容错与列序重排、枚举与 mapper 新字段往返；新增 instrumented 迁移测试 `MigrationV1To2Test`（补齐 `androidTestImplementation(libs.androidx.room.testing)` + `androidTest` assets `srcDir("schemas")`），`:app:compileDebugAndroidTestKotlin` 绿；发现 androidTest 反引号含空格方法名无法 DEX（P2 测试基建缺陷）与 `BillColumn` 对方账号误捕（P3）等观察项，详见 `09_test_report.md §F-12`（真机 3 项因设备未连接转遗留手动清单）。
- **账单导入（F-12，评审）**：Reviewer 评审 F-12 全量交付（`be1ccdd..HEAD`）——PRD 十项验收全过、金额整数分、DB 1→2 迁移三方逐字一致、132 JVM 例 + instrumented 迁移测均已闭合；**结论 FAIL-with-issues**：1 项 P1（`TransactionDto`/`BackupJsonMapper` 未携带 F-12 四个新字段 → 备份/迁移还原后丢 source/对方/支付方式/externalId、重导同账单重复入账）+ 6 项 P2（工厂嗅探优先级 / §9.3.3 编辑态展示缺失 / 事务回滚单测口径失真 / 工厂接口层级 / CSV KDoc 多行 / XLSX 空行计数），无 P0，详见 `08_review.md`。

- **邮件账单自动导入（F-13，设计与决策）**：新增「自动取邮箱账单并导入」功能设计——QQ 邮箱 IMAP（授权码 + `ID` 命令）拉取 → **本地过滤**账单邮件（实测服务端 `SEARCH` 在本场景返回 0，禁用）→ 支付宝取邮件附件 / 微信取正文下载链接（HTTPS，链接 7 天有效）→ **自动穷举 6 位数字口令**（实测口令恒为 6 位纯数字且每期不同）→ ZipCrypto 解密 + 原生 `Inflater` 解压 → 交给 F-12 既有导入链路。新增 `12_mail_bill_import.md`（含全部实测证据与排除记录）+ `DEC-022`（技术路线）+ `DEC-023`（网络/凭据隐私边界修订）+ `01_prd.md §F-13` + `02_architecture.md §17` + TASK-045~050。
  - 已排除路线（留档避免重复尝试）：POP3、邮箱厂商 SDK/OAuth2、**已知明文攻击（bkcrack）**——实测两条目均为 Deflate 压缩，该攻击要求提供「压缩后字节流」而动态 Huffman 首块不可预测；以及「记住口令」方案（口令每期变化，已证伪）。
  - 依赖：新增 JavaMail（`android-mail`/`android-activation` 1.6.7，约 1 MB）+ `INTERNET` 权限；**不引入 zip4j**（实测为 ZipCrypto 非 AES，原生方案足够）。

- **邮件账单自动导入（F-13，实现）**：按 DEC-022 路线全量落地——QQ IMAP 授权码连接（`ID` 命令双保险、`READ_ONLY` 只读、30 天窗口拉头 + `MailBillMatcher` 本地过滤、**零服务端 SEARCH**）→ 支付宝取附件（`MailFilenameDecoder`：RFC2047 GB18030 Q/B + RFC2231 `name*N` 续行）/ 微信取正文链接（`WechatBillLinkExtractor` 精确 host+路径）→ `MailBillDownloader` HTTPS 下载（每跳强制 https、≤5 重定向、30s 超时、异常文案不含链接 token）→ `ZipArchiveInspector` 加密类型探测（method 99 / extra `0x9901` → 拒绝穷举）→ `ZipPasswordCracker` 6 位数字穷举（两段式：12 字节头快筛宽松双约定 + 解压后内容 CRC32 权威判定；前缀密钥状态预计算 + `Dispatchers.Default` 分片并发 + 4096 批次取消检查）→ `EncryptedZipExtractor` 解压（GBK/CP437 条目名）→ 交 F-12 链路（`ImportBillUseCase.importLocalFile` 与 `invoke` 共用 `importPrepared` 核心，口径零分叉）。凭据：`MailCredentialCipher`（Keystore AES-256/GCM 独立别名）+ `MailAccountStore`（落 `noBackupFilesDir`，解密失败防御性置空）+ 新增 P-16 邮箱账单设置页（`mail_account` 路由）；导入页新增 `MailBusy`/`MailResult`/`MailNotConfigured`/`MailError` 四态 + `MailImportPresenter`（9 分支失败文案 `when` 穷尽）。
  - 规模：新增主源码 17 文件 / 2344 行、测试 7 文件 / 951 行 / 70 例（`MailBillMatcherTest` 9 / `MailFilenameDecoderTest` 16 / `WechatBillLinkExtractorTest` 9 / `ContentDispositionParserTest` 6 / `ZipCryptoTest` 12 / `ZipPasswordCrackerTest` 6 / `MailImportPresenterTest` 12）；`assembleDebug` 绿（APK ≈35.8 MB）；全量单测 40 类 / 260 例 0 失败。
  - 真实账单验收（本地实跑，测试按约定删除）：三份真实账单 zip 判型均为 ZipCrypto、**全部命中 6 位数字口令**（单份 90~760 ms，多核亚秒级），解出字节与独立 Python oracle 完全一致。
  - 如实限制（详见 `04_api.md §14.9`）：手动输入口令 UI 未实现（穷举失败降级为结果页 `PasswordNotFound` 提示）；`ImapMailClient`/`MailBillDownloader`/`MailAccountStore` 无 JVM 单测（JavaMail 无假服务器、Context/Keystore 无替身，未引入新测试依赖）；`totalMailCount == billMailCount`（窗口内邮件总数不可得不伪造）；真机端到端、release 构建待设备/后续。
  - 文档回写：`04_api.md §14`、`05_ui.md §10`、`06_tasks.md` TASK-045~050、`07_decisions.md` DEC-022/023 实施状态、`12_mail_bill_import.md §11`。

- **邮件账单自动导入（F-13，分步骤进度可视化 + 多邮箱）**：一键收取升级为「看得见每一步」——5 步竖向 stepper（连接邮箱 / 拉取邮件 / 筛选账单邮件 / 处理账单邮件 / 完成汇总，每步附中文摘要：`已连接（IMAP/SSL）` / `窗口内共 N 封` / `命中 N 封（微信 x / 支付宝 y）` / `成功 N 封 / 失败 M 封` / `新导入 … / 已存在 …`）+ 第 4 步展开区（`第 i/N 封 · 文件名` + 子步骤如 `下载账单` / `解压账单` + 口令穷举百分比）+ 逐封结果行卡（`✅/❌ 平台 · 文件名 — 结果`）。设置页「邮箱账单」更名**「邮箱凭据」**并支持**多账号**（`MailAccountStore.accounts` 列表 + `upsert`/`delete`：0 个引导配置、1 个直接收取、≥2 个弹「选择邮箱」选择框 —— 选一个 / 全部收取 / 取消）。新增 `MailImportProgress` / `MailImportStep` / `MailImportStepState` / `MailImportSummary` 等模型（`domain/model/MailImportModels.kt`）。
- **邮件账单自动导入（F-13，进度页与结果页合并为单页）**：按用户要求**成功或失败都不再切换版式**——`MailBusy` / `MailResult` / `MailError` 三态统一渲染同一个 `MailImportContent`（单页单滚动容器：`verticalScroll` 内容区 + 钉底「重新选择 / 返回」按钮），成功后**原地追加**结果摘要与失败明细，失败时**原地追加**错误卡；删除旧的独立结果页组件（`ImportMailResultContent`），`ImportScreen` 的 F-12 SAF 文件导入链路不动。
- **测试**：F-13 JVM 单测大幅扩展——`MailHtmlTextTest` 19 例（含真机邮件 HTML 形态的实体转义 / 自闭合标签 / 引号内 `>` 边界）+ `WechatBillLinkExtractorTest` 21 例（含尾随标点、host 伪装、端口与路径边界）+ `MailImportStepTrackerTest` 7 例（进度快照累积回归，含真机 `○○○○✓` 场景复刻）；全量 **44 类 / 327 例 0 失败**。
- **真机验证（A1B2C3D4，2026-09-23）**：一键收取端到端通过——窗口内 13 封邮件命中 5 封账单邮件（微信 2 / 支付宝 3）**全部成功**（`成功 5 封，失败 0 封`；两封微信账单分别映射 211 / 231 条且幂等去重生效，二次收取 `新导入 0 条，已存在 896 条`）；子步骤文案与进度头部 `邮箱 k/N` 按预期渲染；多邮箱「选一个 / 全部收取 / 取消」实测可用；授权码缺失的账号在 stepper 上以 `✗ 连接邮箱 · 授权码不可用（解密失败），已跳过该邮箱` 显式呈现。
- **v1.0.0 首个签名 Release + README 按实际重写**：为 release 增加签名配置（`signingConfigs.release` 由仓库根 `keystore.properties` 驱动，四项齐备才创建；缺失时退回未签名产物、不阻断构建），生成 `keystore/minibook-release.jks`（**不入版本库**；`.gitignore` 补 `*.jks` 与 `keystore.properties`）→ 产出**已签名** `app-release.apk`（30.98 MB，`apksigner verify` 通过，证书 CN=Minibook / SHA-256 `e137b7e5…`）；发布 **GitHub Release `v1.0.0`**（tag → `b8375d0`），资产 `minibook-v1.0.0-release.apk`（SHA-256 `5EAF6B51…`，GitHub 侧 digest 与本地一致）。README 从「AI 协作模板」说明改写为 MiniBook 真实说明（功能 F-01~F-13、技术栈、构建与签名、测试与真机状态、权限与隐私、目录与文档地图、已知限制），原 README 原样归档至 `docs/archive/README-legacy-ai-work-model-2026-07-24.md`。**如实修正**：release 现为 `isMinifyEnabled = false` 的**非混淆非裁剪**构建（`proguard-rules.pro` keep 规则为备用配置），此前 §14.9 中「含 R8 混淆/裁剪」表述已改正；release 包**运行期未装机验证**（debug↔release 签名不同，换装需先导出备份，见 §14.9）。

### Changed

- **F-13 微信取链关键路径「零正则」重写（Android ICU 教训）**：`MailHtmlText.toPlainText` 改为手写引号感知 HTML 扫描器（`tagKindAt` / `findTagEnd` / `findUrlAttribute`，`>` 在引号内不算标签结束），`WechatBillLinkExtractor.extract` 改为手写 URL 扫描 + 精确匹配（host 必须全等 `tenpay.wechatpay.cn`、可选端口后必须紧跟 `/`，防仿冒域名），两文件**彻底不含 `Regex(` / `toRegex`**；`IcuSafeRegexTest` 绊线测试持续守护（扫描 F-13 源码中的危险正则字面量）。
- **F-13 邮件账单导入「每种账单只取最近一封」**：`MailBillImportUseCase` 在 FILTER 后经 `MailBillSelector`（`domain/usecase/mail/MailBillSelector.kt`，纯 Kotlin、JVM 可单测）按平台（支付宝/微信）各保留 `sentDateMillis` 最新一封，同平台旧邮件**不处理**——`itemLines` 留 `⏭ {平台} · {主题} — 已有更新的同类账单，跳过`，FILTER 摘要追加「，每种账单只取最近一封 → 待处理 K 封」；`billMailCount` 仍按命中数计。新增 `MailBillSelectorTest` **6 例**（空输入 / 每平台各一封全保留 / 同平台多封取最新 / 真机窗口形态 2 微信 + 3 支付宝 → 只留 2 封 / 同毫秒按主题字典序兜底且与输入顺序无关 / 单平台多封仅留一封并校验 `hitCount = kept + skipped`；`kept`/`skipped` 顺序保持由前两例断言）；全量 **45 类 / 333 例 0 失败**。**真机验证（A1B2C3D4，2026-10-09）**：窗口内 3 封命中 3 封（微信 1 / 支付宝 2）→ 摘要实测「每种账单只取最近一封 → 待处理 2 封」，旧支付宝账单以 `⏭ …已有更新的同类账单，跳过` 显式跳过，`成功 2 封 / 失败 0 封`、幂等 `新导入 0 条 / 已存在 240 条`；「选中的确为最新」由「保留的 ✅ 行为 21 条（≠ 旧账单 111 条）」判定。2026-09-23 那次的「5 封 → 2 封」已无法复现（旧账单不在收件箱了），差异与依据见 `04_api.md §14.9`。

### Deprecated

### Removed

### Fixed

- **事务原子性测试**：补全 TransactionRunnerAtomicityTest 三个空骨架用例为真实断言（deleteById 三表分发 / runInTransaction 异常回滚 / deleteByLedger 四表批删），内存库 + DatabaseProvider 替身，无新增依赖。
- **P1-1 孤儿预算静默丢失**：删除分类时于同一事务内级联清理该分类的预算行（`BudgetDao.deleteByCategoryId` + UseCase 事务闭合），防分类删除后其预算在 budgets 表成孤儿、从预算进度静默消失。
- **F-12 评审收口（1×P1 + 5×P2）**：备份 JSON 的 `TransactionDto` 补交易 4 新字段并双向映射（P1-1，`source` 旧 JSON 缺省兜底 `manual`，防备份/迁移还原后丢 `externalId` 致重导重复入账）；工厂按 zip 魔数前置嗅探（防 `.csv` 名 zip 内容失配）；编辑弹层加来源/对方/支付方式只读信息块；补 `insertBatch` 抛异常回滚单测；修正支付宝 CSV KDoc「换行」声明；微信 XLSX 空白行跳过（对齐 CSV 口径）——详见 08_review.md 与 cb679a7。
- **微信下载链接丢失（F-13 实现期发现并修复）**：`ImapMailClient.ExtractedContent.bodyText()` 原仅取 `text/plain` 部分、`stripHtml()` 原先移除标签导致 `href` 中的 URL 一并丢失 → 微信账单（正文链接仅在 HTML `<a href>`）取不到下载链接。修复：`bodyText()` 拼接 plain+html 两路、`stripHtml()` 先提取并保留 `href`/`src` URL 再去标签。
- **JavaMail 打包冲突（F-13 实现期发现并修复）**：`android-mail`/`android-activation` 1.6.7 的 `META-INF/NOTICE.md`（及其他 META-INF 元数据）与既有依赖打包冲突导致 `assembleDebug` 失败 → `packaging` 块 `excludes` 排除 5 个冲突元数据文件；另补 R8 `-dontwarn` 与 keep 规则（`javax.mail.**`/`com.sun.mail.**`/`javax.activation.**`）。
- **邮箱凭据保存即崩溃 + 设置页恒显示「未配置」（F-13，真机实测发现并修复）**：`MailAccountCodec` 的条目正则含 `\}`，Android ICU 在**类初始化**时抛 `PatternSyntaxException` → `ExceptionInInitializerError`（属 `Error`，`catch (Exception)` 兜不住）→ 点「保存」即闪退、设置页恒显示「未配置」、且迁移已把旧授权码文件改名而 json 从未写出（旧凭据险些丢失）。修复：codec 改为**零正则**手写字符扫描（含邮箱格式校验），`MailAccountStore.loadOrMigrate` 读路径改 `catch (Throwable)`、写入后**回读校验通过才删旧文件**、失败整体回滚并保留旧文件（迁移失败日志只记异常类名、不含凭据），`upsert` 回滚 catch 同步加固为 `Throwable`。
- **步进行长摘要把标签挤成竖排（F-13，真机发现并修复）**：`MailImportStepRow` 里摘要 `Text` **没有任何宽度约束**，而 Compose 的 `Row` 会**先量无权重子项** → FILTER 摘要加长后（「…，每种账单只取最近一封 → 待处理 K 封」）吃满整行，标签「筛选账单邮件」被挤到近乎 0 宽、**竖排成单字**（真机 2026-10-09 用户截图复现；同一缺陷对失败行长摘要「授权码不可用（解密失败），已跳过该邮箱」同样成立，属既有隐患被新文案放大）。修复：标签与摘要**各带权重**（`StepRowLabelWeight 1f : StepRowSummaryWeight 2.4f`）且摘要 `TextAlign.End`，超宽只在自己那一列折行；短摘要行的渲染与修复前逐像素一致。真机复验（A1B2C3D4，2026-10-09）：5 行标签统一 `x[133-426]`（293px）、摘要统一 `x[450-1152]`（702px），筛选行摘要折两行且标签正常，uiautomator dump 全页**无文字重叠、无越界、无单字竖排**；布局纪律已写入 `05_ui.md §10`。
- **微信账单「邮件里没有找到下载链接」（F-13）**：真机 2 封微信邮件全部失败 → 定位后用零正则重写取链路径（见 Changed）并在真机复测通过（`成功 5 封，失败 0 封`）。**如实记录**：真机诊断（旧正则匹配次数 / HTML 是否含链接 / 去标签后链接是否存活）证明**旧正则在真机 ICU 上匹配正常**，最初「ICU 语义差异导致匹配失败」的推断**不成立**；旧实现的确切根因已不可回溯（旧 APK 与旧文件均被覆盖、且未提交版本历史）。结论以真机端到端通过为准，并以零正则重写 + ICU 绊线测试作为永久防线。
- **收取成功后 stepper 显示 `○○○○✓`（F-13，进度快照不累计）**：每份快照把未列出的步骤一律补 `PENDING`，已完成的步骤被后续快照打回待执行。修复：新增 `MailImportStepTracker` 累积步骤状态（每账号一份实例），同一快照序列正确显示 `✓✓✓✓✓`。
- **被跳过的邮箱在 UI 上不可见（F-13，多账号真机验证暴露）**：跳过分支只往 `itemLines` 追加 ❌ 而不发进度快照 → UI 永远看不到，用户会误以为「全部收取」覆盖了所有邮箱（静默漏数据提示缺失）。修复：跳过账号也发快照（stepper「连接邮箱」标 `FAILED` + 原因，其余 `PENDING`，携带累积逐封明细）。

### Security

- **F-13 隐私/权限边界（DEC-023）**：本项目**首次引入网络与外部凭据**，对 PRD §7「所有功能离线可用、无网络依赖」做**显式例外化**（仅 F-13 邮箱账单拉取）。边界写死为：邮箱授权码与 SQLCipher master key **同级保护**（Keystore AES-256/GCM，不落明文、不进日志）；IMAP **只读**（不删除/不标已读/不移动/不改 flag）；出网点仅 `imap.qq.com:993` 与 `tenpay.wechatpay.cn:443`（均 TLS）；**不上传任何用户数据**、不接统计/上报 SDK、不做后台联网；临时文件只落 `cacheDir` 且 `finally` 删除。

## [1.0.0] - 2026-08-20

### Added

- **收支记录（F-01）**：收入/支出记录，金额按「分」存 Long（杜绝浮点误差），支持日期/分类/备注。
- **债务管理（F-02）**：借/还平级模型（lend/repay × owe_me/i_owe 四态），明细 + 按对象双视图；净额口径 CASE WHEN SQL（lend×owe_me=+、lend×i_owe=−、repay×owe_me=−、repay×i_owe=+）。
- **分类管理（F-03）**：18 条内置分类（12 支出 + 6 收入，不可删/不可改 type/isBuiltIn，DEC-007）+ 自定义分类；删除被引用分类走迁移。
- **数据导入导出（F-04）**：明文 JSON 全量备份（version 1 JSON / BackupV1，DEC-005）；legacy 旧版 JSON 导入默认账本（DEC-004）；备份排除 DB 与密钥文件（DEC-006），事务原子。
- **应用锁（F-05）**：生物识别解锁 + 冷启动锁门（key(isLocked)）+ 离开超时重锁；无锁屏不降级明文（DEC-003）。
- **暗色模式（F-09）**：跟随系统 / 手动亮暗，Material 3 语义色 + 动态配色（Android 12+）。
- **数据加密（F-10）**：SQLCipher 整库加密落盘 + Android Keystore（AES-256/GCM）密钥保护。
- **多账本（F-11）**：数据按账本隔离，创建/切换/删除（默认账本禁删），账本切换自动重订阅。
- **月度结余（月度汇总）**：交易按月汇总统计，展示月度结余与收支视图。
- **技术栈**：Kotlin 2.2.0 + KSP + Jetpack Compose + Material 3 全面重构；Room 2.8.4 + SQLCipher 4.6.1、Hilt 2.58、Biometric 1.1.0、DataStore。
- **UI 体系**：首页仪表板、收支/债务/分类/设置/账本管理页面、导航壳、通用组件库（C-01~C-26）与主题设计系统；Phase 2 功能（搜索/报表/预算）占位页。
- **测试**：JVM 单元测试 56 例（全绿）+ instrumented 测试骨架 3 用例（净额口径 / 月度汇总 / 迁移原子性）。

### Changed

- Hilt 版本收敛 2.60.1 → 2.58（DEC-001）。
- 仓库根对齐 minibook-3.0，文档编号规范化。

### Deprecated

### Removed

### Fixed

- 修复 DEC-002 双钥系统完整性缺陷（半实现补全）（b4aa338）。
- Stage 8 警告项修复：domain/data 层错误分类/校验/事务/圈账本（5315fc3）。
- Stage 8 警告项修复：UI 三页 Error 态/重试/snackbar 重放（2ea7424）。
- 修复 data_extraction_rules.xml 非法 domain 值（5107b3b）。
- 显式加载 SQLCipher native 库，修复「自动解锁失败」（b46e551）。
- 首启引导接线 + 账本图标选择器（68d9eca）。

### Security

- **双密钥系统（DEC-002）**：SQLCipher master key（32B）由 AndroidKeyStore 两个别名包裹——`minibook_master_key_auth`（锁屏认证）与 `minibook_master_key_plain`（无锁明文），两者包装同一主密钥；切换锁状态时用当前可用 key 解出 master key 再以目标 key 重包裹覆盖 `minibook_key.bin`。
- **加密落盘**：SQLCipher 整库加密，关闭应用锁时 DB 仍加密，不降级明文。
- **冷启动锁门（ARCH §12）**：NavHost 以 `key(isLocked)` 重建，锁定态仅注册 Lock 目标（不可达即安全）。
