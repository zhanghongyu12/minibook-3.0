# 测试报告

> 最后更新（**账单转债务关联，2026-10-28 / DEC-056 / TASK-094**）：客户端 JVM **580 例 / 0 失败 / 1 跳过**（新增 `BillToDebtLinkUseCaseTest` 8 例，覆盖「同事务落债务+置位」「账单已删回滚」「删债务清关联恢复统计」「反查账单转入集合」），其中 `SyncPayloadContractTest` 通过 ⇒ 客户端实体键集与新服务端白名单一致；服务端 pytest SQLite **78 例 / 0 失败** + 容器内对腾讯云 MySQL **78 例 / 0 失败**；Room `5.json` 相对 `4.json` 只多 1 个可空列；**instrumented 7 例已在 Pixel_7（API 35）执行全绿**、**设备端 9 步逐条验收通过**（长按→转为债务→预填→保存→双标记→统计让位→删债务恢复→不自动保存）；**服务端已部署**（镜像重建 + `alembic upgrade head` → `0005_transaction_debt_link`，`debt_id varchar(36) NULL`，2261 行零变更）。
> 历史：**F-15 服务端 API 首轮单测已绿（pytest 47 例 / 0 失败 @SQLite）**——已被下方「MySQL 双 DSN 轮」取代，保留备查
> 状态：F-14（短信账单导入）**JVM 单测已绿**（52 类 / 410 例 / 0 失败）；**真机 + 模拟器验收已完成（2026-10-10）**，并完成验收后口径定案（F1 修复保留；F2 角标方案按用户决策**撤销**）+ 边界短信复测 + **验收后 UI 改版（TASK-059 / DEC-029）** + **增量 / 全量两种扫描方式（TASK-060 / DEC-030）**——见下方最新章节
> 更早更新：2026-10-13（**TASK-084**：docs/15 七条整改【分类规则归一化、导入来源清单、导入前主动申请短信权限、贷款营销短信过滤、日期区间联动约束、退款记收入+「退款」分类、内置商户词典】+ 即时需求【页面顶部留白修复、首页账号信息行】；JVM 569 例 / 0 失败（含 TASK-085/F-16i `GatedFlowTest` 3 例）；**F-16j** 应用锁前置分诊——未录入生物识别时开启应用锁不再崩溃；**TASK-087 / F-16k + F-16l** 首页账号入口收成标题旁小链接 + 礼金与红包图标去重（JVM **571 例 / 0 失败 / 1 跳过**）；模拟器逐条实测通过；真机安装验证通过）
> 上一轮更新（真机换版）：2026-10-21（**真机换版安装与启动验证**：真机由 TASK-084 的包 `2FD3869…`（2026-10-13 15:22:06 安装，**不含 F-16i 门控修复**）原地覆盖为 TASK-087 的构建 `A6DD265B…`（含 F-16i / F-16k / F-16l；JVM 571 例 / 0 失败 / 1 跳过）；装前 / 装后数据逐字节一致（11,510,272 字节，sha256 `F25D2C30…BD5E9`）；`firstInstallTime` 未变、`lastUpdateTime` → 2026-10-21 14:14:12；手机内 `base.apk` 与本地产物 sha256 一致；启动无崩溃（当日 crash buffer 0 行）；只读 dump 确认 F-16k 小链接在真机就位 —— 见下方最新章节）
> 上一轮更新（F-15 上云）：2026-10-21（**TASK-088**：数据库 host → 腾讯云迁移（11 表行数一致、canonical dump 逐字节相同 `e0ec23b8…`、按账号逐行哈希相同 `326161b5…`、`alembic_version=0004_user_profile_fields`）+ 服务端**在服务器上打镜像、Docker 部署**（`minibook-api:latest`，容器 `healthy`，`/healthz` `/docs` 200）+ 模拟器对**线上服务器**跑通「注册 → 登录 → 记账 → 双向同步」闭环（服务端日志 201/200/200 + `pull`/`push` 200，库内 `24 分类 / 1 账本 / 1 交易 / 26 sync_log`）；服务端 73/73 双跑；**联测中查出服务端缺陷一处**（`_touch_device` 设备归属 → 注册被误报「用户名已被占用」），**该缺陷已于 2026-10-22 修复：TASK-089** —— 见下方最新章节）
> 最后更新：2026-10-23（**云同步 push 全 500 的根因修复**：清库把 `sync_counters` 种子行与 `alembic_version` 一起删掉 ⇒ `next_seq()` 的 `UPDATE` 影响 0 行 ⇒ **所有账号 push 全 500**，而 `pull` 正常、日志无异常行（该 500 是刻意返回的 `ApiError`）；数据面按 `MAX(sync_log.server_seq)` 补种 + `alembic stamp 0004_user_profile_fields`（**刻意不跑 `upgrade head`**），代码面 `next_seq()` 缺行自愈（DEC-055 / TASK-093）；双 DSN 各 **78 例 / 0 失败**、缺陷版注入 ⇒ 新增 2 例均红；重打镜像部署后线上发号实测 `server_seq_global` 2314→2315；**用户手机导入备份后同步成功**（云端 `demo_user` = 若干账本 / 分类 / 交易 / 债务 / 日志）—— 见下方最新章节）
> 上一轮更新（配置分仓与去敏 + 提交前体检 + 正式包真机安装）：2026-10-23（远程地址从仓库彻底移出（`sync.properties` 注入 + docs 占位符化，受版本控制文件里真实公网 IP **0 命中**）、服务端配置拆成 `server/.env.local.example` / `.env.remote.example` 两份、新增 `tools/check-secrets.ps1`（全量 **518 文件 0 命中**）、App 单测 + debug/release 打包 **BUILD SUCCESSFUL**、**正式包装到真机且界面无地址栏**（uiautomator 文本无「服务器地址」）、手机内 `base.apk` 与本地产物 sha256 一致 `48887B68…B67198`、手机自带 `curl` 直连远程 `/healthz` **200**；装正式包前导出 **（备份大小略）**备份（若干账本 / 若干分类 / **若干交易** / 若干债务）—— 见下方最新章节）
> 上一轮更新（公网入口上线：明文 HTTP + 端口 38080）：2026-10-23（外网直连 `/healthz` **200**、`/docs`+`/redoc`+`/openapi.json` 全 **404**、`minibook_test` 登录+push+pull 全绿（重推幂等、seq 不推进）、容器日志记录**真实公网 IP**（限流口径正确）；服务端套件 **76 passed**；App 正式包改指该地址（DEC-053，见 `14_cloud_sync.md §16`））
> 上一轮更新（TASK-089 设备归属缺陷修复 + TASK-090 公网接入方案（待审批））：`_touch_device` 由「重复 INSERT 主键」改为「设备已存在即改归属」，并补证 `login()` 同样中招（无异常兜底 ⇒ 换账号登录 500）；新增 2 条回归测试并**在缺陷版代码上确认两条均失败**；本地 SQLite **75 passed** / 腾讯云 MySQL **75 passed**；新镜像 `6f29b8c354b7` 已部署（回滚标签 `minibook-api:pre-fix-20261021`），线上实测「用属于他人账号的 device_id 登录」旧 500 → 新 200；公网方案结论为**只开 443 + Caddy(TLS-ALPN)**，见 `14_cloud_sync.md §15`（**该方案已于 2026-10-23 被用户否决**：443 不批准、暂不加证书 → 改为明文 HTTP + 38080，见 §16）—— 见下方最新章节）
> 维护者：Tester

## 最新测试（账单转债务关联：统计让位与双向标记，2026-10-28）

**测试信息**
- 触发（用户原话）：「增加一个功能，可以把账单和债务关联起来，在账单上长按，弹出框中增加一个转为债务，点击后跳转到记一笔债务的页面，账单金额和日期带入，其余手动填写，不自动保存，然后把这个债务和账单关联起来。在账单的统计中不统计这这个账单。如果债务中删除了这条数据，统计重新统计账单。另外再账单这条数据上标记一个绿色的标记，已转债务。债务条目上标记一个账单转入。」
- 范围：客户端数据层/领域层/UI/导航/备份 + Room v4→v5 迁移 + 服务端协议扩列（白名单/模型/列映射/迁移 0005）+ 文档。**UI 逐条验收与服务端部署本轮未做**，如实挂账见下。

**结果**
| 项 | 证据 |
|---|---|
| 客户端编译 | `:app:compileDebugKotlin` **BUILD SUCCESSFUL**；`:app:compileDebugAndroidTestKotlin` 通过（新增 2 份仪器测试产出 class 文件） |
| 客户端单测 | `:app:testDebugUnitTest` **76 套 / 580 例 / 0 失败 / 1 跳过**；新增 `BillToDebtLinkUseCaseTest` **8 例全绿**（含「`setDebtId` 影响 0 行 ⇒ 抛 `InvalidInput`」「删债务 ⇒ 账单 `debtId` 清回 null」「`FakeTransactionRunner.transactionsRun == 1` 证明两条写在同一事务」） |
| 协议锁步 | `SyncPayloadContractTest`（解析 `server/app/entity_spec.py` 对照客户端序列化键集）**通过** ⇒ 客户端 `debtId` 与服务端白名单逐字一致 |
| 服务端单测 | `pytest`（SQLite）**78 例 / 0 失败**；过程中修 2 处不兼容：① `reassemble` 在「0005 降级后列已删」时报 `AttributeError` ⇒ 改 `getattr(row, col, _MISSING)` 补 `None`；② 测试夹具（`conftest.transaction_payload` / `test_migration.TX_PAYLOAD`）补 `"debtId": None` 以保持「逐字拼回」语义 |
| 服务端一致性 | `test_column_maps_match_spec_and_models`（spec ↔ `COLUMN_MAPS` ↔ 模型列，含 `max_length == 列长度`）**通过** |
| Room schema | `5.json` vs `4.json`：`transactions` 字段 14→**15**，新增仅 `debtId`（`affinity=TEXT`、可空、无默认值） |
| 仪器测试（**已在模拟器执行**） | Pixel_7（API 35，x86_64）`connectedDebugAndroidTest` **7 例 / 0 失败 / 0 错误 / 0 跳过**（4.339 s，`TEST-Pixel_7(AVD) - 15-_app-.xml`：`tests="7" failures="0" errors="0"`）—— `TransactionDebtLinkDaoTest` 6 例（月结余/日趋势/月趋势/分类占比四处排除、`clearDebtIdByDebt` 后恢复、账本隔离、列表仍显示已转账单）+ `MigrationV4To5Test` 1 例（存量行完好、`debtId` 默认 NULL、可写、`runMigrationsAndValidate(…, 5, …)`） |
| 服务端部署 | 腾讯云 `minibook-api` 镜像重建（`97efae6b…`，回滚标签 `minibook-api:pre-debt-20261028` = `0e773b7f…`）→ 容器 `healthy` → `alembic upgrade head` = `0005_transaction_debt_link (head)`；`information_schema` 确认 `transactions.debt_id varchar(36) NULL`；库内 `transactions` 仍 **2261 行**（纯加列、零数据变更）；`/healthz` 外网 **200** |
| 服务端 MySQL 双跑 | 容器内 `TEST_DATABASE_URL` → 腾讯云 `minibook_test`：**78 例 / 0 失败**（进度行 72+6 全 `.`，无 `F`/`E`） |

**设备端逐条验收（Pixel_7 / API 35 / 全新安装 `com.minibook` 1.1.0，2026-10-28）**

| 步骤 | 操作 | 观测证据（uiautomator 文本） |
|---|---|---|
| 1 | 记一笔支出：金额 88、分类「餐饮」、日期预填 2026-10-28、保存 | 首页「支出 88.00 / 结余 ¥-88.00」，最近交易「餐饮 ¥88.00 2026-10-28」 |
| 2 | 账单页长按该行 | 弹出菜单：**编辑 / 转为债务 / 删除 / 批量管理 / 取消** |
| 3 | 点「转为债务」 | 页面标题变「**转为债务**」，顶部提示「来自账单：金额与日期已带入，其余请手动填写。保存后这条账单会标记为「已转债务」，并不再计入收支统计。」；金额 **88.00** 已带入、日期 **2026-10-28** 已带入、对方姓名**空**（手填 `TestFriend`） |
| 4 | 保存后回账单页 | 该行出现绿色标记「**已转债务**」（¥88.00 仍在列表里，不隐藏） |
| 5 | 债务页 | 出现「借出 / TestFriend / **账单转入** / 2026-10-28 / ¥88.00」，债务净额 **¥88.00** |
| 6 | 回首页 + 报表 | 首页「支出 **0.00** / 结余 **¥0.00**」（该账单不进统计）；报表页「支出 ¥0.00、收支趋势**暂无收支数据**」 |
| 7 | 再长按该账单 | 菜单项变为「**已转债务**」，点它**不跳转**（停留在列表，菜单不关闭）= 已转的账单不可重复转换 |
| 8 | 债务页长按 → 删除 → 确认 | 「暂无债务记录」、净额 ¥0.00；回首页「支出 **88.00** / 结余 **¥-88.00**」= **统计自动恢复**；账单行「已转债务」标记消失 |
| 9 | 不自动保存验证：再点「转为债务」进入页面后直接按返回 | 债务页仍「暂无债务记录」、首页支出仍 88.00 ⇒ 未落库、未关联（符合「不自动保存」） |

**过程如实记录（两次弯路）**
1. **先跑错了 App**：模拟器上残留一个 2026-07-09 的旧包 `com.minibook.app`（versionName 1.0），我第一次按 `pm list packages | grep minibook` 命中的就是它，装的是我们的 `com.minibook`（1.1.0）却启动/操作了旧包 ⇒ 看不到「转为债务」。用户当场指出「你测试的 App 不对啊」。改用 `am start -n com.minibook/.MainActivity` 后正常；判断依据 = `run-as com.minibook ls databases` 有 `minibook-encrypted.db` + `dumpsys package` versionName 1.1.0。
2. **模拟器被后台任务回收**：第一次用「起进程后 sleep 5 就退出」的后台任务启动模拟器，任务结束时 emulator 进程被一并回收（`device offline/not found`）。改为把 emulator 作为**常驻后台作业**运行，验收全程稳定。

**真机换版（2026-10-28 16:53，用户要求「给我手机的装上去」）**

| 项 | 证据 |
|---|---|
| 安装方式 | `adb -s A1B2C3D4 install -r app-release.apk` → `Success`（同发布签名 `CN=Minibook`、同 `versionCode=2`，**原地覆盖不卸载**） |
| 数据保留 | `firstInstallTime` 仍 **2026-10-23 10:25:56**（未变）、`lastUpdateTime` → **2026-10-28 16:53:54** |
| 包一致性 | 手机内 `base.apk` sha256 = 本地产物 `06AB538D4863B0BA…C91C1B`（逐字节一致） |
| Room v4→v5 迁移 | 启动即正常（迁移失败会崩），且首页数字与装前**完全一致**：结余 **¥-1,234.56**、较上月 ¥-56.78、收入 **12.34**、支出 **1,234.56**、最近交易「交通 / 娱乐 / 餐饮…（金额略）」全部在位 |
| 新功能就位 | 记账页长按任一账单 → 菜单「编辑 / **转为债务** / 删除 / 批量管理 / 取消」；点「取消」退出，**全程未修改用户任何数据** |
| 稳定性 | 启动后 `logcat` 无 `FATAL EXCEPTION` |

**诚实挂账**
1. 真机只做了「读」验证（菜单可见 + 数据未变）：**未在用户真实账单上做转换**（会改动其账目，需用户自己点）；用户若点「转为债务」保存，服务端已升级到 0005，`debtId` 可正常上云。
2. 服务端 MySQL 容器 `mysql` 的 Docker 健康检查**长期超时**（`mysqladmin ping -h 127.0.0.1 -P 49779` 10 s 超时、`exit=-1`，容器 2026-10-10 起 `restarts=0`）—— 与本轮改动无关（只加了一列），`minibook-api` 侧 `healthy`、pytest 对 MySQL 读写全绿、外网 `/healthz` 200；未擅自改 MySQL 容器的健康检查配置。
3. 云端探针账号 `probe723551` / `probe723685` 仍在库里（清理属操作者策略拒绝，未重试）；本轮设备验收与真机装包全程**未登录、未上云**，未新增任何云端数据。
4. 模拟器（Pixel_7）在本轮末尾自行退出（后台作业 `exit 1`），验收证据已全部落盘，无需重开。

## 上轮测试（云同步 push 全 500 的根因修复：计数器缺行自愈，2026-10-23）

**测试信息**
- 触发（用户原话）：「手机上的云同步怎么失败了」；随后拍板「1、`next_seq` 缺行自愈 + 回归测试；2、清库边界写进文档；3、重打镜像部署」（「是否从宿主机恢复旧云数据」用户明确**不需要**）。
- 范围：服务端计数器自愈（代码 + 数据库恢复）+ 线上重部署与发号实测 + 文档。App 侧本轮无改动；真机未由本轮操作（数据由用户自行导入并同步）。

**结果**
| 项 | 证据 |
|---|---|
| 根因定位 | 同形报文（由手机备份重建 1157 条、200/批）打线上，拿到 500 响应体 `server_seq 计数器缺失，请先执行 alembic upgrade head`；库内 `sync_counters` **0 行**、`alembic_version` **空**、`sync_log` **0 行** |
| 修复前/后对比 | 修复前 `push` **500**（0.3 s 快速返回、日志无非 INFO 行 —— 因为它是 `ApiError` 刻意返回）；修复后 6 批全 **200**、`server_seq` 0→1157、首批 `skipped:1`（幂等） |
| 回归测试有效 | 新增 `tests/test_counter_selfheal.py` 2 例；**注入缺陷版（去掉自愈）后两条均失败**（`ApiError`），恢复后通过 |
| 双 DSN 双绿 | SQLite **78 例 / 0 失败 / 0 错误 / 0 跳过**（30.819 s）；腾讯云 MySQL `minibook_test`（经 SSH 隧道）**78 例 / 0 失败 / 0 错误 / 0 跳过**（457.733 s）；junitxml 双份均 `tests="78" failures="0" errors="0" skipped="0"` |
| 部署 | `docker compose build && docker compose up -d` ⇒ 容器 **Up (healthy)**、`/healthz` **200**、容器内 `app/services/sync_service.py` 含 `_seed_counter`（grep 2 处） |
| 线上发号实测 | 部署后 `login 200` → `status` `server_seq_global` **2314** → push 1 条新变更 `applied:1` → `status` **2315** → `pull?since_seq=1157` 取回该条（`next_seq` 发号路径真跑通） |
| 用户侧闭环 | 云端 `demo_user` = **若干账本 / 分类 / 交易 / 债务 / 日志**，与手机备份逐项吻合（该账号此前为 0 行） |
| 数据边界 | 清库只删业务表行、`sync_counters` 与 `alembic_version` 永不删 —— 已写入 `docs/14_cloud_sync.md §18` 与 `server/README.md`（DEC-055） |

**诚实挂账**
- 线上发号实测用的是我的探针账号 `probe723685`（本就待清理），该账号因此多落 **1 行**；
- 探针账号 `probe723551` / `probe723685` 仍在库里，清理 SQL 中含 `DELETE` 的命令被操作者策略拒绝，**未重试、未绕行**，SQL 已交付用户；
- 清空前的旧云数据（旧 `demo_user` 若干笔等）按用户决定**不恢复**；宿主机原始 MySQL 未动，需要时可再迁一次；
- 自愈只在**缺行**时生效：若计数器值被人工改小，仍会撞 `sync_log` 的 `(user_id, server_seq)` 主键（自愈不校验语义）。

## 最新测试（配置分仓与去敏 + 提交前体检 + 正式包真机安装，2026-10-23）

**测试信息**
- 触发（用户原话）：「真机已经连接，下面做如下任务 1、给我真机安装一个，可以连远程的。2、远程服务器配置文件拆分成两份，一份本地开发的一份远程开发的。提交代码前一定要检查，千万不要把远程服务器地址以及一些账号密码提交到仓库中。」；中途追问「手机端为什么还展示服务器地址的输入栏」，随后选定「直接换成正式包（不显示地址栏）」。
- 范围：仓库配置分仓与去敏 + 提交前体检脚本；App 正式包真机安装（真机为全局独占资源，本轮由主代理单独执行，未开模拟器、未动线上服务）。

**结果**
| 项 | 证据 |
|---|---|
| 地址出仓库 | 受版本控制文件里真实公网 IP **0 命中**（ripgrep 全仓）〔本行原先把该地址原文写在此处，2026-10-23 复查时发现并改为占位符：体检脚本的规则要求 IP 出现在 `//`、`@`、`:端口` 或配置赋值位，**反引号内的散文不触发**，是规则的一个已知缺口〕；`app/build.gradle.kts` 改为从 `sync.properties` 注入；`docs/*.md` 的真实公网 / 内网 IP 已占位符化 |
| 配置拆两份 | `server/.env.local.example`（SQLite + 开发密钥）/ `server/.env.remote.example`（MySQL + 占位符）；`server/.env.example` 删除；服务器上两份模板同步写入，服务器 `.env`（823 B，真实值）未动 |
| 提交前体检 | `tools/check-secrets.ps1 -All` → `Checked 518 file(s) [all tracked + untracked]` + `OK`，退出码 **0**；过程中如实记录 2 处假命中（PS 5.1 用 ANSI 读 UTF-8），改 `-Encoding UTF8` 后清零（见 DEC-054 / §17.2） |
| App 单测与打包 | `:app:testDebugUnitTest` + `:app:assembleDebug` + `:app:assembleRelease` → **BUILD SUCCESSFUL in 5m 31s**；`SyncUrlValidatorTest` 样本改成 TEST-NET 文档段地址后全绿 |
| 正式包 | `app/build/outputs/apk/release/app-release.apk` **31.64 MB**、签名 `CN=Minibook`（证书 sha256 `e137b7e5…`）；`BuildConfig.java`（release）`SYNC_BASE_URL = "http://<远程公网IP>:38080"`（值来自 `sync.properties`，仓库里没有） |
| 手机侧连通性 | 手机自带 `curl http://<远程公网IP>:38080/healthz` → **HTTP 200**（5G 直连，不经隧道；ICMP 被安全组挡掉属正常，故用 curl 而非 ping） |
| 真机安装 | 先装 debug 包原地覆盖验证「升级不丢数据」（`firstInstallTime` 保持 2026-05-18、首页账目完好）→ 再按用户选择**卸载后装正式包**：手机内 `base.apk` sha256 `48887B68…B67198` **与本地 release 产物逐字节一致**；签名 `CN=Minibook` |
| 无地址栏（用户诉求） | 正式包登录页 uiautomator 全量文本 **12 条**，`含「服务器地址」输入栏: False`；截图确认只有「用户名 / 口令 / 登录」 |
| 数据备份与恢复路径 | 装正式包前用 App「导出数据」备份到 Download：`minibook_backup_20261023_102433.json` **（备份大小略）**（若干账本 / 若干分类 / **若干交易** / 若干债务；样例即界面那笔美团记录），手机两份 + 本机一份（`%TEMP%\minibook_backup_phone.json`） |
| 未闭环项（诚实挂账） | ① 真机登录 POST 尚无服务端日志见证：用错误账号做 UI 探针时，手机**拼音输入法**把注入文本转成中文（用户名框变成「王蓉怕是23」）、口令框为空，界面回「请先填写口令」⇒ 未发出登录请求；最终确认留给用户登录后看服务端访问日志。② 服务器上旧 `.env.example`（1691 B）仍在：删除它的命令被操作者策略拒绝，未重试、未绕行。③ 手机原 debug 包内的**本地数据已按用户选择清空**，备份文件可恢复。 |
| 环境 | 真机 `A1B2C3D4`（1200×2608 / density 480）；装正式包后已 `pm clear` 并重启，留在干净登录页；备份文件仍在 Download |

## 上一轮测试（公网入口上线：明文 HTTP + 端口 38080，2026-10-23）

**测试信息**
- 触发（用户原话）：「我开放安全组了 你可以进行最后一步测试，目前正式包也采用<远程公网IP>:38080 连接」
- 前置决策（用户当日给出）：**暂时不加证书**、**443 不批准**，改用「另外的无关端口」转发；并选择关掉生产接口文档。
- 执行者：主代理（构建只在主代理侧跑；本轮未动真机、未开模拟器）。

**结果总览**

| 项 | 结果 |
| --- | --- |
| 端口选择 | 38080（改前实测空闲；服务器 `ufw` **未启用**，真正把关的是腾讯云安全组） |
| 服务器改动 | `docker-compose.yml` 加 `"38080:8000"`（保留 `127.0.0.1:8000:8000` 给隧道）+ `.env` 设 `ENABLE_DOCS=false`；镜像重建（`6f29b8c354b7` 之上）后 `up -d`，容器 `Up (healthy)` |
| 端口发布 | `docker ps` 显示 `0.0.0.0:38080->8000/tcp` 与 `127.0.0.1:8000->8000/tcp` 并存 |
| 文档收敛 | `/healthz` **200**；`/docs`、`/redoc`、`/openapi.json` 全部 **404**（回环与公网两口一致） |
| **外网直连**（这台 Windows，`curl --noproxy`） | `http://<远程公网IP>:38080/healthz` → **200**，`db:mysql`（不走 SSH 隧道） |
| 外网文档口径 | 同一路直连：`/docs` **404**、`/openapi.json` **404** |
| **外网端到端** | 以 `minibook_test` 明文登录 **200** → `/me` 200 → push#1 `applied:2, skipped:0, server_seq:2870` → 重推 `applied:0, skipped:2, server_seq:2870`（幂等、seq 不推进）→ pull 2 条全在 |
| 真实 IP（限流前提） | 服务端访问日志记为 **`<本机公网IP>`**（本机直连出口），**不是**网关 `172.18.0.1` ⇒ 登录限流按真实客户端 IP 生效 |
| 服务端套件 | 本地 SQLite **76 passed**（新增 1 条：`ENABLE_DOCS=false` 时三处文档路由 404、探针不受影响） |
| App 侧适配 | 正式包 `SYNC_BASE_URL` 改为 `http://<远程公网IP>:38080`；新增 `network_security_config`（main 仅对该 IP 开明文 / debug 整体放开）+ `SyncUrlValidator` 明文白名单同口径收窄；新增 1 条 JVM 测试 |
| App 构建产物 | JVM 单测 **572 例 / 0 失败 / 0 错误 / 1 跳过**（`SyncUrlValidatorTest` 7 例，含新增的「正式包只放行内置明文入口的 host」）；`assembleRelease` **BUILD SUCCESSFUL**（11m14s）→ `app/build/outputs/apk/release/app-release.apk` **31.64 MB**，sha256 `BD99E52F8D69A51F6A314E3426236052FBF8CFA5CCACA761C86D413210A737EE`，**已签名**（CN=Minibook，证书 sha256 `e137b7e5…`） |
| 正式包明文口径（包内实证） | aapt2 读 APK 内 `res/8G.xml`（即 `network_security_config`）：`base-config` + `domain-config` + `domain=<远程公网IP>` ⇒ **默认禁明文、只放行该 IP**；对照调试包那份为 `cleartextTrafficPermitted="true"`（整体放开）；合并后 release 清单含 `android:networkSecurityConfig="@xml/network_security_config"` |
| 正式包内置地址（包内实证） | `app/build/generated/source/buildConfig/release/com/minibook/BuildConfig.java`：`SYNC_BASE_URL = "http://<远程公网IP>:38080"` |
| 诚实挂账 | 明文传输（口令与令牌可被链路中间人看到，用户知情接受）；`mysql` 容器仍显示 `(unhealthy)`（healthcheck 10s 超时误报，库正常）；插桩 E2E 卡死问题未解 |

## 上一轮测试（设备归属缺陷修复 + 公网接入方案，2026-10-22）

**测试信息**
- 触发（用户原话）：「1、修复你说的那个。2 服务器你先列出一个安全部署的计划，不用动手，我审批以后部署，保证在其他地方能连接到。用最少得端口，需要开放端口权限我在控制台帮你开。」
- 执行者：主代理（构建只在主代理侧跑；本轮未动真机、未开模拟器）。
- 拓扑不变：腾讯云 `<远程公网IP>:3228`（Ubuntu 20.04 / 2 vCPU / 1.97 GB），`mysql` 容器只绑 `127.0.0.1:49779`，`minibook-api` 容器只绑 `127.0.0.1:8000`。

**结果总览**

| 项 | 结果 |
| --- | --- |
| ① 缺陷修复 | ✅ `_touch_device()` 改为「设备行已存在即**改归属**」，不再重复 INSERT 主键；`register()` 的 `IntegrityError → username_taken` 只留给真正的「并发注册同名」；**未加错误码、未改客户端** |
| ② 回归测试 | ✅ 新增 2 条（注册 / 登录各一）；**在缺陷版代码上两条均失败**（`UNIQUE constraint failed: devices.id`）⇒ 测试有效性有反证，不只是「跑绿了」 |
| ③ 本地 SQLite | ✅ **75 passed**（32.56 s） |
| ④ 腾讯云 MySQL（容器内跑） | ✅ **75 passed**（73.19 s） |
| ⑤ 镜像与部署 | ✅ 服务器上重建：`minibook-api:latest` = `6f29b8c354b7`；`docker compose up -d` 后容器 `Up (healthy)`、`/healthz` 200（`db:mysql`）；容器内 `app/routers/auth.py:107` 含改归属行；回滚标签 `minibook-api:pre-fix-20261021`（`7c3e39166f84`） |
| ⑥ 线上实测（部署生效） | ✅ 以属于 `emu1012a` 的 `device_id=aeafb982…` 登录 `minibook_test` → **200**（旧版该路径 500）；`devices` 该行 `user_id`：`emu1012a` → `minibook_test` |
| ⑦ 常驻测试账号 | ✅ `minibook_test` 已建（user_id `0c2cb1f7…`），register 201 / login 200 / me 200 / `sync/status` 200 / `sync/pull` 200；凭据落 `test-account.txt`（**已 gitignore**：`git check-ignore` 指向 `.gitignore:91`） |
| ⑧ 公网接入 | ⏸ **只出方案未动手**：结论「**只开 443** + Caddy（TLS-ALPN-01，因此不开 80）」，待用户审批；见 `14_cloud_sync.md §15` |
| ⑨ 线上数据清理 | ⚠️ **未执行**：含 `DELETE` 的命令被操作者策略拒绝，按纪律**未重试、未绕行**；前置备份已做（`online-before-clean-20261022-084632.sql`，1,597,682 字节），9 条指向 `users` 的外键**确认全为 CASCADE**，清理命令已交用户自行执行 |
| ⑩ 诚实挂账 | `mysql` 容器仍显示 `(unhealthy)` —— 其 healthcheck（`mysqladmin ping`）超时仅 10s，在 2 vCPU 小机上握手超时被判失败，**库本身正常**（所有查询通）；插桩 E2E `SyncDeviceE2ETest` 卡死问题仍未解 |

## 上一轮测试（F-15 上云：数据库迁移 + 服务器打镜像 Docker 部署 + 模拟器联测线上服务器，2026-10-21）

**测试信息**
- 触发（用户原话）：「1 把数据库迁移到腾讯云这台服务器中，2 后台服务部署到这台服务器中，3 用模拟器测试线上服务器。」中途纠正：「后台服务器不要直接部署，在服务器打镜像，然后用 docker 部署」。
- 拓扑：Windows 宿主 → **host `<内网主机IP>`**（老库，`mysql:8.0` 容器，`0.0.0.0:49779`，数据 `/opt/docker/mysql/data`）→ **腾讯云 `VM-0-4-ubuntu`**（Ubuntu 20.04 / 2 vCPU / 1.97 GB / 40 GB，Docker 28.1.1 + Compose v2.35.1；`mysql:8.0.46` 容器只绑 `127.0.0.1:49779`，应用容器只绑 `127.0.0.1:8000`）。
- 执行者：主代理（真机与模拟器为全局独占资源，全程串行；构建只在主代理侧跑）。

**结果总览**

| 项 | 结果 |
| --- | --- |
| ① 数据库迁移 | ✅ 11 张表行数一致；`alembic_version = 0004_user_profile_fields`；全部 InnoDB / `utf8mb4_unicode_ci`；规范化 canonical dump **逐字节相同**（sha256 `e0ec23b8…`）；`information_schema.columns` 哈希 `c36ee8a4…`；表元数据哈希 `e55ba085…`；**按账号逐行哈希 `326161b5…`** |
| 数据量 | 6 用户 / 2203 交易 / 2301 `sync_log`（迁移后；源库 10 账号中的 5 个测试账号按既有约定先清掉，真实账号未动，源库保留可回滚） |
| ② 镜像与容器 | ✅ 镜像**在服务器上**构建：`minibook-api:latest`（`7c3e39166f84`，199 MB）；容器 `Up (healthy)`；`/healthz`、`/docs`、`/openapi.json` 均 200；镜像内 12 个依赖可导入（含 `cryptography 50.0.2`） |
| 服务端测试 | ✅ 腾讯云 MySQL **73 passed**（70.10 s）；本地 SQLite **73 passed**（35.06 s） |
| 发布冒烟（容器内 httpx，自清理） | ✅ 注册 201 / 登录 200 / me 200 / push `{applied:1, skipped:0, server_seq:2842}` / pull 取回 / status `counts:{ledger:1}` / refresh 200 且旧 refresh 401 / **幂等重推 `applied:0, skipped:1` 且 `server_seq` 2842→2842** / delete-account 200 / 删后登录 401 |
| ③ 模拟器 → 线上（UI 路径） | ✅ **闭环成立**：注册 201 → 登录 200 → me 200 → 记一笔（餐饮 ¥12.34）→ 双向同步「上传 **26** 条 · 云端删除 0 · 下载 0 · 冲突 0」；库内 `categories 24 / ledgers 1 / transactions 1 / sync_log 26`，交易 `amount=1234`、`server_seq=2868` |
| ③′ 模拟器 → 线上（插桩 E2E） | ⚠️ 已确证请求打到线上（服务端建号 + 令牌），但用例**卡死在 `preview()` 本地阶段** → 列为遗留，未粉饰 |
| ④ 发现的服务端缺陷 | ✅ **2026-10-22 已修**：`_touch_device` 改为「设备已存在即改归属」；补 2 条回归测试（先在缺陷版代码上确认两条**均失败** `UNIQUE constraint failed: devices.id`，修复后通过）；本地 SQLite **75 passed** / 腾讯云 MySQL **75 passed**；已重打镜像 `up -d` 部署，线上实测同一 `device_id` 换账号登录由旧版 500 变 200 |

**① 迁移核对明细（可复算）**
- 导出：`mysqldump --single-transaction --routines --triggers` → `minibook-full.sql`（1,596,813 字节）；导入目标库后做三方哈希比对。
- **canonical dump 逐字节相同**的前提是先把 `CHARACTER SET utf8mb4` 的渲染差异归一：`sed -E 's/ CHARACTER SET [a-z0-9_]+ COLLATE/ COLLATE/g'`，归一后前后 sha256 均为 `e0ec23b8…`（未归一前看起来像字符集漂移，实为渲染差异 —— 这条记录在此以免下次误判）。
- 按账号逐行哈希（把每个用户 6 张业务表 + `sync_log` 的行按主键排序拼接后哈希）前后均为 `326161b5…`：`demo_user` 若干交易 / 若干日志、`demo_user_b` 若干 / 若干、`emu1012a` 1 / 27，`phone1012a` / `diag_probe_2` / `e2e_67470` 均为 0。
- **如实披露**：源库 10 个账号中有 4 个 `dev_*`（E2E 遗留，各 3 交易 + 1 分类 + 1 账本 + 7 日志，可用 `E2e!Passw0rd` 登录证明来源）+ 1 个 `smoke_*` 残留，共 5 个测试账号在迁移前按项目既有约定走 `delete-account` 清除；host 库未 DROP 任何表，必要时重导 `minibook-full.sql` 即可复原。保留物：腾讯云 `/home/ubuntu/minibook-migrate/`、host `/root/minibook-migrate/`。

**② Docker 部署的关键取舍**
- 用户要求「服务器打镜像 + Docker 部署」，因此服务器上**不装 Python 依赖**，只跑容器；`server/requirements.txt` 新增锁定 `cryptography==50.0.2`（MySQL 8 `caching_sha2_password` 在非 TLS TCP 上需它走 full-auth，首次连接 / 改密后必需）。
- **刻意不设 `--workers`**：限流是单进程内存实现，多进程会让限流失效（容器内固定 1 个 uvicorn 进程）。
- **`.env` 不进镜像**（`.dockerignore` 排除，密钥只由 compose 的 `env_file` 注入，权限 600）；`JWT_SECRET` 与 host **逐字符一致** ⇒ 迁移前签发的设备令牌继续有效。
- 端口只绑 `127.0.0.1:8000`，对外仍走 SSH 隧道；容器 `read_only: true` + `tmpfs /tmp`，`mem_limit 448m` / `cpus 1.0` / `pids_limit 256`。

**③ 模拟器联测线上（通道与对证）**
- 通道：模拟器内 App 调试版默认 `SYNC_BASE_URL=http://127.0.0.1:8000` → `adb reverse tcp:8000 tcp:18000` → 本机 18000 → SSH 隧道 → 腾讯云 `127.0.0.1:8000`。**不改地址、不用公网端口**（模拟器 dump 可见「服务器地址（仅调试版可见）」框内即为该回环地址）。
- UI 路径逐步留证：注册页表单（用户名 / 口令 ×2 / 服务器地址 / 同意条款勾选框）→ 点「注册并登录」→ App 进「用户信息」页（昵称、用户名、注册时间、上次登录）⇒ 该页数据来自线上 `GET /me`。
- 服务端同一连接（`172.18.0.1:60096`，即经隧道进来的宿主侧地址）三条日志：`POST /api/v1/auth/register 201` → `POST /api/v1/auth/login 200` → `GET /api/v1/me 200`。
- 同步：先在 App 内记一笔（餐饮 ¥12.34），再进 设置 → 数据 → 云同步 → 双向同步，预览「上传 26 条改动 + 0 条删除 · 下载 0 条 · 冲突 0 条」→ 执行 → 结果卡「同步完成：上传 26 条 · 云端删除 0 条 · 下载 0 条 · 删除本地 0 条 · 冲突 0 条」+「同步前备份：`minibook_backup_20261021_081811.json`」。
- 服务端：`GET /api/v1/sync/pull?since_seq=0&limit=200 → 200`、`POST /api/v1/sync/push → 200`；库内对账 `categories 24 / ledgers 1 / transactions 1 / sync_log 26`，`sync_log` 按实体分组 `category 24（seq 2843–2866）/ ledger 1（2867）/ transaction 1（2868）`，交易行 `amount=1234 / date=2026-10-21 / server_seq=2868` ⇒ 上传条目数与库内落地条目数**逐一对上**。

**④ 缺陷：`_touch_device` 设备归属导致注册被误报「用户名已被占用」**
- 现象：同一台模拟器上注册第二个账号时，App 显示「该用户名已被占用，请换一个」，但该用户名在库中不存在（`SELECT … WHERE username='emu1021a'` 空），服务端日志只有一条 `POST /api/v1/auth/register → 409 Conflict`。
- 定位：`register()` 里 409 只有两个出口（`auth.py:150` 预检命中、`auth.py:167` 捕获 `IntegrityError`）；预检不可能命中（库里没有该用户名），故是 `flush()` 阶段的 `IntegrityError`。`_touch_device()`（`auth.py:76-99`）在 `device = db.get(Device, device_id)` 存在但 `device.user_id != user.id` 时**执行 `db.add(Device(id=device_id, …))`**，而 `devices.id` 是主键 ⇒ 主键冲突。
- 直接对证：App 私有存储 `files/datastore/sync.preferences_pb` 里 `device_id = aeafb982-6fd0-4b5a-9848-e46ce67ee961`（明文可读），而线上 `devices` 表该行属于 2026-10-12 建的账号 `emu1012a`（型号 `sdk_gphone64_x86_64`，即本模拟器）。
- A/B 对照：`pm clear com.minibook`（换到全新 `device_id b53bf895…`）后，**同一套 UI 操作立刻注册成功**（201），库内 `emu1022a` + 1 设备 + 1 refresh_token ⇒ 变量只有 device_id，结论确定。
- 影响与归属：任何「一台设备换账号再注册」的用户都会撞上，且提示误导排查方向；该缺陷**与本次迁移无关**，是迁移过来的老数据暴露出的既有问题。
- **修法（2026-10-22 已落地并部署）**：`_touch_device` 遇到设备属于他人时**改归新用户**（改 `user_id` + 刷新 `last_seen_at`/`name`/`platform`/`app_version`），不再重复 INSERT。**同日补证第二个面**：`login()` 也调用该函数（无异常兜底）⇒ 换账号**登录**会直接 500。
- **修复轮证据**：新增 2 条回归测试并**先在缺陷版代码上确认两条均失败**（`UNIQUE constraint failed: devices.id`）→ 修复后本地 SQLite **75 passed**（32.56 s）、容器内腾讯云 MySQL **75 passed**（73.19 s）；新镜像 `minibook-api:latest` = `6f29b8c354b7`，`docker compose up -d` 后 `healthy`，容器内 `app/routers/auth.py:107` 含改归属行；**线上实测**：以属于 `emu1012a` 的 `device_id=aeafb982…` 登录 `minibook_test` → **200**（旧版该路径 500），`devices` 表该行 `user_id` 由 `emu1012a` 改为 `minibook_test`。回滚标签 `minibook-api:pre-fix-20261021`（`7c3e39166f84`）。

**诚实边界（本轮未覆盖）**
- 插桩 E2E `SyncDeviceE2ETest` 仍卡死：已排除 `DatabaseProvider` 门控（其 `openState` 默认实现为 `flowOf(database)`），卡点收窄到 `preview()` 的本地阶段（`files/sync_backup/` 无新增 ⇒ `exportAll` 未跑；无第三个 socket；服务端 15 分钟只有健康检查）。栈取不到（AVD `ro.debuggable=0` / `ro.build.type=user`，`/data/anr` 不可读，`kill -3` 的 trace 进 tombstoned）。
- 公网入口未开放：Windows 侧只有 3228 可达，8000/80/443 均不通 ⇒ 要公开 API 需加腾讯云安全组规则（本轮按安全默认只绑回环 + 隧道）。
- 无数据库自动备份/定时任务（本轮只做了手工 dump 与保留物）。
- 真实账号 `demo_user` / `demo_user_b` 等**未在 App 上做联网实测**（本轮模拟器用的是新建测试账号 `emu1022a`；真机侧沿用既有会话）。

## 上一轮测试（真机换版安装与启动验证：TASK-084 包 → TASK-087 包，2026-10-21）

**测试信息**
- 授权：用户原话「装手机里吧」（仅此一次授权；延续「真机安装每次先申请」的纪律，前一轮同类授权见 2026-10-13 章节）。
- 设备：小米 `某机型`（Android 16 / SDK 36 / HyperOS），adb `A1B2C3D4`，1200×2608。装前 App 未运行，手机前台是用户正在使用的其他 App（美团）⇒ 本轮只做「快照 → 安装 → 快照 → 启动 → 只读检查」，**除启动与 dump 外未做任何点击**。
- **换版含义（本轮最重要的事实）**：真机此前停留的包是 **TASK-084 的产物** `2FD3869…`（见 `06_tasks.md` TASK-084 测试行），安装时间 2026-10-13 15:22:06；而 **TASK-085 / F-16i 门控修复**（`GatedFlow.kt` mtime 2026-10-13 15:55:04、`RoomDataSource.kt` 15:44:30）发生在该包构建**之后** ⇒ 真机连续 8 天跑的是一个**已知会崩的包**：`logcat -b crash` 中 2026-10-17 ~ 10-20 的实录（保留至今的 3 次）全部是 `java.lang.IllegalStateException: DatabaseGate 未解锁/未打开，无法提供 LedgerDao / CategoryDao`，栈为 `LedgerDataSourceAdapter.observeAll(RoomDataSource.kt:88) → ledgerDao(RoomDataSource.kt:91)`，**栈内无任何 `GatedFlow` 帧**，且这条 `observeAll → ledgerDao` 直连路径在当前源码里已不存在（现为 `gate.gatedFlow { … }`）。
- 装前只读确认：`versionCode=2 / versionName=1.1.0`、`lastUpdateTime=2026-10-13 15:22:06`、`firstInstallTime=2026-05-18 18:28:54`、手机内 `base.apk` sha256 `2fd3869…f9b1` —— 与 TASK-084 产物哈希逐字一致。

**流程与结果**

| 步骤 | 命令 / 对象 | 结果 |
| --- | --- | --- |
| 装前快照 | `am force-stop` → `run-as com.minibook tar cf - .` | **11,510,272 字节**，sha256 `F25D2C308FA06BD3FFABA9740ACC56F2D21718FAC4D9EA097271680F698BD5E9` |
| 第一次安装 | `install -r app-debug.apk` | **失败**：手机在流式安装中途从 USB 掉线（`adb devices` 空、Windows 侧 Android 设备消失）。根因是机器上并存两套 platform-tools，一个残留的旧 adb server 顶掉了客户端；统一到单一 adb server 后恢复 |
| 中断完整性复检 | 重连后 | `lastUpdateTime` 仍 2026-10-13 15:22:06、手机内 `base.apk` 仍 `2fd3869…f9b1`、数据快照仍 11,510,272 字节 **同哈希** ⇒ **中断未留半装状态、未动数据** |
| 安装 | `install -r app-debug.apk` | **Success** |
| 装后快照（未启动 App） | 同上 | **11,510,272 字节，sha256 与装前逐字节一致** ⇒ 安装未触碰本地数据 |
| 包信息 | `dumpsys package com.minibook` | `versionCode=2 / versionName=1.1.0`；`firstInstallTime=2026-05-18 18:28:54` **未变**；`lastUpdateTime` 2026-10-13 15:22:06 → **2026-10-21 14:14:12** |
| 产物核对 | 手机内 `/data/app/.../base.apk` vs 本地产物 | 均为 sha256 `a6dd265b02b172ee4a3b0fe16dd71b5bc51ef67c20c0605a4c0c00d5fa14b736` ⇒ 手机跑的就是 571 例通过的那个构建 |
| 构建内含修复 | 解压 APK 内 `classes*.dex` 检索字面量 | `classes6.dex`：`GatedFlow` / `gatedFlow` / `DatabaseGate` / `giftmoney` 全部命中；`classes16.dex`：`giftmoney` 命中 ⇒ **F-16i 与 F-16l 的代码确实在这个包里** |
| 启动 | `am start -n com.minibook/.MainActivity` | `pid=24856` 存活、`mCurrentFocus=com.minibook/com.minibook.MainActivity`；首页渲染出「首页」标题、账号小链接与「默认账本」 |
| 崩溃检查 | `logcat -b crash -d`；主缓冲 `FATAL/AndroidRuntime` | **当日（10-21）0 行**；主缓冲无 com.minibook 记录。crash buffer 内最近一次仍是 2026-10-20 的**修前包**崩溃 |
| 启动后数据 | 再快照并与装前逐文件比对 | 26 个文件中 **25 个逐字节一致**（加密 DB / `-wal` / `-shm`、`minibook_key.bin`、`mail_accounts.json` / `mail_auth_*.bin`、全部 DataStore 与备份 JSON）；**唯一变化 = `files/profileInstalled`** —— 该文件由 `androidx.profileinstaller` 写入（`classes18.dex` 内含该字面量与 `androidx/profileinstaller` 包名），是基线 profile 安装标记，属 APK 换版后首次启动的预期簿记，**非用户数据** |
| 只读 UI 核对（F-16k） | `uiautomator dump` | `首页 [72,200][190,280]` + 账号小链接文本 `[220,213][365,267]`，其可点祖先 `[48,168][389,312]`（高 **132px ≈ 44dp**，`clickable=true`），右侧 `默认账本 [915,208][1090,273]` 未变；**大卡已消失** ⇒ F-16k 在真机路径成立（此前仅模拟器证据） |
| 收尾 | `input keyevent 3` + `am force-stop` | 焦点回到 `com.miui.home` 桌面，手机交还用户；App 已停止，不留前台占用 |

**未做（如实挂账）**
- 真机**逐页点击走查**：用户当时正在用手机，除启动与 dump 外未点击任何控件。
- **F-16l 图标真机目视**：只验到 `giftmoney` 字面量在包内（+ 模拟器已目视确认）；真机分类页未打开（需点击 + 截屏，且应避免截取含用户账目的界面）。
- **F-16i 的真机复现路径回归**（加锁 → 解锁 → 立刻回桌面 / 回前台）：本轮只观察到「启动后数分钟内无崩溃」，**不构成该路径的回归证明**（该限制在 2026-10-13 章节已登记，本轮未消除）。
- 装机期间快照均含用户真实数据（备份 JSON、邮箱凭据密文等），与历史快照一致地只落在仓库外的 `%USERPROFILE%\minibook-rollback-20261012\`；本轮**未截屏**，无个人金额画面留存。

## 上一轮测试（TASK-087：首页账号入口收成标题旁小链接 + 礼金/红包图标去重，2026-10-13）

**测试信息**
- 触发：用户反馈「1、首页账号设置不用那么大  在首页两个字后面加个 "未登录"  "昵称" 作为链接就可以了 2、分类中收入的礼金和红包的图标重复了」。
- 范围：`app/src/main/java/com/minibook/ui/home/HomeScreen.kt`（`HomeTopBar` 小链接、删除 `HomeAccountRow`）、`app/src/main/java/com/minibook/ui/components/CategoryIcons.kt`（新增 `GIFT_MONEY` / `GiftMoney`、`resolve(raw, name)` 按名覆盖、`pickerKeys` 24 → 25）、`app/src/main/java/com/minibook/ui/components/CategoryComponents.kt`（`CategoryGlyph` 增 `name`）、`app/src/main/java/com/minibook/domain/model/BuiltInCategories.kt`（礼金 `icon` → `"giftmoney"`）、9 个 `CategoryGlyph` 调用点传 `name`。
- 命令：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`。

**结果总览**

| 项目 | 结果 |
| --- | --- |
| JVM 单测 | **571 例 / 0 失败 / 0 错误 / 1 跳过**（75 套件；唯一跳过 = `SyncApiE2ETest` 未配置 `MINIBOOK_E2E_BASE_URL`） |
| 新增用例 | `CategoryIconsTest` +2：礼金与红包解析结果不同、名为「礼金」的旧分类换成 `GiftMoney` 且其它分类不受影响 |
| assembleDebug | 成功；APK **39,918,654 字节**，2026-10-13 16:58:15，sha256 `A6DD265B02B172EE4A3B0FE16DD71B5BC51EF67C20C0605A4C0C00D5FA14B736` |
| 服务端 | 本轮未改动，仍 pytest 双 DSN 各 **73 例 / 0 失败** |
| 模拟器 | Pixel_7 `emulator-5554`（1080×2400）装机 + uiautomator 逐条实测 |

**模拟器实测**
- ① 首页顶栏同一行：`首页 [63,179][177,261]` + `未登录 [203,194][313,247]`，右侧仍是 `默认账本 [810,189][981,252]`；大卡已消失，结余卡 / 较上月环比 / 收入 / 支出 / 快捷入口 / 空态 / 底部导航均正常。
- ② 分类页：收入 8 项不变，其中「礼金」渲染为**手捧心**矢量（`GiftMoney`），与支出「人情」的礼物盒、与图标选择器末尾的「红包」图形均不再重复；选择器共 **25** 个图标（礼金位换新手捧心，红包保留可选）。
- `logcat -d -b crash` 无新增条目；`adb emu kill` 正常退出。

**缺陷与观察**
- 分类编辑弹层里误点图标格会立即改变选中项（模拟器实测一次点到 `CASH`），只能靠返回键取消——属既有交互，本轮未改。
- `Gift`（人情）与 `RedPacket`（红包）两条矢量视觉仍相近（都是带缎带的盒体）；本轮只解掉「礼金 vs 红包」的同图问题，未重绘 `RedPacket`，若后续仍觉相似可另开一轮换图。

## 最新测试（F-16j：应用锁前置分诊 —— 未录入生物识别不再崩溃，2026-10-13）

**测试信息**
- 触发：F-16i 模拟器回归时顺手验证安全页，点「应用锁」开关即崩（`locksettings set-pin 1234` 设了系统 PIN，AVD 未录入指纹/人脸）。
- 范围：`BiometricHelper` / `SettingsViewModel` / `SettingsSecurityScreen` / `AppLockController`。
- 命令：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`（先 `--stop`）。

**结果总览**

| 项目 | 结果 |
| --- | --- |
| JVM `:app:testDebugUnitTest` | **569 例 / 0 失败 / 0 错误 / 1 跳过**（suites=75；本次无新增用例，F-16j 靠模拟器端到端验证） |
| `:app:assembleDebug` | BUILD SUCCESSFUL；APK 39,508,475 字节，2026-10-13 16:18:41 |
| APK sha256 | `C334F7F480F3BD5B4F3D55FA7F67132D162AC81A34C51F9586A4B894DB60DA9D` |
| 服务端 | 未改，仍 73 例 / 0 失败 |

**崩溃根因（模拟器 crash buffer）**

```
FATAL EXCEPTION: main  Process: com.minibook, PID: 4040
java.lang.RuntimeException: java.lang.reflect.InvocationTargetException
Caused by: java.security.InvalidAlgorithmParameterException: At least one biometric must be enrolled
           to create keys requiring user authentication for every use
```

**修复内容**
1. `BiometricHelper.hasEnrolledBiometric()`：`BIOMETRIC_STRONG` / `BIOMETRIC_WEAK` 任一 `BIOMETRIC_SUCCESS` 即算已录入；**不含 `DEVICE_CREDENTIAL`**（只设 PIN 时它也 SUCCESS，正是崩溃场景）。
2. `SettingsViewModel.setAppLockEnabled(true)`：`isDeviceSecure()` 之后增加该分诊，不满足则发 `needBiometric` 事件，**不启用、不降级明文**。
3. `SettingsSecurityScreen`：新增生物识别引导卡（跳系统安全设置），与 DEC-003 无锁屏引导卡并列。
4. `AppLockController`：解锁 / 降级 / 升级 / 首启认证四处 cipher 创建改 `runCatching`，失败回调 `false` / `AUTH_FAILED`（保持锁定），不再崩主线程。

**模拟器复测（Pixel_7 `emulator-5554`，`CredentialType: PIN`、无生物识别）**
- 点「应用锁」开关：`pidof com.minibook` 前后同为 **4728**（未崩溃）；
- 页面显示引导卡原文「开启应用锁需要系统已录入指纹或人脸（Keystore 加密要求）。请先在系统设置中录入生物识别，再回来开启应用锁——数据仍保持加密。」+「去设置」；
- 返回设置页，安全行仍为「应用锁未开启（数据加密已启用，不可关闭）」⇒ 未产生半开状态；
- `logcat -d -b crash` **0 行**；截图 `build-verify-applock-guidance.png`（gitignored）。

**遗留**
- 未在**有指纹的真机**上走查「录入生物识别 → 开启应用锁 → 冷启动认证 → 解锁」完整成功路径（需真机安装授权）。
- F-16i 的真机复现路径（锁屏瞬间重订阅）同样仍待真机验证。

## 最新测试（TASK-085 / F-16i：解密门门控 Flow —— 修复重锁瞬间主线程崩溃，2026-10-13）

**测试信息**
- 触发：真机安装后拉取 `logcat -b crash`，发现 **2026-10-13 10:11:13** 的 FATAL（发生在装前那次安装的包上）。
- 范围：`DatabaseProvider` / `DatabaseGate` / 新增 `GatedFlow.kt` / `RoomDataSourceImpl` / `RoomDataSource` 适配器。
- 命令：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain`（先 `--stop` 停 daemon）。

**结果总览**

| 项目 | 结果 |
| --- | --- |
| JVM `:app:testDebugUnitTest` | **569 例 / 0 失败 / 0 错误 / 1 跳过**（suites=75；唯一跳过 = `SyncApiE2ETest` 未配置真实服务地址） |
| 本轮新增 | `GatedFlowTest` 3 例（门关不发射 / 解锁发射当前库值 / 重锁取消上游再解锁切新库；收集用 `UnconfinedTestDispatcher`） |
| `:app:assembleDebug` | BUILD SUCCESSFUL；APK 39,196,755 字节，2026-10-13 15:55:41 |
| APK sha256 | `B297835C81F0513D9B6DB38392A960BF4064C893B1842171944AFAC739F2AA71` |
| 服务端 | 未改，仍 73 例 / 0 失败 |

**崩溃根因（真机 crash buffer 全栈）**

```
FATAL EXCEPTION: main  Process: com.minibook, PID: 27954
java.lang.IllegalStateException: DatabaseGate 未解锁/未打开，无法提供 LedgerDao
  at LedgerDataSourceAdapter.ledgerDao(RoomDataSource.kt:91)
  at LedgerDataSourceAdapter.observeAll(RoomDataSource.kt:88)
  at LedgerRepositoryImpl.observeAll(LedgerRepositoryImpl.kt:30)
  at ObserveLedgersUseCase.observe(ObserveLedgersUseCase.kt:19)
  ...
  at androidx.lifecycle.compose.FlowExtKt$collectAsStateWithLifecycle$1$1$1
```

- `AppLockController.kt:72-73`：`databaseGate.close()` **先于** `_isLocked.value = true`；
- UI 生命周期恢复重启上游 Flow 时，旧实现在调用点 `ledgerDao() ?: error(...)` 同步抛出。

**修复与验证**
1. `DatabaseProvider.openState`（`DatabaseGate` 以 `MutableStateFlow` 覆写；`open()` 成功发射、失败与 `close()` 归 `null`）；
2. `GatedFlow.kt`：`openState.flatMapLatest { if (it == null) emptyFlow() else block(it) }`——**不能** `filterNotNull()`，否则重锁事件到不了 `flatMapLatest`、旧库流不被取消；
3. `RoomDataSourceImpl` 七个 `observe*` + 适配器 `observeAll` 门控（门关：不发射、不抛异常；解锁：切到新库 DAO）；
4. 一次性 `suspend` 查询/写入**保持硬失败**（不静默返空，遵守「不记错」）；
5. `GatedFlowTest` 3 例锁定语义；构建与单测全绿。

**遗留风险（如实记录）**
- 一次性 `suspend` 读取若恰在「关库→锁屏」窗口内触发，仍会抛 `IllegalStateException`（有意不改为静默返空）；后台路径（`SmsReceivedReceiver`、自动同步）已自行 catch。
- 本轮**未在真机复现**该崩溃（复现路径：加锁→解锁→立刻回桌面/回前台），修复靠代码时序 + 单测语义锁定，真机回归待用户当次批准后补。

## 最新测试（TASK-084：docs/15 七条整改 + 顶部留白 / 首页账号行即时需求，2026-10-13）

### 测试信息

- 本轮改动（用户 2026-10-13 两条即时需求 + docs/15 七条逐条讨论后全部拍板）：
  1. **页面顶部留白修复（F-16a）**：`MainScreen` 的 `Scaffold` 无 `topBar` 却默认吃 `systemBars` inset，与内层各页 `Scaffold` 叠加成约两倍状态栏高度 ⇒ 改为 `contentWindowInsets = WindowInsets(0, 0, 0, 0)`（底部不受影响，`NavigationBar` 自消费导航栏 inset）。
  2. **首页账号信息行（F-16a）**：`HomeAccountRow`（未登录「未登录 + 登录」链接 / 已登录「昵称 + 账号设置 ›」，整行可点跳账号页）；`SyncAuthStore` 增 `KEY_NICKNAME` / `updateNickname` / `displayName`（`GET /me` 与保存资料成功后回写）；`HomeViewModel.accountDisplay`。
  3. **分类规则归一化核心词（F-16b）**：`CategoryRuleNormalizer`（4 步归一化 + STRONG / WEAK 两档强度）；学习侧存核心词；`ImportBillUseCase` 按 STRONG → 核心词更长 → 更新新排序，`allowWeakRuleMatch` 只给预览 / 待确认路径；**L2 只认 STRONG**；算法文档 `docs/19`。
  4. **导入来源清单（F-16c）**：`SmsWhitelistEntry.importEnabled` + `SmsSenderSummary` + 扫描反推、默认全选、`SmsSenderNamer` 猜机构名、预览发送方列、白名单双开关（自动记账 / 允许导入）、只登记出过账单的来源。
  5. **导入前主动申请短信权限（F-16d）**：`ImportScreen` 的 `smsPermissions()` + `RequestMultiplePermissions` launcher，授权后按同一 mode 继续扫；引导文案补系统设置兜底。
  6. **贷款 / 授信营销短信过滤（F-16e）**：方向判定前三层预筛（回复指令特征 / 授信语义且无强交易锚点 / 账单准入硬门槛）；「利息」「消费」降级为需交易锚点语境；用户两条真实原文进 fixture；带「尾号+扣款」的真实交易带「退订回T」不误杀；脏数据按用户决定不清理。
  7. **退款记「收入」+ 单独「退款」分类（F-16f）**：`REFUND_KEYWORD` 优先判收入 + `platformCategory = "退款"` + `BillCategoryMapping` 短信侧条目。
  8. **日期区间联动约束（F-16g）**：`OptionalDateField` / `DateFilterChip` 增 `minDateMillis` / `maxDateMillis`，M3 `SelectableDates` 置灰不可选日期。
  9. **内置商户词典（F-16h）**：`res/raw/merchant_dictionary.json` 1315 条 + `MerchantDictionary` 链路 + `ImportBillUseCase` 第 1.5 步（仅预览建议，L2 不查）；`docs/20`。

### 结果总览

- JVM `:app:testDebugUnitTest`：**566 例 / 0 失败 / 0 错误 / 1 跳过**（suites=74；唯一跳过 = `SyncApiE2ETest` 未配置真实服务地址）。本轮新增 39 例：`CategoryRuleNormalizerTest` 13 + `SmsBillParserTest` 10 + 导入来源与内置词典 16（`SmsWhitelistStoreCodecTest` / `SmsSenderNamerTest` / `SmsSenderSummaryBuilderTest` / `MerchantDictionaryTest` / 词典文件校验 / `SmsBillImportUseCase` 来源过滤）。
- `:app:assembleDebug`：BUILD SUCCESSFUL；APK **39,194,040 字节**（2026-10-13 12:10），sha256 `2FD386973C39D200D28C6EBE87128F59638B811EA970BB2AF9E9422E7864F9B1`。
- 服务端：本轮未改（pytest 双 DSN 仍 73/73）。
- 模拟器（Pixel_7 / emulator-5554）：逐项实测通过，见下。

### 模拟器实测细节（uiautomator 驱动，1080×2400）

- **首页（已登录态）**：顶部账号行「emu1012a」（@297,404）+「点此进入账号设置」（@340,459）；结余卡 ¥0.00 + 「较上月 +¥88.00」保留；点击账号行 → 进入账号页（用户信息 7 行：昵称 / 用户名 / 邮箱 / 个性签名 / 地区 / 注册时间 / 上次登录，**无账号 ID**）。
- **首页（未登录态）**：退出登录后首页账号行变「未登录」+「登录云端账号，本地账目不受影响」+ 右侧「登录」链接。
- **顶部留白**：首页内容首行 y≈404、设置页首行 y≈382、交易页筛选行 y≈494（修复前约为两倍状态栏高度）。
- **交易页日期联动**：起始日期选 2026-10-15 后，结束日期选择器 **14 个日期（10/1–10/14）置灰不可选**（uiautomator 检出 `enabled="false"` 节点 14 个）。
- **设置页**：「账号」仍第一行（F-15u 保持）；短信账单页渲染正常（权限未授予、白名单为空提示）。

### 缺陷与观察

- 本轮无新增缺陷；构建期修复 2 处（`HomeViewModel` 构造缺 `syncAuthStore` 参数；`SmsSenderSummaryBuilder` 同数排序改为按号码**数值**升序而非字典序）。
- 短信授予 / 通知权限弹窗与「导入来源」勾选弹框依赖真实短信数据与权限行为，模拟器无法全量走查；设置页双开关文案与导入页代码路径已实测/审查。

## 最新测试（TASK-083：预算删除语义 / 分类预算删除立即刷新 / 账本矢量图标 / 账号页文案，2026-10-13）

### 测试信息

- 本轮改动（用户 2026-10-13 五条反馈：显示名改称昵称、账号 ID 不展示、账本图标换新、预算填了不能删除或清空、分类预算删除后不立即刷新）：
  1. **「显示名」改称「昵称」**：`SettingsAccountScreen.kt` / `ProfileEditScreen.kt` 字段与说明行、`LoginScreen.kt` 辅助说明
     （改「登录用的账号名（不是昵称）」）、`AccountViewModel.kt` KDoc 全部改口径。
  2. **不再展示账号 ID**：`SettingsAccountScreen.kt` 删掉「账号 ID」行，已登录用户信息 8 项 → **7 项**
     （昵称 / 用户名 / 邮箱 / 个性签名 / 地区 / 注册时间 / 上次登录）；`UiState.userId` 保留供内部使用。
  3. **账本图标换自带矢量集**：新增 `ui/components/LedgerIcons.kt`（16 个稳定 key + `LEGACY_EMOJI` 兜底 + `LedgerGlyph`）；
     渲染点收口（`Pickers.kt` / `LedgerManageScreen.kt` / `LockComponents.kt`）；`LedgerEditorSheet.kt` 的 emoji 快捷行 +
     手输框换成 **6 列图标网格**；默认账本与导入回退图标 `📒` → `"book"`；**不改 Room schema、不做数据迁移**。
  4. **④ 总额可删除 / 清空**：新增 `ClearTotalBudgetUseCase` + `BudgetsViewModel.clearTotalBudget()`；
     总额弹层在已设过总额时给出「删除月度总预算」danger 入口（`BudgetEditorSheet` 按模式切换按钮与确认文案）。
  5. **⑤ 分类预算删除后立即刷新（根因修复）**：删除原先走 `writableDatabase().delete("budgets", …)` 绕过 Room 失效通知 ⇒
     预算进度 Flow 不重发、必须退出页面（`WhileSubscribed(5s)` 超时重连）才刷新（也解释了「保存会刷新、删除不会」——
     保存走 DAO `upsert`）。改走新增的 `BudgetDao.deleteCategoryBudget`（`@Query` DAO 删除）。
  6. 新增测试：`LedgerIconsTest` 3 例 + `ClearTotalBudgetUseCaseTest` 1 例 + `ClearCategoryBudgetUseCaseTest` 2 例。
- 环境：Pixel_7 模拟器（emulator-5554，时区 **GMT**，`-no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`）；
  **模拟器回归期间真机 `A1B2C3D4` 未触碰**（所有 adb 命令均带 `-s emulator-5554`）；真机安装经用户当次批准
  （原话「装手机上」）后单独执行，证据见下方「真机安装与启动验证」。
- 复测用的 API：主机 `0.0.0.0:8000`（本轮重启后 pid 651144），模拟器经 `adb reverse tcp:8000 tcp:8000` 访问。

### 结果总览

| 项 | 结果 |
|---|---|
| Android JVM `:app:testDebugUnitTest` | **527 例 / 0 失败 / 0 错误 / 1 跳过**（suites=70；521 → 527 新增 6 例；唯一跳过项为需真实服务端的 `SyncApiE2ETest`） |
| Android `:app:assembleDebug` | **BUILD SUCCESSFUL**；产物 `app/build/outputs/apk/debug/app-debug.apk` **39,092,700 字节**（2026-10-13 09:19:43），sha256 `3099F8193E815EC0A6837036E01FED08FDCF5B0D699FFC3F843555F2459647DC` |
| 模拟器回归（逐条） | 账本管理页矢量账本图标 ✅ / 新建账本 6 列图标网格 + 选中态 ✅ / 总额预算「删除月度总预算」入口 + 总额专属确认文案 ✅ / 删除后页面**立即**回到空态 ✅ / 分类预算「删除该分类预算」→ 删完**不离开页面**列表立即刷新为「尚未设置分类预算，点击添加」✅ / 账号页 7 项且无「账号 ID」✅ / 编辑资料页「昵称」✅ |
| 真机 `A1B2C3D4` 安装 | `install -r` 原地覆盖 **Success**；装前 / 装后数据 tar **逐字节一致**（10,849,280 字节，sha256 `EF992D3710C00F980CB87E4CC39E62B25F90996C833773C40FEA2D8A659AB620`）；`firstInstallTime` 未变、`lastUpdateTime` → 2026-10-13 09:53:12；启动无崩溃；**手机内 `base.apk` 与本地产物 sha256 一致**（`3099F819…647DC`） |
| 服务端 | 本轮未改服务端代码（仍为 `pytest` 双 DSN 各 73 例 / 0 失败，见下节） |

### 模拟器实测细节（uiautomator + 截屏，分辨率 1080×2400）

- **账本管理**（设置 → 账本）：默认账本行左侧显示**矢量书本图标**（该账本 DB 里仍是 `📒`，走 `LEGACY_EMOJI` 兜底映射，
  证明「不做数据迁移也能正确渲染」）。
- **新建账本**：底部表单「图标」区为 **6 列 × 3 行共 16 个矢量图标**，默认选中第 1 个（book，主题描边 + `primaryContainer` 底），
  其后为笔记本 / 翻开的书 / 书籍 / 图书馆 / 便签 / 收据 / 书堆 / 公文包 / 存钱罐 / 银行 / 银行卡 / 图表 / 购物车 / 房子 / 汽车。
- **④ 总额预算**：空态点「设置总预算」→ 输入 3000 → 保存后总额卡显示 预算 ¥3,000.00 / 剩余 ¥3,000.00；
  再点「编辑总额」出现「**删除月度总预算**」；点它弹出「确定删除当月的月度总预算吗？已记录的交易与分类预算不受影响。」，
  确认后页面**立即**回到空态（「设置月度总预算，追踪本月支出」）——此前该入口恒为 `null`，用户无法清空总额。
- **⑤ 分类预算**：设 餐饮 ¥100 后列表出现该行；点行尾「编辑」→「**删除该分类预算**」→ 确认弹「确定删除「餐饮」当月的分类预算吗？」→
  删除后**停留在预算页**且列表立刻变成「尚未设置分类预算，点击添加」。
- **账号页**（模拟器登录 `emu1012a`）：用户信息 7 行 = 昵称 emu1012a / 用户名 emu1012a / 邮箱 emu@example.com /
  个性签名 emu-bio / 地区 TestRegion / 注册时间 `2026-10-12 06:16` / 上次登录 `2026-10-13 01:45`（模拟器 GMT，等价 UTC）；
  **页面内无「账号 ID」**；下方「账号操作（编辑资料 / 修改口令）」「会话（退出登录 / 注销账号）」不变。
- **编辑资料页**：首字段为「昵称」，说明「昵称会显示在账号页；邮箱 / 个性签名 / 地区都可以留空」，昵称辅助文案「留空 = 回退显示用户名「emu1012a」」。
- 负路径顺带验证：用不存在的账号 `emu1012b` 提交 → 页面就地提示「**用户名或口令不正确**」（不泄露账号是否存在），无崩溃。

### 真机安装与启动验证（2026-10-13，用户当次批准「装手机上」）

- 授权：用户原话「装手机上」（仅此一次授权；此前一轮已说明「真机安装需每次先申请」）。
- 流程（沿用已被验证的原地覆盖路径）：`am force-stop com.minibook` → 用 `run-as com.minibook tar` 取装前数据快照 →
  `adb -s A1B2C3D4 install -r app-debug.apk` → 再取装后快照 → 逐字节比对 → 启动与崩溃检查 → 比对手机内 `base.apk` 哈希。
- 证据：
  - 装前快照 `%USERPROFILE%\minibook-rollback-20261012\phone-data-before-20261013-0955.tar` 与
    装后快照 `phone-data-after-20261013-0955.tar`：均为 **10,849,280 字节**，sha256 均为
    `EF992D3710C00F980CB87E4CC39E62B25F90996C833773C40FEA2D8A659AB620` ⇒ **数据逐字节未变**
    （含加密 DB / WAL / SHM、DataStore、`no_backup/minibook_key.bin`、备份 JSON）。
  - `install -r` → **Success**；`versionCode=2` / `versionName=1.1.0` 不变；`firstInstallTime=2026-05-18 18:28:54` 未变；
    `lastUpdateTime` 2026-10-13 08:39:54 → **2026-10-13 09:53:12**；`READ_SMS` / `RECEIVE_SMS` / `POST_NOTIFICATIONS` 授权保持。
  - 启动：`mCurrentFocus=com.minibook/.MainActivity`；`logcat -b crash` **空**；主日志无 `FATAL` / `AndroidRuntime`。
  - **手机内 `/data/app/.../base.apk` 的 sha256 = 本地产物 sha256 = `3099F8193E815EC0A6837036E01FED08FDCF5B0D699FFC3F843555F2459647DC`**
    ⇒ 手机跑的确实是本轮通过 527 例测试的那个构建。
- 附带观察到的事实（用于让台账准确）：本轮快照里已出现 `files/datastore/sync.preferences_pb` 与 `files/sync_backup/*`
  （2026-10-13 上午），即**真机已登录云端账号且自动同步在跑**；因安装是原地覆盖、数据与 Keystore 密钥未被触碰，登录态与令牌原样保留。
- 未做：真机逐页走查（用户可能在用手机，装完只做了启动与数据完整性检查，未做任何点击操作）。

### 缺陷与观察

- **本轮抓到并修复的编译缺陷**：`LedgerIcons.kt`（新生成的矢量文件）首次编译报
  `e: …/ui/components/LedgerIcons.kt:317:33 Unresolved reference 'size'`（`modifier.size(size)` 缺
  `import androidx.compose.foundation.layout.size`）；补 import 后重跑通过。**该文件是生成物、首次参与编译，说明
  「生成代码也必须真编译一次」**——与 F-15o（编译绿仍运行时崩）是同一类教训的另一面。
- 登录页键盘行为（非缺陷）：用户名框 `imeAction = Next`，在用户名框按回车是**移到下一项**而非提交；提交需在口令框按回车或点按钮。
- 本轮未做：暗色逐页截图（本轮改动为图标/文案/删除语义，颜色 token 未动）；真机**逐页走查**（安装与启动验证已按用户当次批准完成，见上；未做点击走查）；`connectedDebugAndroidTest` 未重跑（无 Room schema 变更，`MigrationV3To4Test` 不受影响）。

## 上一轮测试（TASK-082：未登录态收为登录按钮 / 设置页「账号」提第一行 / 内置分类换自带矢量图标集，2026-10-12）

### 测试信息

- 本轮改动：
  1. **未登录态收为登录按钮**（用户否决上一版资料头卡，原话「用户未登录时候不要展示一个大框，做成一个让用户登录的按钮即可」）：
     `SettingsAccountScreen.kt` 的 `GuestLoginCard` 换成 `GuestLoginButtons`——满宽「登录」主按钮（52dp）+ 「还没有账号？注册新账号」文字链。
  2. **设置页「账号」提第一行**：`SettingsScreen.kt` 把「账号」行移到「账本」之前。
  3. **内置分类图标换自带矢量集**：新增 `ui/components/CategoryIcons.kt`（24 个 Material Symbols Rounded 路径，
     稳定 key + `LEGACY_EMOJI` 兜底）；`BuiltInCategories` 24 条模板 `icon` 由 emoji 换成 key；新增渲染入口
     `CategoryGlyph`（`CategoryComponents.kt`），9 处渲染点统一改走它（分类格 / 交易列表行 `ListItems.kt` /
     记账分类下拉 / 交易详情 / 预算卡 / 预算编辑 / 搜索筛选）；分类编辑器的「手输 Emoji」换成 6×4 图标网格。
     口径：`Category.icon` 仍存 String、**不改 Room schema、不做数据迁移**，历史 emoji 原样保留并兜底渲染。
  4. 新增测试：`CategoryIconsTest` 3 例。
- 环境：Pixel_7 模拟器（emulator-5554，时区 **GMT**，`-no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`）；
  真机 `A1B2C3D4` 在本轮回归中未触碰（所有 adb 命令均带 `-s emulator-5554`；真机安装与启动验证另见下方 2026-10-13 章节）。

### 结果总览

| 项 | 结果 |
|---|---|
| Android JVM `:app:testDebugUnitTest` | **521 例 / 0 失败 / 1 跳过**（suites=67；518 → 521 新增 `CategoryIconsTest` 3 例；唯一跳过项为需真实服务端的 `SyncApiE2ETest`） |
| Android `:app:assembleDebug` | **BUILD SUCCESSFUL**；产物 `app/build/outputs/apk/debug/app-debug.apk` **39,090,923 字节**（2026-10-12 17:36），sha256 `456A17C005197308FBE6AF8FFDBC941B3A456E2C0CD4F84723871B2A15A5C5D1` |
| 模拟器浅色回归 | 设置页账号第一行 / 账号页未登录态（只有登录按钮 + 文字链）/ 分类 tab 24 个新图标 / 分类编辑器 6×4 网格 + 选中态 / 交易列表行图标（历史 emoji 兜底路径）/ 分类筛选下拉 全部通过 |
| 模拟器暗色回归 | 分类 tab 24 个图标在暗色 tonal 面上全部可读、选中 pill 可见 |
| 服务端 | 本轮未改服务端代码（仍为 `pytest` 双 DSN 各 73 例 / 0 失败，见下节） |

### 模拟器实测细节（uiautomator + 截屏，分辨率 1080×2400）

- **设置页**：首行就是「账号 / 未登录 · 点此登录云端账号」（y=518），其后 账本 / 外观 / 安全 / 数据 / 短信账单 / 关于。
- **账号页（未登录）**：整页只有「登录」按钮 + 「还没有账号？注册新账号」文字链 + 隐私说明——**没有资料头卡、没有头像**（uiautomator 文字清单仅这三条）。
- **分类 tab**：支出 16 + 收入 8 全部渲染为新矢量图标（餐饮叉勺 / 交通汽车 / 购物袋 / 娱乐场记板 / 住房 / 医疗 / 教育 / 服饰 / 通讯 / 人情 / 旅行 / 美团 / 转账 / 缴费 / 付款 / 其他 / 工资 / 奖金 / 投资 / 兼职 / 礼金 / 收款 / 退款 / 其他），每个图标按分类色着色在淡色圆底上。
- **分类编辑器**：点「餐饮」打开底部表单，「图标」区是 **6 列 × 4 行共 24 个圆钮**；点第 2 个（汽车）→ 该钮变 `primaryContainer` 底 + primary 描边，其余保持灰底（选中态成立）。老数据的 emoji 不进网格高亮（属预期：不点就不改）。
- **交易列表行**（`ListItems.kt` 修复点）：记账页 2026-09-15 ¥88 餐饮一条，分类圆底内是**矢量叉勺**而不是 emoji——即 DB 里仍是旧 emoji `🍜`、经 `LEGACY_EMOJI` 兜底渲染，真实端到端路径成立。
- **分类筛选下拉**：每行「图标 + 分类名」横向对齐，无 emoji 残留。
- **暗色**：分类 tab 图标与文字在 `surfaceContainer` 系色面上可读，未出现纯黑底眩光。
- 过程坑：swiftshader 下冷启动又弹 `Application Not Responding: com.android.systemui`（点 Wait / `force-stop` + `am start` 恢复）；
  `cmd uimode night yes/no` 后当前页仍是二级页，需先 `keyevent 4` 回主界面再点底部 tab（否则点击落在二级页上、表现为"点不动"）。
- 截图存档（gitignored）：`build-verify-home-icons.png`、`build-verify-categories-icons.png`、`build-verify-category-editor.png`、
  `build-verify-icon-selected.png`、`build-verify-list-row-icon.png`、`build-verify-settings-first-row.png`、
  `build-verify-account-guest2.png`、`build-verify-categories-dark.png`、`build-verify-category-dropdown.png`。
- **未覆盖**：搜索筛选 Chip 图标（只验了记账页筛选下拉）。预算页 / 预算编辑与真机 `A1B2C3D4` 已在 2026-10-13 真机安装轮补验（见下方「真机安装与启动验证」章节）。

### 真机安装与启动验证（2026-10-13，用户当次批准「给我手机装上」）

- 授权：用户原话「给我手机装上」（仅此一次授权，遵循"每次安装先申请"）。
- **装前快照**：`am force-stop com.minibook` 后 `adb -s A1B2C3D4 exec-out run-as com.minibook tar cf - .` → `%USERPROFILE%\minibook-rollback-20261012\phone-data-before-20261013.tar` = **7,434,240 字节**，sha256 **`2CF95C5B7F73282579BFB6F0F0418DD4E820C208DF4B773D3B2446373F08E18D`**。
- **安装**：`adb -s A1B2C3D4 install -r app/build/outputs/apk/debug/app-debug.apk` → **Success**（同 Android Debug 签名 + 同 applicationId ⇒ 原地覆盖）。
- **装后快照**：`phone-data-after-20261013.tar` = **7,434,240 字节**，sha256 **`2CF95C5B…E18D`，与装前逐字节一致** ⇒ 本地数据未丢、无迁移改写。
- **包信息**：`versionCode=2 / versionName=1.1.0`；`firstInstallTime=2026-05-18 18:28:54` 未变；`lastUpdateTime` 2026-10-12 16:42:04 → **2026-10-13 08:39:54**；权限保持（`READ_SMS` / `RECEIVE_SMS` / `POST_NOTIFICATIONS` = granted）。
- **启动**：`am start -n com.minibook/.MainActivity` → `mCurrentFocus=com.minibook/.MainActivity`，进程 pid 8154 全程存活（无重启）；`logcat -b crash` 与主缓冲 `FATAL / AndroidRuntime` **均无 com.minibook 记录**。
- **新图标真机确认**：用户真机上正停留在「预算 → 编辑分类预算」表单（非本次导航所致）时截屏可见「美团」分类行与下拉均渲染为**新矢量图标（外卖盒）**而非 emoji ⇒ `CategoryGlyph` 在真机路径成立。该截屏含个人金额，已按要求删除、不留存、不引用数值。
- **过程如实说明**：本次对真机仅执行「快照 → 安装 → 快照 → 启动 → 只读检查」；一次点击坐标按模拟器分辨率（1080×2400）估算、在真机（1200×2608）上落在手势区、未命中任何控件；随后发现用户正在使用手机（焦点已变为微信相册预览），**立即停止一切真机操作**——未再点击、未按「保存/取消」、编辑分类预算表单保持原样。
- **未做**：真机 UI 逐页走查（用户正在使用手机，且需避免截取含个人账目的界面）。

## 最新测试（TASK-081 / TASK-076 部分：Hero 卡环比 + 法务文本与注册勾选，2026-10-12）

### 测试信息

- 本轮改动：
  1. **首页 Hero 卡环比**（TASK-081 / 决策 G）：`ui/components/Cards.kt` 新增 `NetBalanceTrend` + `NetTrendLine`
     （结余下方「较上月 +¥X / ¥-X / 与上月持平 / 上月无记录」），`HomeViewModel` 新增
     `previousBalanceOrNull` 纯函数（上月收支全 0 ⇒ null =「上月无记录」），`HomeScreen` 透传；
     **只给可核对的绝对差额，不给百分比**（上月为 0 时百分比无定义）；涨跌符号 + 语义色双编码。
  2. **法务文本与注册勾选**（TASK-076 部分完成）：`res/raw/privacy_policy.txt`（11 节）/ `terms_of_service.txt`
     （10 节）为**界面与上架材料同源的唯一副本**；新增 `LegalScreen` + 设置 →「关于」两行入口
     （设置 → 关于 → 隐私政策 / 用户协议，2 跳可达）；注册页新增**默认不勾选**协议勾选框，
     未勾选时本地拦下（`SyncPresenter.agreementError`）并页面内直说原因；登录页只放「登录即表示同意」链接行。
  3. 新增测试：`NetBalanceTrendTest` 5 例 + `HomeViewModelTrendTest` 3 例 + `LegalTextTest` 3 例 +
     `SyncPresenterTest` 协议 1 例。
- 环境：Pixel_7 模拟器（emulator-5554，时区 **GMT**，`-no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`）；
  真机 `A1B2C3D4` 本轮**未触碰**（所有 adb 命令均带 `-s emulator-5554`）。

### 结果总览

| 项 | 结果 |
|---|---|
| Android JVM `:app:testDebugUnitTest` | **518 例 / 0 失败 / 0 跳过**（suites=66；506 → 518 新增 12 例：Hero 环比 8 + 协议 1 + 法务守卫 3） |
| Android `:app:assembleDebug` | **BUILD SUCCESSFUL**；产物 `app/build/outputs/apk/debug/app-debug.apk` 38,788,074 字节（2026-10-12 16:03），sha256 `AEEEB4AD…951E` |
| 模拟器浅色回归 | 首页 Hero 卡（含环比行）/ 设置「关于」/ 隐私政策页 / 用户协议页 / 注册页勾选全部通过 |
| 模拟器暗色回归 | 首页通过：环比行「较上月 +¥88.00」在暗色 tonal 面上可读（收入色 `0xFF4ADE80`） |
| 服务端 | 本轮未改服务端代码（仍为 `pytest` 双 DSN 各 73 例 / 0 失败，见下节） |

### 模拟器实测细节（uiautomator，分辨率 1080×2400）

- **首页 Hero 卡两态**：无上月记录时显示「上月无记录」；在模拟器上通过 UI 造数（9 月 15 日一笔 ¥88 支出）后，
  首页变为「较上月 + ¥88.00」（9 月结余 -88 元 → 10 月 0 元，差额 +88 逻辑正确），收入色 + 「+」号双编码可见。
- **设置页**：新增「关于」组（隐私政策 / 用户协议两行 + 副标题文案），页脚 `MiniBook v1.1.0`。
- **隐私政策 / 用户协议页**：完整正文渲染（11 节 / 10 节），标题「隐私政策」「用户协议」，含
  「运营主体与联系方式…尚未声明…请勿用于正式对外发布」。
- **注册页**：勾选框**默认未勾选**（`checked="false"`）；未勾选点「注册并登录」→ 页面内错误
  「请先阅读并勾选同意《用户协议》和《隐私政策》」且**不发网络请求**；「两次口令不一致」精确挂在
  确认口令字段（红框 + 字段级文案），页面级不重复显示；点《用户协议》/《隐私政策》链接跳转正文页并可返回。
- 过程坑：swiftshader 下冷启动多次弹 `Application Not Responding: com.minibook`（内存紧张，FreeMB≈770），
  点「Wait」+ `force-stop`/`am start` 恢复；`uiautomator dump` 偶发 `null root node`（重试即可）；
  布局随错误文案出现而下移，链接坐标需以最新 dump 为准。
- 截图存档（gitignored）：`build-verify-hero-light.png`、`build-verify-hero-delta-light.png`、
  `build-verify-hero-dark.png`、`build-verify-privacy-light.png`、`build-verify-register-consent2.png`。

### 缺陷与观察

- **构建期坑（已修，非运行时缺陷）**：`LegalScreen.kt` KDoc 曾写 `` `res/raw/*.txt` ``，其中 `/*` 被 Kotlin
  当作**可嵌套块注释**的开始符 ⇒ `Unclosed comment` 编译失败（`LegalScreen.kt:71`，连带 `MiniBookNavHost`
  Unresolved reference）。改为「`res/raw` 目录下的纯文本」表述；已 grep `/\*\.|raw/\*` 确认 `app/src` 无同类残留。
- **单测期坑（已修）**：`SyncPresenterTest` 新例把 `assertTrue(reason != null && …)` 与后续 `assertTrue(reason.contains(…))`
  拆成两句，第二句对 `String?` 智能转换不生效 ⇒ 编译失败；合并为单个 `assertTrue(… && … && …)`。
- 观察（非缺陷）：模拟器强调色仍随壁纸动态取色（既有行为）；「上月无记录」口径＝上月收支全 0，不显示环比。
- 未做：真机 `A1B2C3D4` 验收（用户要求先不动手机）；首启隐私弹窗（决策 D 未拍板，缺口见 `docs/17` §5.2）；
  运营主体 / 联系方式 / 注册地（用户 2026-10-12 明确暂不声明，上架前必须补）；`SmsNotificationHelper.kt:72/:94`
  通知小图标仍是 `R.mipmap.ic_launcher`（既有代码，未改）。

## 最新测试（TASK-075 / 078 / 079：认证页交互规范化 + 视觉现代化，2026-10-12）

### 测试信息

- 本轮改动（对应 `16_auth_ux_and_visual_plan.md` §6 与 TASK-075/078/079）：
  1. **主题 token**：`ui/theme/Color.kt` 重写（青绿基线 + M3 分层表面族 + 静态语义色）、`Theme.kt`
     （形状 6/12/16/20/28dp、按背景亮度计算 `on*` 色）、`Type.kt`（新增 `MoneyLarge` / `MoneyMedium`）；
  2. **图标层**：新增 `ui/components/MiniBookIcons.kt`（18 个自带矢量图标，路径取自 Material Icons /
     Apache-2.0），替换底部导航 emoji、搜索 🔍、账本行 ✏️/🗑、返回 ←、月切换 ‹›、FAB ＋、快捷入口图标；
     新增 `MiniBookIconsTest` 遍历全部图标（矢量路径写错只在运行时崩，参见 F-15o）；
  3. **面板分层**：`surfaceVariant.copy(alpha = …)` 的 8 处面板与 3 处显式 `surface` 卡片改
     `surfaceContainerLow / High / Highest`（Chip、进度/环图轨道等语义用色刻意保留）；
  4. **认证页 UX**（TASK-075）：`AuthComponents.kt` 重写（字段级 `isError` + `supportingText`、
     `LiveRegion` 错误播报、trailing 眼睛图标、autofill `ContentType`、`KeyboardCapitalization.None` +
     关闭自动纠错、`imeAction` 链、`enabled = !busy`），`LoginScreen` / `RegisterScreen` 同步改造。
- 环境：Pixel_7 模拟器（emulator-5554，时区 **GMT**，`-no-snapshot-save -no-boot-anim -gpu
  swiftshader_indirect`）；真机 `A1B2C3D4` 本轮**未触碰**（所有 adb 命令均带 `-s emulator-5554`）。

### 结果总览

| 项 | 结果 |
|---|---|
| Android JVM `:app:testDebugUnitTest` | **506 例 / 0 失败 / 0 跳过**（suites=63；504 → 506 为 `MiniBookIconsTest` 2 例） |
| Android `:app:assembleDebug` | **BUILD SUCCESSFUL**；产物 `app/build/outputs/apk/debug/app-debug.apk` 38,949,733 字节（2026-10-12 15:28），sha256 `E8AA9993…B83`；emoji→矢量图标收口后**重装模拟器复验**通过（无闪退） |
| 模拟器浅色逐页 | 首页 / 设置 / 账号 / 登录页全部通过（无闪退、无错版） |
| 模拟器暗色回归 | 首页通过：背景为 tonal surface（非纯黑）、净额卡高一档、底部导航独立色面、收入 `0xFF4ADE80` / 支出 `0xFF8A94` 可读、选中 pill 可见 |
| 服务端 | 本轮未改服务端代码（仍为 `pytest` 双 DSN 各 73 例 / 0 失败，见下节） |

### 模拟器实测细节（uiautomator，分辨率 1080×2400）

- **首页（浅色）**：底部导航为矢量图标（首页 / 记账 / 债务 / 分类 / 设置）+ 选中态 tonal pill；净额卡
  `surfaceContainerLow`、金额 28sp Semibold 等宽数字；空态圆底 `surfaceContainerHigh`；快捷入口（报表 /
  预算 / 导入账单）为矢量图标 + `surfaceContainerLow` 底。
- **设置页**：六组（账本 / 外观 / 安全 / 账号 / 数据 / 短信账单），账号行「未登录 · 点此登录云端账号」，
  **账号页无云同步行**（入口只在 设置 → 数据 → 云同步），页脚 `MiniBook v1.1.0`。
- **账号页**：用户信息（当前状态「未登录（本地数据不受影响，可继续离线记账）」）+ 登录 / 注册新账号 +
  隐私说明；标题「账号」带矢量返回箭头。
- **登录页**：矢量返回箭头、应用图标圆角底衬、「欢迎回来」、用户名框（辅助说明）、**口令框右侧眼睛图标**、
  调试版服务器地址 `http://127.0.0.1:8000`、满宽药丸「登录」按钮、「还没有账号？ 去注册」、隐私行 —— 逐项无缺陷。
- **暗色**：`cmd uimode night yes` 后首页分层成立（见上表）。
- 过程坑：swiftshader 下首次截图遇到 `Application Not Responding: com.android.systemui`，点「Wait」并重启
  Activity 后恢复；`mCurrentFocus` 确认为 `com.minibook/.MainActivity` 才继续。
- 截图存档（gitignored）：`build-verify-home-light.png`、`build-verify-settings-light.png`、
  `build-verify-account-light.png`、`build-verify-login-light.png`、`build-verify-login-dark.png`、
  `build-verify-home-dark.png`。

### 缺陷与观察

- **F-15o（本轮前序发现，已在模拟器复测确认修复）**：`painterResource(R.mipmap.ic_launcher)` 对自适应图标
  XML 抛 `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported ex. PNG,
  JPG, WEBP`（栈首帧 `AuthComponents.kt:68`）⇒ 点「注册新账号」当场闪退。**编译绿、JVM 全绿都抓不到**。
  已改矢量 `R.drawable.ic_launcher_foreground` + 圆角底衬；本轮同类风险由 `MiniBookIconsTest` 在单测层兜住。
- 观察（非缺陷）：模拟器上强调色呈淡紫而非新基线青绿 —— `dynamicColor = true` 在 API 31+ 生效（壁纸取色），
  静态青绿基线只在关闭动态取色 / API<31 / 自定义主题色时出现。
- 未做：真机 `A1B2C3D4` 验收（用户明确要求先不动手机）；`SmsNotificationHelper.kt:72/:94` 的通知小图标仍是
  `R.mipmap.ic_launcher`（既有代码，未改，属同类隐患）。

## 最新测试（TASK-074：登录 / 注册独立页 + 资料扩充 + 时间口径，2026-10-12）

### 测试信息

- 本轮改动（对应用户四项要求）：①「注册时间」显示不对 ②登录 / 注册各自独立成页 ③云同步入口去重
  ④用户资料扩充。服务端：`users.email/bio/region` + 迁移 `0004_user_profile_fields`、`UserOut` 输出
  **带 `Z` 的 UTC** 与 `last_login_at`、`PATCH /api/v1/me` 字段级更新；客户端：新增 `AuthViewModel` /
  `AuthComponents` / `LoginScreen` / `RegisterScreen` / `ProfileEditScreen`，重写 `AccountViewModel` 与
  `SettingsAccountScreen`，新增路由 `settings-login` / `settings-register` / `settings-profile-edit`。
- 环境：Pixel_7 模拟器（emulator-5554，**时区 GMT**）+ 真 MySQL 8（<内网主机IP>:49779）dev 库
  API（127.0.0.1:8000，adb reverse）。真机 `A1B2C3D4` 本轮**未触碰**（用户明确要求先不动手机）。

### 结果总览

- 服务端 pytest：SQLite **73 例 / 0 失败**；MySQL（minibook_test）**73 例 / 0 失败**（68→73，+5）；
  `alembic upgrade head` 已打到真库 `0004_user_profile_fields (head)`。
- Android JVM `:app:testDebugUnitTest`：**504 例 / 0 失败 / 0 跳过**（499→504，+5：时间格式化 3 例 +
  两次口令不一致 1 例 + 邮箱格式 1 例）；`:app:assembleDebug` 出包成功。
- 主机直连 E2E（真 MySQL dev 库，账号 `e2e_67470`）：register → login → me → PATCH 四项 → 只 PATCH
  `{bio:""}` → 非法邮箱 → 空 body，全通过：
  - `me.created_at=2026-10-12T06:04:04.215457Z`、`me.last_login_at=2026-10-12T06:04:04.607119Z`（**带 Z**）；
  - 字段级语义正确：只传 `{bio:""}` 时 `bio` 被清空，`email` / `region` / `nickname` **保持不变**；
  - `{"email":"nope"}` → **422**；`{}` → **400**（`nothing_to_update`）。

### 模拟器界面实测（uiautomator 驱动）

1. 设置 → 账号：未登录态显示「当前状态 / 未登录（本地数据不受影响，可继续离线记账）」+「登录」+
   「注册新账号」，**账号页已无云同步行** ✓（要求③的一半）；
2. 注册页渲染完整：标题「创建账号」+ 用户名 / 口令 / 确认口令 + 常驻规则提示（「3–32 位…」「至少 8 位」
   「再输入一次，避免打错」）+「注册并登录」+「已有账号？去登录」 ✓；
3. 注册 `emu1012a` / `emu-pass-1234` → **注册成功即自动登录**：账号页显示 8 项（显示名 / 用户名 /
   邮箱 / 个性签名 / 地区 / 注册时间 / 上次登录 / 账号 ID）✓（要求④）；
4. 时间口径（要求①）：账号页「注册时间」「上次登录」均显示 `2026-10-12 06:16`（`yyyy-MM-dd HH:mm`）。
   模拟器 `persist.sys.timezone=GMT`，故 06:16 是**正确的本地渲染**（等价于 `2026-10-12T06:16Z`）；
   真机在 Asia/Shanghai 会显示 14:16。修复前该位置是原样吐出的 UTC 串；
5. 编辑资料页：改邮箱 / 个性签名 / 地区 → 保存后自动返回，三项写入而显示名 / 用户名 / 注册时间 /
   上次登录 / 账号 ID 不变 ✓（「只提交改动过的字段」端到端成立）；
6. 设置 → 数据页：**唯一**一处「云同步 / 登录云端账号，手动上传 / 下载账目数据（本地数据不会丢）」✓
   （要求③的另一半：入口只剩这里）；
7. 账号页下半：账号操作（编辑资料 / 修改口令）+ 会话（退出登录 / 注销账号）；退出确认框文案
   「只是退出账号：本地账目一条不动；下次登录后仍可继续同步。」✓。

### 本轮发现的缺陷（只有运行时才暴露）

- **F-15o（已修复）**：点「注册新账号」App 直接闪退 ——
  `java.lang.IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported ex. PNG, JPG, WEBP`，
  栈首帧 `AuthComponents.kt:68`（`painterResource(R.mipmap.ic_launcher)`，而该资源是自适应图标 XML）。
  **编译绿 + JVM 504 例全绿都抓不到**，只有跑起来点得到；已改用矢量
  `R.drawable.ic_launcher_foreground` + 圆角底衬，重装后复测注册页正常。
- 同类隐患（未改）：`app/src/main/java/com/minibook/data/local/sms/SmsNotificationHelper.kt:72` 与 `:94`
  的通知小图标仍引用 `R.mipmap.ic_launcher`。

### 未覆盖 / 说明

- 登录页与注册页共用 `AuthPage` 组件；**两者均已实测正常**——登录页渲染「欢迎回来」+ 用户名 / 口令（带
  「显示口令」切换）+ 调试版服务器地址输入框 +「登录」+「还没有账号？去注册」+ 隐私说明。
- 真机 `A1B2C3D4` 本轮未安装新构建（用户明确要求先不动手机）→ **真机验收仍待用户许可**。
- 模拟器本轮两次因内存压力掉线（与之前几轮同因），重启后 App 与数据仍在。

## 上一轮测试（账号页 + 服务地址内置 + SyncApi 主线程读响应体修复，2026-10-12）

### 测试信息

- 本轮改动：`settings-account` 路由 + `AccountViewModel` / `SettingsAccountScreen`（登录 / 注册 / 用户信息 /
  修改资料 / 修改口令 / 退出登录 / 注销账号）；服务地址改 `BuildConfig.SYNC_BASE_URL` 内置（仅调试版可见输入框）；
  `SyncApi.callRaw` 读响应体收进 `withContext(ioDispatcher)`；服务端 `users.nickname` + `PATCH /api/v1/me` +
  `POST /api/v1/auth/delete-account`。
- 环境：Pixel_7 模拟器（emulator-5554）+ 真 MySQL 8（<内网主机IP>:49779）API（127.0.0.1:8000，adb reverse）。

### 结果总览

- 服务端 pytest：SQLite **68 例 / 0 失败**；MySQL（minibook_test）**68 例 / 0 失败**；`alembic upgrade head`
  已打到真库 `0003_user_nickname`（users.nickname 列存在）。
- Android JVM `:app:testDebugUnitTest`：**499 例 / 0 失败 / 0 跳过**（新增 10 例：显示名回退、改口令前置校验、
  改资料请求的 null 语义、响应缺字段降级、注销请求字段名；注册/登录本地校验与认证类 422 文案 4 例）。
- 模拟器 `:app:connectedDebugAndroidTest`：**21 例 / 0 失败 / 0 跳过**。
- 主机直连新端点 7 步：register → login → me → PATCH 昵称 → PATCH 空串清除 → delete-account → 删除后 me 401，全通过。

### 模拟器界面实测（uiautomator 驱动）

1. 设置 → 「账号」（副标题「未登录 · 点此登录云端账号」）→ 二级页顶栏标题「**账号**」（与入口同名）✓；
2. 注册一次性账号 `e2e1012z` → 登录成功 → 用户信息显示显示名 / 用户名 / 注册时间 ✓；
3. 「修改资料」改显示名为 `E2ENick` → 保存后显示名变为 E2ENick ✓；
4. 「修改口令」（sup3r-secret → sup3r-secret2）→ 退出登录（确认框）→ 用**新口令**重新登录成功且显示名仍为 E2ENick ✓；
5. 「注销账号」：口令二次确认 → 确认后回到未登录态 ✓；MySQL 中该账号整行消失（含 6 表 + sync_log），
   再次用旧令牌 me → 401 ✓；
6. `demo_user_b`（用户真实数据的云端副本：若干账本 / 若干交易 / 若干 sync_log）**全程未被触碰** ✓。

### 本轮发现并修复的缺陷

- `SyncApi.callRaw` 把 `resp.body?.string()` 放在 `withContext(ioDispatcher)` 外面：小响应侥幸进缓冲区、
  大响应在调用方（主）线程真读 socket ⇒ 账号页点「登录」报 `NetworkOnMainThreadException`
  （自动同步从未暴露，因为 `SyncUseCase` 整段已在 IO）。修复：`execute()` 与读 body 一起收进 IO 上下文。
- **「注册失败」提示不准**（真机实测）：服务端 `RegisterRequest` 要求口令 ≥ 8 位、用户名 3–32 位限
  `[A-Za-z0-9_.-]`，客户端原先不本地校验，服务端 422 又复用成同步文案「数据校验失败，未写入云端」，
  用户用 6 位口令注册只看到这句话 ⇒ 无法自查。修复：注册/登录本地前置校验（不合规不发请求、直说
  「口令至少 8 位」）、表单常驻规则提示、认证类 422 文案改为说清规则。

### 仍未验证（如实登记）

- 正式版（release）`SYNC_BASE_URL` 仍为空占位：**待部署域名确定后填入并用 release 包验证一次**；
- 真机 `A1B2C3D4` 验收待用户提供设备（本次全部在模拟器执行）；
- 「仅调试版可见」的地址输入框在 release 中的隐藏行为由 `BuildConfig.DEBUG` 保证，未用 release 包实测。

## 最新测试（F-15：真 MySQL 8 双 DSN 全绿 + Android 网络栈 30 例 + 同步核心 20 例，2026-10-10）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-10（F-15 服务端 MySQL 联调轮 + Android 网络栈 TASK-065） |
| 被测对象 | ① `server/`（FastAPI + SQLAlchemy 2 + Alembic，用户系统 + 同步接口）② Android `data/remote/`（OkHttp 网络栈与令牌编排） |
| 运行环境 | ① Windows 本机 Python 3.12.0 + `server/.venv`；② 同机 JDK 21 + Gradle（`JAVA_HOME=D:\soft\jdk\jdk21`） |
| 数据库 | **内网 MySQL 8.0.46**（`<内网主机IP>:49779`，docker `mysql:8.0`，utf8mb4 / utf8mb4_unicode_ci，`default-time-zone=+08:00`）+ **SQLite**（同一套用例再跑一遍） |
| 执行方式 | 主代理单点串行（规则 3）；`pytest -q --junitxml=…` 与 `gradlew :app:testDebugUnitTest` |
| 设计与差分 | `docs/14_cloud_sync.md` §7 验收 A1~A11、**§12 实现状态与差分** |

### 结果总览

| 指标 | 数值 |
|------|------|
| pytest @ **MySQL**（`minibook_test`） | **52 例 / 0 失败 / 0 错误 / 0 跳过**，`exit=0`，junitxml `tests="52" errors="0" failures="0" skipped="0"`（含 TASK-064 新增 3 例日志隐私） |
| pytest @ **SQLite** | **52 例 / 0 失败 / 0 错误 / 0 跳过**，`exit=0`（同一套用例，双 DSN 双绿） |
| Android JVM 单测（`com.minibook.data.remote.*`） | **30 例 / 0 失败**：`SyncDtosTest` 7、`SyncErrorsTest` 5、`SyncUrlValidatorTest` 6、`SyncAuthStoreTest` 6、`SyncTokenAuthenticatorTest` 6（`BUILD SUCCESSFUL`，逐类核验 junit XML） |
| Android JVM 单测（同步核心 TASK-066） | **20 例 / 0 失败 / 0 跳过**：`SyncPlanTest` 19（三模式、三路比较、LWW 双向、墓碑差分、本地已删不复活、仅下载不删本地、跨类型计数）+ `SyncPayloadContractTest` 1（**未跳过** ⇒ 真的解析了 `server/app/entity_spec.py`，6 类字段集与实体序列化器键集**逐字一致**） |
| Android JVM 单测（同步 UI TASK-067） | **21 例 / 0 失败**：`SyncPresenterTest` 覆盖预览四计数、危险确认判定与文案、三模式明细裁剪、结果卡（成功/失败/可重试）、状态行四态、冲突口径映射、时间格式化回退、短 id 与快照名边界 |
| **真实服务端端到端（无设备，新增）** | `SyncApiE2ETest` **1 例 / 0 失败 / 3.05s**（**未跳过**）：生产 `SyncApi` 真 OkHttp → 真 API → 真 MySQL。注册→登录→`me`→推 3 类实体→拉回（中文+emoji 逐字一致、UTF-8 44 字节相等）→重复推送 `applied=0` 且 `server_seq` 不前进→墓碑下行→`status` 计数→`refresh` 轮换→旧 refresh token 被拒→坏令牌归 `TOKEN_INVALID`。默认由 `app/build/e2e-base-url.txt` 开关控制（未配置如实 skipped） |
| MySQL 侧独立核验（同上 E2E 落库结果） | `sync_records` 3 条（ledger/category/transaction）、1 条墓碑、`updated_at_raw` 与客户端原样逐字一致（`2026-10-10T08:30:00.000000000Z`）、墓碑 `payload` 为 **JSON literal null**（`JSON_TYPE='NULL'`、`payload = CAST('null' AS JSON)`，旧描述不残留）；核验后测试数据已清理（users/records/tokens/devices 全为 0） |
| 全量 JVM 单测回归（`:app:testDebugUnitTest`） | **481 例 / 0 失败 / 0 错误 / 0 跳过**（60 套件）：含既有 410 例全部复跑通过 ⇒ 「6 张实体加 `@Serializable` + 可空字段补 `= null`」与新增 UI 层**未碰坏任何既有行为**（E2E 用例需开关文件，默认如实 skipped，不计入本行） |
| Alembic @ MySQL（真连库） | `alembic upgrade head` → `0001_init (head)`，建 `users`/`refresh_tokens`/`devices`/`sync_records`/`sync_counters` + `alembic_version`；**六张表全部 `InnoDB` + `utf8mb4_unicode_ci`**；`downgrade base ⇄ upgrade head` 双向真跑通过；`sync_counters` 首行 `server_seq=0` |
| 端到端 HTTP 冒烟（uvicorn 真进程 → 真 MySQL） | `healthz` 报 `db=mysql`；注册 201 → 登录（`expires_in=1800`，access 243 字符）→ `push` `applied=1 server_seq=1` → `pull` 取回 1 条 → `status` `counts={"ledger":1}`；**中文 23 字符 / 44 UTF-8 字节逐字一致**（`equal=True`） |
| 本轮实测修复的 4 个真缺陷 | ① `alembic/env.py` 把 URL 回写 configparser → 口令 URL 编码含 `%` 时 `ValueError: invalid interpolation syntax`，`alembic upgrade` 直接失败；② MySQL 8 对 `CHARSET=utf8mb4` 默认给 `utf8mb4_0900_ai_ci`，与部署口径不一致；③ `downgrade` 先 `DROP INDEX` 撞外键支撑索引（errno 1553）→ 中途失败留下半删库；④ Android `hasTokens` 只看密文非空，Keystore key 失效时误报「已登录」；⑤ `://x` 类畸形地址漏过校验（另修 health 测试硬编码 `db == "sqlite"`） |

> **DEC-037 修订（2026-10-10，晚于本表）**：上表是**改版前**的记录。云端存储已由「`sync_records` + JSON payload」
> 改为 **6 张真实业务表 + `sync_log`**：`pytest` 双 DSN 各 **61 例 / 0 失败**；真实开发库 `0001_init ⇄ 0002_typed_entities`
> 往返通过（5 行旧数据全部拆列、0 行跳过）；云端 SQL 聚合可用；模拟器设备端 `connectedDebugAndroidTest`
> **18 例 / 0 跳过 / 0 失败**。权威口径见 `14_cloud_sync.md §3.3/§13`、`07_decisions.md`（DEC-037）、`CHANGELOG.md`。

### 未验证（不得声称已验证）

1. ~~**服务端日志不含 payload 的自动化断言**（TASK-064）~~ → **已闭合（2026-10-10）**：`server/tests/test_log_privacy.py` 3 例，
   覆盖 push / pull / 整批校验失败三条路径，断言日志文本中不出现备注哨兵、金额哨兵，以及 `"payload"` / `"description"`
   这类字段名（字段名出现即等价于整个 body 被打印）；SQLite 与 MySQL 双 DSN 各 **52 例 / 0 失败**。
2. **真机 / 局域网端到端（TASK-068 剩余）** 未做——需设备（真机装着用户真实数据的正式版，签名不同，
   装 debug 包会要求卸载）。客户端↔服务端**真实 HTTP** 端到端已覆盖（见上表 `SyncApiE2ETest`）。
3. **`MigrationV3To4Test` 已写但未运行**（instrumented 需真机/模拟器；真机 `A1B2C3D4` 装着用户真实数据的正式版，
   装 debug 测试 APK 会因签名不同要求卸载 ⇒ 不得在真机跑，需先获批启模拟器）。当前只以 `4.json` 静态比对替代。
3. **真机联调（TASK-068）未做**：真机 `A1B2C3D4` → 本机 API 的首次上传 / 仅下载对账 / 冲突两端一致 / 断网中断重试，均待执行。
4. **多进程与并发竞态**：登录限流为单进程内存实现；两端同时 push 同一条尚不存在的记录时，后到者可能收到 `server_error`（重试即幂等通过）。
5. Android 侧 Keystore 真机行为未测（JVM 用 `FakeSecretCipher` 替代；「落盘非明文」已用真 DataStore 文件断言）。

## 历史测试（F-15 服务端 API 首轮：pytest 47 例全绿 @SQLite，2026-10-10）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-10（F-15 服务端开发轮） |
| 被测对象 | `server/`（FastAPI + SQLAlchemy 2 + Alembic，F-15 云端同步服务端：用户系统 + 同步接口） |
| 运行环境 | Windows 本机，**Python 3.12.0** + `server/.venv`（fastapi 0.143.0 / SQLAlchemy 2.1.4 / alembic 1.20.0 / PyMySQL 1.2.3 / PyJWT 2.15.1 / argon2-cffi 25.1.0 / pytest 9.1.1） |
| 数据库 | **SQLite**（每个用例一个临时库，零依赖）；**MySQL 未跑**（无环境，TASK-069） |
| 执行方式 | 主代理单点串行（规则 3）：`server\.venv\Scripts\python.exe -m pytest -q`，退出码 0 |
| 设计与差分 | `docs/14_cloud_sync.md`（§3 设计 / §7 验收 A1~A11 / **§12 实现状态与差分**） |

### 结果总览

| 指标 | 数值 |
|------|------|
| pytest | **47 例 / 0 失败 / 0 错误 / 0 跳过**（exit code `0`） |
| 用例分布 | `test_auth` 11（含用户名大小写归一化、限流、刷新轮换与撤销、过期 access、argon2 非明文断言）；`test_isolation` 4（越权/伪造头/计数隔离）；`test_sync_push` 10（白名单、整批回滚、信封一致性、墓碑、批量上限、中文备注往返）；`test_sync_pull` 5（增量游标、分页无重无漏、limit 钳制、墓碑下发、未授权）；`test_idempotency` 3；`test_conflict` 4；`test_migration` 3；`test_timeutil` 5；`test_health` 2 |
| Alembic | `alembic upgrade head` 在 SQLite **真建表通过**（`users`/`refresh_tokens`/`devices`/`sync_records`/`sync_counters` + `server_seq=0` 首行，`alembic_version=0001_init`）；MySQL 侧以 `alembic upgrade head --sql` **离线渲染**核验：`DATETIME(6)` / `JSON` / `BIGINT` / `VARCHAR(32|36|64)` / 复合主键 `(user_id, entity_type, entity_id)` / `ENGINE=InnoDB CHARSET=utf8mb4` 均在 DDL 中 |
| 首轮失败与修复 | 首跑 20 例 401 —— 根因：JWT `iat/exp` 用 **naive** UTC 调 `.timestamp()`，被按本地时区解释，`exp` 少算 8 小时 ⇒ 新签发令牌立即 `token_expired`。改为 aware UTC 后全绿（已写入 `14_cloud_sync.md §12.2`）。另修 `alembic.ini` 中文注释导致 configparser `UnicodeDecodeError`（Windows cp936） |

### 未验证（不得声称已验证）

1. **MySQL 上跑同一套测试**：`TEST_DATABASE_URL` 通路（含「库名必须含 `test`」护栏）已就绪，但**未执行**——本机无 MySQL（TASK-069）。
2. **日志不含 payload 的自动化断言**：实现上只记 `user_id`/计数/错误码，但缺「日志文本检索不到金额/备注样例」的自动化断言（TASK-064 未闭合项）。
3. Android 客户端（TASK-065~067）、真机端到端（TASK-068）未开始。

## 最新测试（F-14 增量 / 全量两种扫描方式 + 白名单空态，2026-10-10 13:2x）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-10（13:2x） |
| 授权依据 | 用户指令「插上了，真机测试把」+ 本轮反馈（两种扫描方式原话见 `13_sms_bill_import.md §16` / DEC-030） |
| 真机环境 | 小米 `某机型`，Android 16 / SDK 36（HyperOS），adb `A1B2C3D4`，1200×2608 |
| 执行方式 | 主代理单点串行执行（规则 3）：`install -r -g` + `pm grant` → 冷启 → `uiautomator dump` 定位 → `input tap` → `screencap` 截图 + 节点文本比对 |
| 详细记录 | `docs/13_sms_bill_import.md §16`（逐端证据与截图清单）、`docs/07_decisions.md` DEC-030 |

### 结果总览

| 指标 | 数值 |
|------|------|
| JVM 单测（本轮） | **52 类 / 410 例 / 0 失败 / 0 错误 / 0 跳过**（F-14 占 7 类 / **77** 例；本轮 `SmsImportPresenterTest` +2 例） |
| `:app:assembleDebug` | **绿**（`BUILD SUCCESSFUL in 53s`） |
| ① 增量扫描（真机） | **通过**：`扫描 0 条` —— 那条已看过的快递短信**完全不出现**（既非折叠也非灰显），正是用户第三次反馈的核心诉求（首轮证据截图里说明行还是旧文案 `增量扫描：只看 05-07 15:15 之后的短信`，该文案已在 13:4x 节点修正中替换，见下 ⑤） |
| ② 全量扫描（真机） | **通过**：`扫描 1 条 = 可导入 0 + 未识别 1 + 非账单 0 + 已存在/重复 0` + `全量扫描：列出全部历史短信` + `▸ 此前已看过 1 条（点击展开）` |
| ③ 入口卡两按钮（真机） | **通过**：短信卡 = `增量扫描`（主）+ `全量扫描`（次）+ 一行小字「增量＝只看上次扫描之后的新短信；全量＝全部历史（看过的折叠在下面）。」 |
| ④ 白名单空态（真机） | **通过**：`白名单为空：对某个发送方手动确认入账 3 次（期间无纠错）后自动加入；…`，且**不再出现「清空白名单」按钮**（用户提问「白名单在哪看，就只有清空」） |
| ⑤ 时间节点 = 扫描时刻（真机，节点修正后复验） | **通过**：进导入页 → L3 补扫把节点写为 `13:47`（墙钟）→ 增量 `本次覆盖 10-10 13:47 之后的新短信，起点已更新为 10-10 13:48` ✓；**再跑一次全量 → 节点不动**；随后增量 `本次覆盖 10-10 13:48 …，起点已更新为 10-10 13:49` ⇒ 覆盖起点 = 上次**扫描**时间（既非短信时间、也非全量时刻）✓ |
| ⑥ 增量「只读新到达的」（模拟器，新构建复跑） | **通过**（`emulator-5554`，App 保持打开）：增量 #1 → `扫描 0 条` + `本次覆盖 10-10 05:35 …，起点已更新为 10-10 05:51`（节点从旧值推进到本次扫描时刻）；注入短信 C → 增量 → `扫描 1 条 = 可导入 1` + `本次覆盖 10-10 05:51 …，起点已更新为 10-10 05:52` ⇒ **只读到新到达的 C，早先的 A/B 不重读**（严格 `date > 节点` 成立）✓ |
| ⑦ 增量首次无节点 | 修正前在模拟器上实测过旧行为（**每次增量都提示「首次使用已建立水位基线」**——空库立不起节点，正是本次修正要修的毛病）；修正后该分支文案由**单测**锁定（`增量扫描：首次使用，起点已设为本次扫描时间 …`），真机/模拟器上节点均已存在，未再触发 |
| 未覆盖（如实挂账） | 白名单**非空态**（新增的「白名单（N 个发送方…）」标题行 + 清空按钮）本轮未实测：真机白名单为空，模拟器 AVD 在重启后数据被重置（白名单亦空）。该分支改动 = 一行 `HintText` + 原 `forEach` 逐项开关不变 + 清空按钮移入 `else`，编译与空态路径均已验证；真实非空态待下次有发送方晋级后目视确认 |

### 单测增量明细（+2 例，`SmsImportPresenterTest`）

| 用例 | 断言要点 |
|------|----------|
| `扫描方式说明行：全量与增量（时间节点 = 扫描时刻而非短信时间）` | 全量恒为 `全量扫描：列出全部历史短信`（与节点无关）；增量常规 = `增量扫描：本次覆盖 10-10 12:00 之后的新短信，起点已更新为 10-10 12:30`（本地时区格式化）；增量首次（`sinceMs=0, scannedAtMs=12:30`）= `增量扫描：首次使用，起点已设为本次扫描时间 10-10 12:30（历史短信请用「全量扫描」）`；拿不到扫描时刻时退回 `增量扫描：只看 10-10 12:00 之后的新短信`（不显示 1970 年） |
| `预览携带扫描方式与时间节点：计数闭合不受影响` | `SmsScanPreview` 默认 `FULL / 0L / 0L`；`copy(scanMode = INCREMENTAL, sinceMs = …, scannedAtMs = …)` 后 `assertClosed()` 仍成立、`scanTotal` 与 `unrecognized` 不变（扫描方式与时间节点只是元信息） |

## 最新测试（F-14 验收后 UI 改版：「已看过」标记 / 导入页三入口 / 设置分组汇总，2026-10-10 11:00–11:25）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-10（11:00–11:25） |
| 授权依据 | 用户三点要求（原话见 `13_sms_bill_import.md §15` / DEC-029）+ 既有真机测试授权 |
| 真机环境 | 小米 `某机型`，Android 16 / SDK 36（HyperOS），adb `A1B2C3D4`，1200×2608 |
| 模拟器环境 | AOSP `Pixel_7`，Android 15 / API 35，`emulator-5554`，1080×2400 |
| 执行方式 | 主代理单点串行执行（规则 3）：`install -r -g` + `pm grant` → `uiautomator dump` 定位 → `input tap` → `screencap` 截图 + 节点文本比对 |
| 详细记录 | `docs/13_sms_bill_import.md §15`（含逐端证据与截图清单）、`docs/07_decisions.md` DEC-029 |

### 结果总览

| 指标 | 数值 |
|------|------|
| JVM 单测（本轮） | **52 类 / 408 例 / 0 失败 / 0 错误 / 0 跳过**（F-14 占 7 类 / **75** 例；本轮 `SmsImportPresenterTest` +2 例） |
| `:app:assembleDebug` | **绿**（`BUILD SUCCESSFUL in 2m 48s`） |
| ① 已看过标记（模拟器） | **通过**：第 1 次扫描「未识别 1」在主列表；第 2 次扫描 → 主列表清空 + `▸ 此前已看过 1 条（点击展开）`；计数两次完全一致（`扫描 17 条 = 可导入 0 + 未识别 1 + 非账单 3 + 已存在/重复 13`） |
| ① 已看过标记（真机） | **通过**：第 1 次扫描显「⚠ 未识别（发送方未登记模板）：您好,您京东到了一个件…」；第 2 次折叠为「此前已看过 1 条」（用户投诉场景复现并消除） |
| ② 导入页三入口（两端） | **通过**：模拟器/真机均为「选择导入方式 + 账单文件导入 / 邮箱账单自动导入 / 短信账单导入」三卡（标题 + 一行说明 + 按钮）+「▸ 如何导出账单？」折叠条；微信/支付宝指引卡不再常驻 |
| ② 返回语义（模拟器） | **通过**：结果态按系统返回键 → 回三入口页（`选择导入方式` 可见），再返回才退出本页 |
| ③ 设置分组汇总页（两端） | **通过**：5 行汇总（账本 / 外观：跟随系统 · 默认 / 安全：应用锁未开启 / 数据：备份导出·导入 + 邮箱凭据（真机「已配置 1 个」/+ 模拟器「未配置」）/ 短信账单：已开启 · 静默聚合）+ 页脚 `MiniBook v1.0.0` |
| ③ 四个二级页（模拟器） | **通过**：`settings-sms`（开关矩阵 + 白名单 + 权限）、`settings-appearance`（暗色模式 + 主题颜色）、`settings-security`（应用锁 + 锁定时长 + 数据加密）、`settings-data`（导出/导入/导入账单/邮箱凭据）均可达，顶栏「← + 标题」返回 |
| ③ 二级页（真机抽验） | **通过**：`settings-sms` 页含「本机短信库共 1 条（App 可见数量…）」（MIUI 受限视图口径，见 §14.4） |
| 未覆盖（如实挂账） | 折叠区**展开态**真机截图未单独采集（同一 presenter 纯函数 + 单测覆盖）；真机仅抽验一个二级页 |

### 单测增量明细（+2 例，`SmsImportPresenterTest`）

| 用例 | 断言要点 |
|------|----------|
| `已看过的未识别条目移出主列表、进折叠区` | `lines()` 不含 `seen=true` 条目、含 `seen=false` 条目；`seenLines()` 精确等于该条 ⚠ 行；`unrecognized.size == 2` 且 `scanTotal == 8`（**计数闭合不因折叠改变**） |
| `未识别行文案统一：主列表与折叠区同一格式` | `unrecognizedLine(item) == "⚠ 未识别（发送方未登记模板）：摘要：支出 77.00 元"`（折叠区不另造格式） |
| `待确认条数 = 未看过的未识别 + 疑似重复（已看过不计，2026-10-10 二次反馈）` | 全看过 → 归零（只保留疑似重复）；混合（1 已看过 + 1 新）→ 只报新条目；`null` → 0 —— 在**原用例内扩展断言**，用例总数不变（仍 52 类 / 408 例） |

## 最新测试（F-14 真机 + 模拟器验收，2026-10-10）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-10 |
| 授权依据 | 用户指令「真机连接上了，你可以测试一下，如果你能模拟发短信，那就非常棒了」 |
| 真机环境 | 小米 `某机型`，Android 16 / SDK 36（HyperOS），adb `A1B2C3D4`，1200×2608 |
| 模拟器环境 | AOSP `Pixel_7`，Android 15 / API 35（`sdk_gphone64_x86_64`）；`adb emu sms send` 注入真实 PDU → 真 `SMS_RECEIVED` 广播 |
| 执行方式 | 主代理单点执行（规则 3）：`adb install -r` + `uiautomator` 定位点击 + `logcat` + `run-as` 读 DataStore/DB |
| 详细记录 | `docs/13_sms_bill_import.md §14`（含缺陷根因、SQL 报错原文与 MIUI 证据） |

### 结果总览

| 指标 | 数值 |
|------|------|
| JVM 单测（复跑，口径定案后） | **52 类 / 406 例 / 0 失败 / 0 错误 / 0 跳过**（F-14 占 7 类 / 73 例） |
| `:app:assembleDebug` | **绿** |
| 真机 L0 全量干跑（真实数据） | **通过**：`扫描 1 条 = 可导入 0 + 未识别 1 + 非账单 0 + 已存在/重复 0`，零写入 |
| 真机权限流 | **通过**：重装后经 `pm grant --user 0` 授权，**不重启**即进入扫描（缺陷 B 修复验证） |
| 模拟器 L0 干跑 | **通过**：`扫描 4 = 可导入 3 + 未识别 0 + 非账单 1 + 已存在/重复 0`（3 条账单 + 1 条验证码） |
| 模拟器余额链审计 | **通过**：故意注入缺口（应 755.91，实发 700.00）→「余额链疑似缺口 1 处」 |
| 模拟器确认入库 | **通过**：新增 3 笔、跳过 0 条；首页见记录与「短信」来源徽标；金额自洽（支出 325.00 = 100+50+100+30+20+25，收入 200.00，结余 −125.00） |
| 模拟器幂等重扫 | **通过**：`已存在/重复 3、可导入 0`，逐条给出跳过原因 |
| 非账单零提醒 | **通过**：验证码短信（App 进程已杀）→ `自动 0，待确认 0`，无通知 |
| L1 后台待确认 | **通过**：`自动 0，待确认 1`，不写库、不崩溃 |
| **L2 后台自动记账** | **通过**：3 次确认晋级 + 开「自动记账」+ 杀进程注入 → **`自动 1，待确认 0`**，首页出现 ¥25.00 记录 |

### 本轮发现并修复的 5 个缺陷（均为「JVM 绿 / 真机静默失效」）

| # | 缺陷 | 发现手段 | 修复 |
|---|------|---------|------|
| A | `SmsBillReader.selection()` 生成 `1..6.first` 非法 SQL | 真机 logcat：`SQLiteException: near ".6": syntax error` | 改拼 `SmsMessageType` 常量 |
| B | `ImportScreen` 权限取组合期快照 → 引导页卡死 | 真机重装后授权复检 | 点击时 + `onResume` 当场复查 |
| C | 接收器漏判「自动记账」总闸（未开启也可能写库） | 代码核对 + 模拟器复测 | 统一口径 `SmsSettings.autoImportUsable` |
| D | 银行模板兜底渠道 `OTHER` → **L2 永不触发** | 接收器诊断日志：`原因=渠道（other）会被账单文件覆盖` | `SmsTemplate.defaultChannel`；`bank.generic` → `BANK_DIRECT`；补回归用例 |
| E | 广播拉起的新进程未开库 → 后台链路静默失效（异常被吞） | 模拟器 logcat：`IllegalStateException` | 后台静默开库（DEC-002）+ DB 未就绪降级「待确认」+ 结果/原因日志 |

### 新的能力边界（如实登记）

**MIUI/HyperOS 受限视图**：非默认短信应用的 `content://sms` 查询被 ROM 重写为 `sms_restricted`，过滤 `sp_type≠0` 的服务/通知类会话。实测 App 可见 **1 条** / shell 可见 **9 条** → **oracle 对齐（shell 计数 = App 计数）在本 ROM 上不成立**，不得据此判定漏读。详见 `13_sms_bill_import.md §14.4`。

### 验收后口径定案：三层兜底（F1 修复保留 / F2 角标撤销）+ 边界短信复测（2026-10-10）

默认通知方式是**静默聚合**（不弹通知）。实测发现该配置下「未白名单发送方的银行短信」在 App 内**没有任何提示**：数据没丢（L0 能重新识别，已实测），但发现环节断了。先按「补扫 + 水位不越界 + 角标」补修并验证通过，随后**真机角标常驻去不掉**（缺陷 F3：未识别条目无销账出口），用户据此拍板**撤销角标**，改为三层兜底（根因、证据与复盘见 `13_sms_bill_import.md §14.6`，口径见 DEC-028 修订）：

| # | 项 | 结果 |
|---|----|------|
| F1 | 补扫触发点由「打开导入页」改为「打开 App（首页 VM 初始化）」 | **通过（保留）**：真机 `logcat -s MiniBookSms` 每次开 App 都出 `L3 补扫：扫描 1，自动 0，待确认 1，跳过=-` |
| F2a/b/c | 水位不越过待确认 + `SmsPendingTracker` + 首页角标 | **曾实现并验证通过**（模拟器：杀进程后开 App 角标自愈 2 条、L0 写入 2 笔后角标消失、首版 `onError` 配色不可读已改白字）→ **2026-10-10 用户决策撤销**，跟踪器（含 7 例单测）、角标与销账调用全部删除，水位回到「读到即推进」 |
| F3 | 角标常驻（未识别条目无销账出口） | **由决策消解**：不做角标、不做已读未读 ⇒ 该缺陷不再存在；「不遗漏」改由 L0 手动导入兜底 |
| — | 撤销后复验（真机 + 模拟器） | **通过**：两端首页均无任何「待确认」节点；补扫日志照常；真机账本数字未受影响 |
| B1/B2 | 通知类（106 端口）与「银行端口但无金额」 | **通过**：均判**非账单**，零提醒、不写入（不猜金额） |
| B3 | 发送方未登记模板 | **通过**：判**未识别**，清单页显示发送方/时间/预览/原因「发送方未登记模板」+ 重新扫描/手工记一笔 |
| B4 | **跨源去重**（手工 ￥77.00 vs 未入库的 ￥77.00 短信） | **通过**：复扫由「可导入」转「已存在/重复」（`可导入 1→0`、`重复 12→13`）；该行在惰性列表可视区外，未截到逐行文案 |

### 仍未验证（不得声称已验证）

1. 应用锁开启态 L2 门控（需系统锁屏 + 生物识别/PIN）；
2. 真实银行账单短信端到端（本机 App 可见短信中无银行账单，需另一台手机发送）；
3. MIUI 对 95xxx 短号的 `sp_type` 归类；
4. 通知「逐条提醒」模式（默认「静默聚合」已实测：无通知）；
5. L3 增量补扫场景 —— **已构造并验证**：开 App 即触发（真机日志 `L3 补扫：扫描 1…`）；「角标重启自愈」属已撤销方案，不再适用；
6. L4 分享导入未实现；
7. 双卡（`sub_id`）与长时间跨度（跨月/跨年）扫描（模拟器单卡、`emu sms send` 无法指定 `sub_id`/短信时间）。

### 结论

- [x] 通过（**JVM 单测 + 构建 + 真机 L0 + 模拟器 L0/L1/L2 端到端**）
- [ ] 不通过（无）

结论说明：F-14 在两端完成可执行验收；**6 个实测缺陷已修复/定案**（真机 5 个修复复测 + 验收后 F1 修复保留；F2 角标方案与 F3 常驻缺陷按用户 2026-10-10 决策**撤销**，改为三层兜底 = ①到达即记账 → ②打开 App 补扫 → ③手动导入），并复测 4 类边界短信（通知类 / 无金额银行短信 / 未登记模板 / 跨源判重）；未覆盖项见上（应用锁态、真实银行样本、逐条通知、双卡与长跨度、L4）。

## JVM 单测（F-14 短信账单导入，2026-10-09）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-09 |
| 测试范围 | F-14 短信账单导入：解析器（模板锚点/金额转分/方向判定/日期跨年/幂等键）、跨源匹配规则（唯一判重/多候选/同源排除/双向）、余额链审计、白名单四条件与晋级降级、计数闭合不变量、白名单编解码、导入页 presenter 展示口径。对应 TASK-051~057；先读 `docs/13_sms_bill_import.md` + `docs/05_ui.md §11`。 |
| 被测代码 | 工作区未提交改动（F-14 全量实现，2026-10-09） |
| 构建环境 | JDK21（`D:\soft\jdk\jdk21`）；`:app:assembleDebug` BUILD SUCCESSFUL（app-debug.apk 35.95 MB） |
| 执行命令 | `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest` |

### 结果总览（证据来自 `app/build/test-results/testDebugUnitTest/*.xml`）

| 指标 | 数值 |
|------|------|
| JVM 单测 | **52 类 / 406 例 / 0 失败 / 0 错误 / 0 跳过**（基线 45 类 / 333 例 → 本次 +7 类 / +73 例；含 2026-10-10 补的渠道兜底回归 1 例） |
| 本次失败用例 | 首跑 394 例中 3 例失败，**全部为测试自身缺陷**（见下），修正后全绿 |
| `:app:assembleDebug` | **绿**（含新增 3 项权限与 `SmsReceivedReceiver` 的清单合并） |
| androidTest | 本阶段未新增；既有 instrumented 用例仍未在设备执行（延续挂账） |
| 真机 / 端到端 | ✅ **已执行（2026-10-10）**，见上方「最新测试」章节与 `13_sms_bill_import.md §14` |

### F-14 单测明细（7 类 / 73 例）

| 文件 | 例数 | 覆盖点 |
|------|:---:|------|
| `SmsBillParserTest` | 22 | 银行消费/入账抽全字段；金额边界（千分位/最小分/三位小数非法）；收支同现取靠前；无方向词→`Unrecognized`（不猜）；金额/日期缺失→`Unrecognized`；验证码/营销→`NotBill`；未登记发送方→`Unrecognized`；支付宝/财付通渠道抑制；**银行模板兜底渠道=银行直连且允许自动记账（2026-10-10 实测缺陷 D 回归）**；幂等键稳定性（同文稳定/金额不同不同/零宽与空白不影响）；跨年回退；摘要脱敏 |
| `SmsWhitelistPolicyTest` | 13 | 四条件逐条否定 + 全满足→自动；3 次确认晋级；已达阈值但用户关闭→不重开；纠错降级（关闭+清零）；错误留痕 |
| `SmsImportPresenterTest` | 11 | 干跑 headline 与计数闭合等式一致；结果 headline 四类计数；逐条结果行四类标记；超 100 行截断；5 步状态机（扫描中/预览/失败/完成）；余额链文案不含余额数值；待确认计数 |
| `SmsImportSummaryTest` | 8 | 两条闭合不变量（合法/各自违反抛异常）；空库；渠道抑制口径 |
| `BalanceChainAuditorTest` | 7 | 自洽链条无缺口；断裂检出缺口与时间区间；收入方向；不同卡尾号不互比；样本不足跳过；`headline` 汇总 |
| `SmsMatchRulesTest` | 7 | 无候选→新；唯一跨源→判重（依据含金额与来源）；两条→疑似重复；同来源不参与；**双向**（账单文件入口反查短信行）；金额格式化；来源文案 |
| `SmsWhitelistStoreCodecTest` | 5 | 编解码往返（含/不含错误时间戳）；畸形行忽略；按发送方升序；计数非法回退 0 |

### 失败用例详情（首跑 3 例，均为测试自身缺陷，已修正）

1. `EnumRoundTripTest.枚举条目数量与规格一致`：既有守卫断言 `TransactionSource.entries.size == 3`，F-14 新增 `SMS_IMPORT` 后应为 4 → 同步断言与 value 断言。
2. `SmsImportSummaryTest.解析计数不闭合 → 抛异常`：用例给的数据其实**两条不变量都成立**（`10 = 6+3+1`、`6 = 6+0+0+0`）→ 改为真正漂移的组合（`parsed 5 ≠ imported 4`）。
3. `SmsBillParserTest.幂等键：空白与零宽字符不影响哈希`：断言写错语义——零宽字符是**删除**而非「转为空格」，全角空格 U+3000 才转空格 → 拆成三条规则分别断言。

### 已知遗留 / 未验证项（**不得声称已验证**）

1. **无真实短信账单样本**：以上全部为**合成 fixture**；解析准确率（真实银行的格式漂移）未经真实样本回归。
2. **真机端到端**：✅ 已于 2026-10-10 执行（见本文件顶部章节）；其中 `SmsNotificationHelper`「逐条提醒」模式与「应用锁开启」态仍未覆盖。
3. **L3 补扫（`catchUp`）无单测**：依赖 `SmsBillReader`（Android `ContentResolver`）与 `ImportBillUseCase`（多依赖），JVM 侧无替身；口径（首次只立基线、开关门控、水位推进）为**代码级保证**。
4. **UI 交互测试未覆盖**：待确认清单页、设置区块、权限引导为 Compose 交互，延续项目「UI 可选」口径。
5. 解析按天匹配（时间戳丢弃）→ 跨天错位漏判为**已知盲区**，二期以 `occurredAt` + ±10 分钟窗口解决。

### 结论

- [x] 通过（**限 JVM 单测与构建**）
- [ ] 不通过（无）

结论说明：F-14 JVM 单测 73 例 + 全量 406 例全绿、`:app:assembleDebug` 绿；**真机与模拟器端到端验收已于 2026-10-10 完成**（见本文件顶部章节），仍未覆盖项已在章内如实登记。

---

## 最新测试（F-12 账单导入，2026-08-24）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-08-24 |
| 测试人 | Tester（Tester） |
| 测试范围 | F-12 微信/支付宝账单导入：数据层迁移（DB 1→2）、支付宝 GBK CSV / 微信 XLSX 解析器、ImportBillUseCase（自动分类/幂等/事务原子）、UI 接线（源码静态核对 + 真机待验证）。对应 TASK-040~044；先读 `docs/01_prd.md §F-12`（验收逐条）+ 02/05/06/07。 |
| 被测代码 | e13eee0（数据层/解析器/用例）+ 913e7f0（UI） |
| 构建环境 | JDK21（`D:\soft\jdk\jdk21`）；**设备 MEIZU 21 未连接**（`adb devices -l` 空，`kill-server`/`start-server` 重试仍空，PnP 未见 MEIZU VID 2A45）→ 真机 3 项降级为「遗留手动验证清单」 |

### 结果总览

| 指标 | 数值 |
|------|------|
| JVM 单测 | **132 例全绿**（`gradlew :app:testDebugUnitTest`），本次补测 +21 例 |
| androidTest 编译 | `:app:compileDebugAndroidTestKotlin` **绿**（含新增 `MigrationV1To2Test`） |
| 迁移 instrumented | 编译绿，**未执行**（①设备未连接 ②见缺陷 P2-1） |
| 真机迁移 / UI 结构 / 端到端 | 未执行（设备未连接），见「遗留手动验证清单」 |
| 缺陷 | P2 ×1（androidTest 无法 DEX）+ P3 观察 ×2；**无 P0/P1 产品缺陷** |

### 单测（JVM 132 例全绿）

- **F-12 原有 16 例**（e13eee0 已含：AlipayCsvParserTest 4 / WechatXlsxParserTest 3 / BillCategoryMappingTest 3 / ImportBillUseCaseTest 6）覆盖：GBK 解码、动态表头（前导说明行增减）、`\t` trim、金额转分（8.70→870 / 6.5→650）、`￥,` strip、不计收支/交易关闭/`/`中性行/解析失败逐类计数、微信日期序列数（1899-12-30+整数天）、`alipay:`/`wechat:` externalId 前缀、自动分类命中/未命中→「其他」、幂等去重、事务写异常→整体回滚零落库。
- **本次补测 +21 例**（对齐 PRD §F-12 验收中 Original 未覆盖分支，清单见下）。

### 补测清单（本次 +21 例）

| 文件 | 例数 | 覆盖分支（PRD §F-12 验收/DEC） |
|------|:---:|------|
| `BillParserFactoryTest`（新） | 7 | DEC-017 格式探测：`.csv`/`.xlsx`（大小写不敏感）/zip 魔数兜底/未知扩展名→null/无扩展名或 null 名→按 CSV 试解/短魔数(<4B)不算 zip |
| `BillTextTest`（新） | 7 | 金额 strip `¥/￥/,/，/\u00A0`；非法/负/空/零；description ≤200 截断 + 备注`/`不追加 + 仅备注；日期归一化 `2026/8/24`；收/支映射 |
| `BillHeaderMatcherTest`（新） | 4 | 必需列三缺一不判表头；列序重排仍命中（换列）；`对方账号`/`商家订单号`不被误捕（不落库）；XLSX 绝对列序换列 |
| `EnumRoundTripTest`（扩） | +2 | `TransactionSource` fromValue 往返/value 常量/非法串；枚举数量守卫纳入 `TransactionSource=3`、`ImportSkipReason=4` |
| `MapperRoundTripTest`（扩） | +1 | Transaction 四新字段 + source 枚举往返（导入交易 `alipay_import` + 手动记录默认 `manual`/三列 null） |
| `MigrationV1To2Test`（新，androidTest） | 1 | DB 1→2 迁移：旧数据保留、新列默认、唯一索引、NULL 互异（见「迁移验证」） |

### 迁移验证（DB 1→2，DEC-018）

- **测试基建补齐（Tester 补，非产品代码）**：`app/build.gradle.kts` 增 `androidTestImplementation(libs.androidx.room.testing)` + `androidTest.assets.srcDir("schemas")`（原 `room-testing` 仅在 `testImplementation`，错位到 JVM 源集且无 assets 指向，无从跑 MigrationTestHelper）。
- **新增 `MigrationV1To2Test`**：`createDatabase(v1)` 造旧 10 列库 + 一条手动记录 → `runMigrationsAndValidate(2, true, MIGRATION_1_2)`（对照 `2.json` 校验收件 schema）→ 断言 ①旧数据仍在 ②`source='manual'`、`externalId/counterparty/paymentMethod` 三列 NULL ③唯一索引 `UNIQUE(ledgerId, externalId)` 重复插抛 `SQLiteConstraintException` ④NULL externalId 互异不冲突（两条 null 均插入成功）。
- **schema 静态校验（已通过）**：`app/schemas/1.json`=旧 10 列 + 4 普通索引；`2.json`=14 列（`source TEXT NOT NULL DEFAULT 'manual'`）+ 唯一索引 `index_transactions_ledgerId_externalId`，与 `MIGRATION_1_2` SQL、Entity 注解名逐字一致。
- `:app:compileDebugAndroidTestKotlin` 绿；**执行依赖设备 + 先解 P2-1**。

### 真机验证（未执行：设备 MEIZU 21 未连接）

`adb devices` 为空（重启 adb 仍空），Windows PnP 未见 MEIZU（VID 2A45）ADB 接口。真机迁移保数据、UI 结构、端到端导入三项目前无法本机执行，**不卡住**，转「遗留手动验证清单」（含精确点击路径与预期结果）。

### 缺陷清单

| 编号 | 级别 | 现象 | 复现 | 根因假设 / 建议方向（不修） |
|------|------|------|------|------|
| P2-1 | P2 | `gradlew :app:assembleDebugAndroidTest` 于 D8 dex 失败：`Space characters in SimpleName 'monthSummary 按区间汇总收支' are not allowed prior to DEX version 040` | 执行 `assembleDebugAndroidTest`（androidTest 源集含 14 个中文反引号含空格方法名：既有 13 + 本次新增 1） | 反引号含空格方法名 JVM 单测可用、Android dex 不可（D8 需 DEX 040+ 才允许空格，当前 D8 目标 <040）。**测试基建缺陷（pre-existing）**，阻断 `connectedAndroidTest`（instrumented 迁移测试因此仅能编译、暂无法运行）。修复方向：把 androidTest 全部反引号方法名改无空格 ASCII（如 `monthSummary 按区间汇总收支`→`monthSummarySumsByRange`），或排查 androidTest dex 的 min-api 配置。主包 `assembleDebug` 已绿（无此问题，仅 androidTest 命中）。 |
| P3-1 | P3 | `BillColumn.match` 以 `h.contains("对方")` 命中 COUNTERPARTY，支付宝「对方账号」列（PRD 明列不落库）亦命中 | 静态核对 `BillColumn.kt` | 当前支付宝真实表头「交易对方」列序在「对方账号」之前，首列优先（`putIfAbsent`）恰好正确；若两列位置调换则误捕。建议 COUNTERPARTY 精确锚「交易对方」。观察级，非当前数据缺陷，不修。 |
| P3-2 | P3 | `SafBillFilePreparer` XLSX 临时文件名固定 `import_bill.xlsx`，与 DEC-017 设计 `minibook_import_<uuid>.xlsx` 不符 | 静态核对 | 单用户顺序导入无冲突、finally 已删，仅进程残留/并发场景有微小风险，非功能缺陷。观察级，不修。 |

### 验收标准逐条核对（PRD §F-12 十项）

| # | 验收标准 | 状态 | 证据 |
|---|---------|------|------|
| 1 | 两种格式可解析；非目标格式/表头失败→未识别零落库 | ✅ JVM（真机端到端待设备） | 解析器 + `BillParserFactoryTest` |
| 2 | 动态表头不依赖前导行；支付宝 `\t` 尾随 trim | ✅ JVM | `AlipayCsvParserTest` + `BillHeaderMatcherTest` |
| 3 | 微信日期序列换算、金额 BigDecimal 转分无精度损失 | ✅ JVM | `WechatXlsxParserTest`（46257→2026-08-23）+ `BillTextTest` |
| 4 | 字段映射逐项正确；当前状态/对方账号/商户单号不落库 | ✅ JVM | 解析器断言 + `BillHeaderMatcherTest` 不误捕 |
| 5 | 过滤逐类计数（不计收支/交易关闭/解析失败） | ✅ JVM | `AlipayCsvParserTest` 计数断言 |
| 6 | 幂等：重复导入跳过、手动记录 externalId=null 不受影响 | ✅ JVM + 迁移测 | `ImportBillUseCaseTest` + `MigrationV1To2Test`（NULL 互异） |
| 7 | 自动分类预填正确、失败归对应 type「其他」 | ✅ JVM | `BillCategoryMappingTest` + `ImportBillUseCaseTest` |
| 8 | 结果页成功 N / 跳过 M 分原因、解析失败给行号 | ✅ 源码核对（真机渲染待设备） | `ImportComponents.kt`（C-35/C-36）+ `ImportViewModel` |
| 9 | 事务原子性：写异常整体回滚零落库 | ✅ JVM | `ImportBillUseCaseTest`（注入异常→0 插入） |
| 10 | 千条级流畅、期间 UI 锁定 + 进度反馈 | ⏳ 待真机 | 遗留清单 |

### 真机验证结果（2026-08-24 补验，编排者执行）

> 前置：`adb install -r app\build\outputs\apk\debug\app-debug.apk`（覆盖安装保数据）→ **Success**。设备 MEIZU 21（`461QYFDN225D9`）连接时断时续（authorized↔unauthorized 反复），关键步骤均完成。

1. **真机迁移保数据** ✅：`am start` 启动，进程存活，`logcat` 搜 `Room|SQLiteException|IllegalState|Migration|FATAL|AndroidRuntime` 无命中——迁移成功、无整库重建；列表可见既有数据（含更早导入的微信账单数据与手动记账数据）。
2. **入口 + 导入页 Idle 态** ✅：uiautomator dump 确认首页三格 tile 含「导入账单」📥；导入页顶栏「导入账单」+ 引导说明 + 微信/支付宝两 `PlatformGuideCard`（含导出路径与 `.xlsx`/`.csv` 提示）+「去选择文件」按钮，文案与 05_ui §9 精确一致。
3. **支付宝端到端导入** ✅：push `alipay.csv` 到 `/sdcard/Download/` → SAF（DocumentsUI）选文件 → 首页最近交易出现支付宝数据：`支付宝` SourceBadge、自动分类（交通🚗/餐饮🍜）、counterparty（某市交通卡公司/某火锅店/某电商平台）、description（乘车/美团收银/库迪订单）、金额分正常。
4. **幂等（去重）** ✅：重导同一支付宝账单 → 结果页「本次无新增交易」「跳过 234 笔」：**已存在 202 / 不计收支 20 / 交易关闭 8 / 解析失败 4**（202+20+8+4=234 全对上），externalId 去重生效、无重复入账。
5. **过滤** ✅：不计收支 20、交易关闭 8 正确跳过并计数（与解析器口径一致）。

> **与本清单原预估的差异（待查）**：实测支付宝为「成功 202 / 跳过 32（不计收支 20 + 交易关闭 8 + **解析失败 4**）」，非原预估「206 / 28」。差额在 **4 笔解析失败**——设备锁屏未能展开明细核对原因（疑似金额非法/时间缺失/单号缺失等数据边界）。明日展开「解析失败 4 笔（点击展开）」核对是否属正常过滤还是需修代码。
> **微信 XLSX**：设备上已有更早导入的微信数据（美团/滴滴出行/婷姐/礼金自动分类等，SourceBadge 正常），且 `WechatXlsxParserTest` 单测已覆盖日期序列/金额字面量/中性行过滤；本轮未单独重导微信账单，明日可对 `wechat.xlsx` 补一次端到端。

---

## Phase 2 阶段测试（F-06/07/08，2026-08-21）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-08-21 |
| 测试人 | Tester（Tester） |
| 测试范围 | **Phase 2 阶段测试**——预算超支口径（TASK-037）、搜索组合过滤（TASK-032）、报表 UseCase（TASK-036 / TASK-034）、数据 mapper 派生口径（PeriodTrend.balanceFen）、SearchViewModel 金额区间 min>max 校验（TASK-033）。对应 TASK-031~039「需测试覆盖点」中 JVM 单测可覆盖项（重点 = TASK-037）。 |
| 代码版本 | 测试提交（Tester，2026-08-21，待编排者批准后 commit）；被测代码 = Phase 2 编码 14 个本地提交，工作区干净（`git status` 无改动）。 |

### 测试结果

| 指标 | 数值 |
|------|------|
| 用例总数 | 92（JVM 单元 92 例，全量 `./gradlew :app:testDebugUnitTest`） |
| 通过 | 92 |
| 失败 | 0 |
| 跳过 | 0（纯 JVM 单测无跳过；instrumented 未执行不入本口径，见「已知遗留」） |
| 覆盖率 | N/A（未采集） |

> 口径说明：
> - **单元 92 例 = Phase 2 新增 36 例 + Phase 1 基线 56 例**。Phase 1 基线（2026-08-20，提交 `1d09ae1`）= Tester 4 文件 40 例（EnumRoundTripTest 12 / MapperRoundTripTest 9 / TimeFormatterTest 6 / BackupJsonMapperTest 13）+ Developer 存量 16 例（MoneyFormatterTest 10 / ValidatorsTest 6）。
> - **Phase 2 新增 36 例 = Tester 6 文件**，全绿，清单见下方「用例清单」。

### 用例清单（Phase 2 新增，Tester 6 文件 / 36 例）

#### TASK-037 预算超支口径（重点）

**`BudgetProgressTest`（11 例）** — 纯 domain 逻辑（PRD F-08 / DEC-012）：

| 用例名 | 覆盖点 |
|--------|--------|
| 未设总额预算但当月有支出 - overBudget 为 false（守卫） | (a) 守卫：totalBudgetFen=0 且当月有支出 → overBudget=false |
| 未设总额预算且当月无支出 - overBudget 与 hasOverBudget 均为 false | (a) 守卫：totalBudgetFen=0 且无支出 → 均 false |
| 已设预算且已用大于预算 - overBudget 为 true | (b) 已设预算且已用>预算 → overBudget=true；hasOverBudget=true |
| 已用刚好等于预算 - 不超支（严格大于） | 边界：已用==预算 → 不超支（PRD 超支=已用>预算） |
| 已用小于预算 - 不超支且剩余为正 | 未超支剩余为正 |
| 超支时 remainingFen 为负 | (d) remainingFen 可为负 |
| 任一分类超支 - hasOverBudget 为 true（总额未超支） | (c) 任一分类超支 → hasOverBudget=true |
| 全部分类未超支且总额未超支 - hasOverBudget 为 false | hasOverBudget=false 基线 |
| 分类已用大于分类预算 - 分类 overBudget 为 true 且剩余为负 | CategoryBudgetProgress 超支/负剩余 |
| 分类已用等于分类预算 - 分类不超支 | 分类边界（==）不超支 |
| BudgetProgress 字段透传 | 模型字段映射正确 |

**`ObserveBudgetProgressUseCaseTest`（5 例）** — 三源 combine（Fake Budget/Transaction/Category Repository + Fake LedgerDataStore，`runTest`）：

| 用例名 | 覆盖点 |
|--------|--------|
| 三源 combine 总额已用为各分类 totalFen 之和 | 三源组合；总额已用=各分类 `CategoryAgg.totalFen` 之和（≈monthSummary.expenseFen）；委托账本/过滤类型正确 |
| 分类已用合计超过总额预算 - overBudget 为 true | 已用>预算 → 超支 + negative remaining |
| 已删分类的孤儿预算被跳过 - 不计入分类进度 | 已删分类（不在分类流）的孤儿预算（categoryId 悬空）跳过展示 |
| 分类进度按 order 升序 | 按 `Category.order` 升序（乱序输入→有序输出） |
| 当前账本未初始化 - 发零值进度保活 | 空串 ledgerId → 零值 BudgetProgress（保活） |

#### TASK-032 搜索组合过滤

**`SearchTransactionsUseCaseTest`（3 例）** — Fake TransactionRepository 验证 filter 透传：

| 用例名 | 覆盖点 |
|--------|--------|
| 交易搜索 filter 字段透传 | remark/amountMin/amountMax/categoryId/dateFrom/dateTo/type 逐字段透传（分、含端点） |
| 默认 filter 返回全量（全 null 不过滤） | 默认 `TransactionFilter()` 等价不过滤 |
| 当前账本未初始化 - 发空列表保活且不触达 Repository | 空串 ledgerId → 空列表、不触达仓库 |

**`SearchDebtsUseCaseTest`（4 例）** — Fake DebtRepository 验证 filter 透传：

| 用例名 | 覆盖点 |
|--------|--------|
| 债务搜索 filter 字段透传 | partyName/kind/direction 透传 |
| 仅姓名关键词过滤非 null 化 | 未选方向/kind → 保持 null（不过滤） |
| 默认 filter 返回全量（全 null 不过滤） | 默认 `DebtFilter()` 等价不过滤 |
| 当前账本未初始化 - 发空列表保活且不触达 Repository | 空串 ledgerId → 空列表 |

#### TASK-036 报表

**`ReportUseCaseTest`（9 例）** — Fake TransactionRepository（返回 PeriodTrend 序列 / CategoryAgg 序列）：

| 用例名 | 覆盖点 |
|--------|--------|
| 月视图委托 observeDailyTrend 且区间为当月首末日 | MONTHLY 委托 observeDailyTrend，区间=当月首/末日；映射 ReportTrend 三序列 |
| 年视图委托 observeMonthlyTrend 且区间为当年首末日 | YEARLY 委托 observeMonthlyTrend，区间=当年首/末日 |
| 当前账本未初始化 - 发空趋势保活 | 空串 → 空 ReportTrend（图 1 空态判定） |
| 累计结余 runningFold 多桶累加正确 | `runningFold`(0) 累计：d1 +200 → d2 −200 → d3 −50 |
| 无数据时累计结余走势 isEmpty 为 true | BalanceTrend 空态判定 |
| 分类占比 金额降序 且 已删分类置底 null 兜底 | 分类映射 + 已删分类（不在分类流）→ category=null 兜底 + 金额降序 + totalFen>0 过滤 + 委托 type/区间 |
| 分类占比 当前账本未初始化 - 发空列表保活 | 空串 → 空列表 |
| PeriodTrend balanceFen 等于 income 减 expense | 数据 mapper 口径：balanceFen=income−expense（负/零/正） |
| ReportTrend isEmpty 判定 | 桶空或收入/支出序列均空 → isEmpty |

> **数据 mapper 说明**：`PeriodTrendRow→PeriodTrend`、`CategoryAggRow→CategoryAgg` 为
> `RoomDataSourceImpl` 的**私有**方法（字段透传 / 金额 Long 分不变），不可脱离 data 层单独单测，
> 故并入报表 UseCase 测试：`PeriodTrend.balanceFen`（income−expense）由本组直接断言，
> `CategoryAgg` 的 Long 分透传为 data class 天然成立。二者见 TASK-034/TASK-036 覆盖点，无遗漏。

#### TASK-033 SearchViewModel min>max 校验

**`SearchViewModelAmountRangeTest`（4 例）** — JVM 可见的「校验判定」逻辑（`kotlinx-coroutines-test` + `Dispatchers.setMain` 提供可控 Main 调度器）：

| 用例名 | 覆盖点 |
|--------|--------|
| min 大于 max - amountRangeInvalid 为 true | min>max → 无效 |
| min 等于 max - amountRangeInvalid 为 false（合法） | min==max 合法 |
| 只设 min 或 max - amountRangeInvalid 为 false（不过滤单边） | 单边不为无效 |
| min 大于 max - uiState 折为 Error 且不执行搜索 | min>max → `SearchUiState.Error(AMOUNT_RANGE_ERROR_MESSAGE)` |

### 失败用例详情

（无——单元 92 例全绿，无失败用例。）

### 已知遗留 / 待补

1. **instrumented 真机执行仍未做（Phase 1 遗留，本阶段未动）**：`NetByPartyDaoTest`（净额，7 例）、`MonthlySummaryDaoTest`（月结余，3 例）与 `TransactionRunnerAtomicityTest`（迁移事务原子性，Phase 1 收尾已补真断言 commit `ab7214c`）——本阶段未改 `app/src/androidTest/`，`compileDebugAndroidTestKotlin` **编译通过（BUILD SUCCESSFUL）**但未在设备/模拟器执行，结果待人工。
2. **Phase 2 数据层聚合 SQL 正确性（TASK-031 LIKE/金额区间、TASK-034 收支分桶/分类占比）属 instrumented 才能验证的 SQL 行为**：本阶段未写对应 instrumented 用例；JVM 侧以 UseCase 委托 + Fake Repository 覆盖「委托正确 + 结果映射」，SQL 明细待后续补设备验证。
3. **UI 交互测试未覆盖**（对应 TASK-033/035/038/039 的「UI 可选」项）：搜索结果组合的完整 UI 流、图表明细渲染 + 空态、预算页 UI、收支页分类联表展示——本阶段仅覆盖 `SearchViewModel.amountRangeInvalid` 判定与 `uiState.Error` 路径；`SearchUiState` 其余（Loading/Empty/Success 两组结果合并、debounce 行为）属 UI 交互流，未单测。

### 结论

- [x] 通过
- [ ] 不通过

结论说明：Phase 2 JVM 单元测试 92 例全绿（Phase 1 基线 56 例 + 本阶段新增 36 例），Tester 阶段测试通过；instrumented 用例（DAO 净额/月结余/迁移原子性）编译通过但未在设备执行、UI 交互测试为「UI 可选」未覆盖——已知遗留，需设备验证后补测（延续 Phase 1 口径）。

---

## 历史测试记录

### Phase 1 / Stage 7（2026-08-20）— 首轮阶段测试

- 提交：`1d09ae1`（Tester，7 个测试文件，+939 行）
- 范围：领域模型枚举 / 数据映射往返 / util 工具 / DAO 净额与月结余（instrumented）
- 结果：单元 56 例全绿；仪器测 2 套（10 例）编译通过未执行；迁移事务原子性为 TODO 骨架
- 状态：**已确认**（含已知限制，待设备验证后补测 instrumented 项）

> 补充（2026-08-21 协调日志回填）：`TransactionRunnerAtomicityTest` 三个空骨架已由 Tester 补全为真实断言（`commit ab7214c`，内存库 + DatabaseProvider 替身），此前的「降级为 TODO 骨架」限制已解除，剩设备执行。

### 与 Phase 1 报告的延续性

- 本 Phase 2 报告在 Phase 1（Stage 7）基础上**新增** Phase 2 单元 36 例，**保留** Phase 1 基线 56 例与既有已知限制（真机 instrumented 未执行、UI 可选未覆盖）。
- 被测版本延续同一工程 / JDK21（`D:\soft\jdk\jdk21`）构建链；测试风格、命名（中文反引号用例名 + AAA）、Fake Repository/DataSource 写法（新增 `app/src/test/java/com/minibook/testutil/`）均对齐既有基线。
- Phase 1 遗留项（真机 instrumented 执行、迁移原子性设备验证）在「已知遗留」中仍挂账，未被本阶段关闭。
