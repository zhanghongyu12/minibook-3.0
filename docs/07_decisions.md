# MiniBook Android 重构版 - 决策记录

> 状态：已确认（本文件为决策档案，持续追加）
> 维护者：全员可追加（Architect 汇总本阶段决策）
> 最后更新：2026-10-10
> 说明：DEC-001 为技术栈选型；DEC-002~DEC-009 对应阶段 3 拍板的 8 项最终决定（D-1~D-8，与 ARCHITECTURE §14 / DATABASE 落地口径一一对应）；DEC-010~DEC-021 为 Phase 2 决策；DEC-022/DEC-023 为 Phase 3（F-13 邮件账单自动导入）决策。编号递增不复用；已确认决策不可删除，只能标记为「已废弃」。

---

## DEC-001: 技术栈选型

- 日期：2026-08-18
- 决定：采用以下技术栈，**不可再改**：
  - 语言/UI：Kotlin 2.2.0 + Jetpack Compose（BOM 2025.09.01）+ Material 3
  - 本地存储：Room 2.8.4（KSP）+ SQLCipher 4.6.1（全库加密）
  - 密钥/认证：Android Keystore（AES-GCM）+ androidx.biometric 1.1.0
  - DI：Hilt 2.58（KSP）
  - 架构：MVVM + Repository + UseCase + DataSource（接口），协程 + Flow
  - 构建：gradle 8.14.3 / AGP 8.13.0 / KSP 2.2.0-2.0.2 / JDK 21
  - 兼容：minSdk 26（Android 8.0）· compileSdk 36
  - 形态：MVP 单模块 `:app` + 按包分层（含 DataSource 抽象层与 RemoteDataSource 仅接口留云端出口）
- 原因：纯 Android 原生、隐私优先、离线可用的本地优先应用；Compose + Room + SQLCipher + Keystore 是满足加密与生物识别联动（PRD F-05/F-10）的最小成熟组合；Hilt 官方稳定、与 Compose 声明式集成成熟。
- 影响：所有实现遵循本栈；版本锁定写入 `libs.versions.toml`；后续决策（DEC-002~009）均在本栈约束下展开。
- 替代方案：Flutter/Taro 跨端（弃：不做多端）；Realm 本地库（弃：API 26 支持与加密社区成熟度不及 Room+SQLCipher）；自行实现加密（弃：Keystore/Biometric 有官方方案）。
- 最终选择：**DEC-001 既定栈**，按上列版本执行。
- 修正记录（2026-08-18）：Hilt 版本由 2.60.1 收敛为 **2.58**。阶段 6 编码 TASK-001 实测发现 Hilt 2.60.1 与 AGP 8.13.0 内在矛盾、无法共存，实证如下：
  1. Hilt 2.60.1 的 Gradle 插件显式要求 AGP ≥ 9.0.0（`hilt-android-gradle-plugin-2.60.1.jar` 的 `HiltPluginEnvironment.class` 字节码常量含 "9.0.0" 检查），apply 阶段直接拦截，无绕过开关。
  2. 降 Hilt 2.59：虽无 9.0.0 版本检查，但运行时引用 `com.android.build.api.artifact.ScopedArtifact.POST_COMPILATION_CLASSES`（AGP 9 新增枚举值），AGP 8.13.0 不提供 → 项目评估期 NoClassDefFoundError（jar 字节码 grep 证实 2.59 引用、2.58 不引用）。
  3. **Hilt 2.58**：既无 9.0.0 检查、又不引用 AGP9 运行时 API，是兼容 AGP 8.13.0 的最高稳定版。
  4. 实测 `./gradlew :app:assembleDebug` **BUILD SUCCESSFUL**（Hilt 插件全链 hiltAggregateDepsDebug/transformDebugClassesWithAsm 正常，commit 1d8822d）。
  - 收敛方案：**方案 A（降 Hilt 不动 AGP/Kotlin/Gradle/KSP）**，用户 2026-08-18 批准。AGP 8.13.0 / Kotlin 2.2.0 / Gradle 8.14.3 / KSP 2.2.0-2.0.2 均维持不变。

---

## DEC-002: 应用锁关闭时数据库解锁策略（D-1）

- 日期：2026-08-18
- 决定：**锁可关，DB 仍加密**。应用锁关闭时，用无认证要求的备用 Keystore 密钥（`minibook_master_key_plain`）自动解密打开数据库；DB 整体保持加密。密钥体系为**一把 master key + 两把包裹 key**：`minibook_master_key_auth`（锁开启，`setUserAuthenticationRequired(true)`）+ `minibook_master_key_plain`（锁关闭，无认证要求），两把 key 均加密同一 SQLCipher master key；切换锁状态时用当前 key 重加密并覆盖 `minibook_key.bin`。
- 原因：用户关闭应用锁不应牺牲数据加密（关锁≠明文）；Keystore 双别名可让「锁」成为可开关的独立体验，而不触碰 DB 加密底线。
- 影响：ARCHITECTURE §7.2/7.3/7.4 密钥体系与冷启动流程；`KeyManager` 需维护两个 alias；`minibook_key.bin` 存于私有目录且随锁状态切换重写。
- 替代方案：关锁即降级为明文（弃：违反 PRD 加密定位）；关锁仍强制生物识别（弃：无锁时不打扰）。
- 最终选择：**双包裹 key 方案**，如决定所述。

---

## DEC-003: 设备无系统锁屏时的处理（D-2）

- 日期：2026-08-18
- 决定：设备无系统锁屏（`isDeviceSecure()` 为 false）且应用锁开启时，引导用户去设置锁屏，**不降级为明文**。
- 原因：auth-gated Keystore key 依赖系统锁屏支撑「设备凭据/生物识别」认证；无锁屏强行开库只能走明文/弱保护，违背安全默认。
- 影响：ARCHITECTURE §7.4 冷启动分诊 B；`isDeviceSecure` 检测与引导提示（PRD F-05 异常处理）。
- 替代方案：降级为无认证 Keystore key 自动开库（弃：与 D-1 关锁降级语义冲突，开启应用锁却无保护）；禁止使用应用（弃：过度严苛）。
- 最终选择：**引导设锁屏，不降级明文**。

---

## DEC-004: 旧版（Taro）导出 JSON 兼容导入（D-3）

- 日期：2026-08-18
- 决定：导入时检测缺 `ledgers` 字段的 legacy JSON → 按「导入到默认账本」处理（每行补默认账本 ledgerId，legacy 时间戳归一化为 `yyyy-MM-dd`）。
- 原因：旧版（Taro/Dexie）用户升级需平滑迁移数据；新格式以 `ledgers` 字段的有无即可判别新旧版本。
- 影响：ARCHITECTURE §10.2 / API.md §11.1 导入校验分支；`ImportAllUseCase` legacy 归一化逻辑。
- 替代方案：拒绝旧格式强制走中间格式（弃：升级体验差）；永远支持全时间戳替代方案（弃：与整数分/日期规范冲突）。
- 最终选择：**检测 `ledgers` 字段缺失即 legacy 分支**。

---

## DEC-005: 导出文件是否加密（D-4）

- 日期：2026-08-18
- 决定：导出为**明文 JSON**（SAF 用户自管文件）；加密导出留待 Phase 2 可选增强。
- 原因：明文本地接入系统文件选择器、可读可人工 inspect；MVP 聚焦迁移可用性；加密备份属增强项。
- 影响：ARCHITECTURE §10.1 / API.md §11；Exporter 不含加密环节；Phase 2 可在格式外层套 AES-GCM。
- 替代方案：导出即加密（弃：MVP 复杂度增加、人工 inspect 不便）；导出可选加密（弃：MVP 不做选择器分支）。
- 最终选择：**明文 JSON，Phase 2 再议加密**。

---

## DEC-006: 换机/云备份路径（D-5）

- 日期：2026-08-18
- 决定：`android:allowBackup=false` + `dataExtractionRules` 排除库与 key 文件；换机迁移走「导出 JSON → 导入 JSON」路径。
- 原因：Keystore 密钥不随云备份迁移；若系统备份带走了加密库，新机将因缺密钥而无法解密。从第一天禁用应用数据备份，避免「可恢复却不可解密」的陷阱。
- 影响：ARCHITECTURE §7.5 / 导出导入为唯一跨设备迁移通道（DEC-004/005 配合）。
- 替代方案：允许备份并导出密钥 blob（弃：密钥随备份外泄违背 Keystore 设计）；允许备份、换机重设密钥（弃：数据不可恢复）。
- 最终选择：**禁止系统备份 + 应用内 JSON 迁移**。

---

## DEC-007: 内置分类是否允许改名（D-6）

- 日期：2026-08-18
- 决定：内置分类**允许改图标/颜色/名称**，但**不可删、不可改 `type` 与 `isBuiltIn`**；「其他」兜底分类**保持默认名**。
- 原因：内置分类的是否「内置」属性（删/改类型）决定种子语义与逻辑完整性，必须锁定；展示个性化（图标/颜色/名称）属用户自由，不锁定。
- 影响：DATABASE §4.3/§8.3 可改字段口径（update 只改 name/icon/color/order）；删除保护 `isBuiltIn`；「其他」order=99 兜底不变。
- 替代方案：内置分类完全不可改（弃：个性化需求）；内置分类可删（弃：破坏分类完整性，与 PRD F-03「不可删除」冲突）。
- 最终选择：**图标/颜色/名称可改，type/isBuiltIn 与删除不可**。

---

## DEC-008: 预算表唯一约束策略（D-7）

- 日期：2026-08-18
- 决定：预算「总额唯一」在 **Repository 层事务内「先删后插」** 保证；DB 不加冗重触发器。分类预算由 DB `UNIQUE(ledgerId, month, categoryId)` 约束直接保证。
- 原因：SQLite 中 `(ledgerId, month, NULL)` 的 NULL 彼此视为不同，UNIQUE 无法约束「月度总预算」（categoryId=NULL）唯一；加触发器复杂且难迁移，业务层先删后插简单可靠。
- 影响：DATABASE §4.5 唯一性说明；`BudgetDao.deleteTotal` + upsert 成对出现；Repository 事务边界。
- 替代方案：DB 触发器（弃：ROOM/迁移复杂度）；哨兵值替代 NULL（弃：语义污染，categoryId 是真实外键）。
- 最终选择：**Repository 先删后插 + DB 分类唯一约束**。

---

## DEC-009: 多模块拆分时机（D-8）

- 日期：2026-08-18
- 决定：MVP **单 Gradle 模块** + 包边界预留（`com.minibook.domain.*`/`data.*`/`ui.*`）；出现双依赖方后再拆独立模块。
- 原因：单模块避免过早引入多模块构建复杂度；包边界即模块边界，未来拆分包内代码几乎无需改动。
- 影响：ARCHITECTURE §3.1 目录结构；后续构建拆分成本趋近于「新开模块 + 移动包」。
- 替代方案：MVP 即多模块（弃：构建复杂度前置、收益不明）；MVP 内所有代码打平（弃：包边界即失去，后续拆分伤筋动骨）。
- 最终选择：**单模块、包边界预留**，DEC-001 锁定。

---

## DEC-010: Phase 2 启动：F-06/F-07/F-08 范围与验收

- 日期：2026-08-21
- 决定：Phase 2 启动，范围 = F-06 搜索 + F-07 报表统计 + F-08 预算管理（P2 优先级）。复用单套文档体系（不新建第二套文档系统），对 01_prd.md 做「决策留痕式修订」：将 §6 中 F-06/F-07/F-08 由各一句「验收标准」展开为与 F-01~F-05 对齐的「输入 / 验收标准 / 异常处理」完整结构，并将 01_prd.md 状态由「已确认」转为「评审中」。
- 原因：Phase 1（MVP）已落地，进入延展功能期；F-06/F-07/F-08 在 §4 已列但 §6 详情不足，无法作为下游 Architect/Designer/Developer 的可执行输入。而 01_prd.md 现为「已确认」状态，按 document_rules §状态流转「已确认文档修改需先记录到 07_decisions.md」，故先记本决策再动正文。
- 影响：01_prd.md 状态转「评审中」、最后更新改 2026-08-21；§6 新增 F-06/F-07/F-08 的完整「输入/验收标准/异常处理」；新增需求需评审通过后方可转回「已确认」并作为下游输入。金额口径、账本范围、预算口径均沿用既有 F-01/F-11/DEC-008 的已拍板口径，不新增口径。实现层技术口径（图表绘制、提醒形态等）由 Architect 后续 DEC-011~013 决策，本决策不越界锁定。
- 替代方案：新建第二套文档承载 Phase 2 需求（弃：文档碎片化，与现有 00-09 单套编号体系冲突）；直接改 01_prd.md 而不记 DEC（弃：违反 document_rules 状态流转铁律）；留白不展开、由 Architect 自行补全（弃：PM 越界失职，「要什么」必须 PM 给全）；对 01_prd.md 原地改但不降状态为「评审中」（弃：新增需求未经评审即视为「已确认」会误导下游）。
- 最终选择：**复用单套文档 + DEC-010 决策留痕 + 01_prd.md 决策留痕式修订并转「评审中」**。

---

## DEC-011: F-07 图表绘制方案（Compose 原生 Canvas，不引入第三方图表库）

- 日期：2026-08-21
- 决定：F-07 报表三类图（①收支趋势 ②分类占比 ③结余走势）全部用 **Jetpack Compose 原生 Canvas** 绘制（`Canvas`/`DrawScope`：折线用 `drawPath`+`drawLine`、环形/饼图用 `drawArc`、柱状用 `drawRoundRect`），**不引入任何第三方图表库**（Vico / MPAndroidChart / Compose-Charts 等一律不引入）。
- 原因：报表仅需「折线 + 环形 + 柱状」三类基础图，Compose 原生 Canvas 完全覆盖；引入图表库构成对 DEC-001「技术栈不可改」的变更，且增加依赖体积、版本锁定与升级负担；本地离线记账应用无缩放/超长坐标轴/大数据量实时仪表盘等重交互需求，原生绘制足够且更贴合 Material 3 主题。
- 影响：新增自绘图表组件（`ui/reports/components/`，折线/环形/柱状/图例/空态）；不改 `gradle/libs.versions.toml` 与 `app/build.gradle.kts`；图表「五要素」定义并落 `02_architecture.md §Phase 2`；报表数据聚合仍走 DB 端 SQL（DEC-001 口径），Canvas 只负责渲染、不承担任何算术。
- 替代方案：引入 Vico（Compose 图表库，弃：构成 DEC-001 变更 + 依赖膨胀）；引入 MPAndroidChart（弃：传统 View 体系需 interop，与 Compose 混排）；Compose Canvas + 第三方手势库（弃：需求用不上缩放手势，更强依赖更无必要）。
- 最终选择：**Compose 原生 Canvas 自绘折线/环形/柱状，零新增图表依赖，DEC-001 技术栈不变**（不构成对 DEC-001 的变更，无需用户拍板）。

---

## DEC-012: F-08 超支提醒形态（App 内告警优先，系统通知栏列为可选项）

- 日期：2026-08-21
- 决定：超支提醒本期**只做「App 内告警」**：①预算页内进度条超支标红 + 超支文案（已用 > 预算 / 剩余 < 0）；②首页/入口加轻量入口提示（当前月存在总额超支或任一分类超支时，入口展示醒目标记/横幅，点击进预算页）。**系统通知栏列为可选项（Phase 2 默认不实现）**：如需落地须先处理 `POST_NOTIFICATIONS` 运行时权限（Android 13 / API 33+，且 API 26~32 无此权限的兼容分支）与「应用未在前台时如何判断超支」的后台触发时机权衡。
- 原因：`01_prd.md §6 F-08` 只锁「必须在本机提醒超支、不依赖云端」，未锁通知形态（明确交由 Architect DEC 决策）；App 内告警零权限、零后台调度，与「本地优先 + 最小权限面」一致；用户打开 App 即见页内红标，通知栏在本期无「后台实时算超支」的实际收益，为提醒而引入权限与后台任务得不偿失。
- 影响：预算页进度「超支=标红 + exceeds 文案」；首页/入口提示为增强项；不申请 `POST_NOTIFICATIONS`、不引入 WorkManager 后台任务；如未来需系统通知栏，另记 DEC 并评估 API 26~32 / 33+ 分支与通知渠道配置。
- 替代方案：系统通知栏 + 后台调度（弃：权限 API33+ 分支 + 后台触发时机权衡 + 无实时收益，标记为可选）；仅页内标红不做入口提示（可，作为最小实现，入口提示为增强）；每次记账后即时检查并弹系统通知（弃：与后台权衡同源，仍要 `POST_NOTIFICATIONS`）。
- 最终选择：**App 内告警优先（页内标红 + 首页/入口提示增强），系统通知栏标注为可选项**（不含权限取舍矛盾，无需用户拍板）。

---

## DEC-013: F-06 搜索 DAO 扩展方式（扩展 observeFiltered 加参，不新建 search 方法）

- 日期：2026-08-21
- 决定：F-06 搜索通过**扩展既有 `observeFiltered` 加参数**实现，**不新建独立 `search*` 方法**：`TransactionDao.observeFiltered` 增加 `remark`（备注模糊 LIKE）+ `amountMin`/`amountMax`（金额区间，单位分）；`DebtDao.observeFiltered` 增加 `partyName`（姓名模糊 LIKE）。`TransactionFilter` 数据类同步加 `remark`/`amountMin`/`amountMax`，`DebtFilter` 加 `partyName`。
- 原因：`observeFiltered` 已有「`AND (col=:param OR :param IS NULL)`」参数化写法与「Phase 2 搜索复用同一查询」的预留注释（`TransactionDao.kt` 类注释 / `TransactionFilter.kt` 注释）；扩展同一查询避免 DAO 方法膨胀与 DataSource/Repository 双路径维护；新字段可空且默认不过滤，对既有调用方零破坏。
- 影响：改动链 = `TransactionDao.observeFiltered`（8 参）/`DebtDao.observeFiltered`（4 参）加参 → `TransactionFilter`/`DebtFilter` 新增可空字段 → `RoomDataSourceImpl.observeByLedger`（transaction/debt 两处）转发新字段。`TransactionDataSource`/`DebtDataSource` 与 `TransactionRepository`/`DebtRepository` 的 `observeByLedger(ledgerId, filter)` **签名不变**（filter 为引用透明对象），Repository 层零改动。既有调用方（首页最近交易/收支页/债务页，传 null 或缺省 filter）不受影响；加参后 `TransactionFilter` 默认字段全 null == 原缺省行为。
- 替代方案：新建 `searchTransactions`/`searchDebts` DAO 方法（弃：SQL 重复、调用链分叉、DataSource/Repository 需另增方法并行维护）；新建独立 `SearchDataSource` 契约（弃：单实体查询、无跨实体聚合，layer 膨胀）；搜索做成一次性 `suspend` 查询而非 Flow（弃：与「observe 冷流 + 切账本重订阅实时刷新」既有范式不符）。
- 最终选择：**扩展 `observeFiltered` 加参 + 扩 `TransactionFilter`/`DebtFilter`，DataSource/Repository 契约签名不变**。

---

## DEC-014: 回写 04 §12.3 BudgetProgress 契约对齐实现

- 日期：2026-08-21
- 决定：回写 `docs/04_api.md` §12.3（BudgetProgress 契约）对齐实际实现：`BudgetProgress.month` 由 `YearMonth` 改为 `String`（`yyyy-MM`）；结构由「嵌套 `BudgetLineProgress`/`BudgetCategoryProgress` + `category: Category?`」扁平化为「`totalBudgetFen`/`totalUsedFen`/`categoryProgress: List<CategoryBudgetProgress>`」；分类已删除的孤儿预算由 `ObserveBudgetProgressUseCase` 跳过（不入 `categoryProgress`），取代原「null 兜底展示」表述；`observe(month: YearMonth)` 与三源 combine 口径如实写入。
- 原因：Reviewer P2-1 指出 §12.3 契约与实现漂移（`month` 类型、结构扁平化 vs 嵌套、孤儿预算跳过行为）。
- 影响：`docs/04_api.md` §12.3 正文修订（仅文档，不动代码）；`BudgetProgress`/`CategoryBudgetProgress` 派生属性（`remainingFen`/`overBudget`/`hasOverBudget`）与「未设总额预算(0)不算超支」守卫（`totalBudgetFen > 0`）如实写入；P1-1 删分类级联清预算后 observe 路径仍保留的孤儿跳过兜底行为明确。
- 替代方案：保持旧 §12.3 不动（弃：契约漂移持续误导下游 UI 实现）；反向改实现迁回嵌套结构以匹配文档（弃：实现已按 PRD F-08 / DEC-008 / DEC-012 落定并接线，应以源码为准回写文档）。
- 最终选择：**回写 §12.3 对齐实现（month=String、扁平化、孤儿跳过），不改实现**。

---

## DEC-015: 新增 F-12 微信/支付宝账单导入（PRD 落地 + 编号/事务/映射口径自决）

- 日期：2026-08-24
- 决定：
  1. 「微信/支付宝账单导入」功能编号定为 **F-12**（非 F-09）。原因：`01_prd.md` 中 F-09 已被「暗色模式」占用、F-06~F-11 均已分配（F-06/07/08 = Phase 2 搜索/报表/预算，F-09 = 暗色，F-10 = 加密，F-11 = 多账本）。为不重编号已发布/已确认功能（document_rules「编号一经确定尽量保持稳定」），顺延取 F-12。
  2. 优先级定为 **P2（Phase 2）**，与 F-06/F-07/F-08 同阶段（§4 延展功能列表）。
  3. 导入一致性口径定为 **「逐行过滤 + 单事务整批写入」**：过滤/幂等跳过（不计收支、交易关闭、已存在、解析失败）属行级语义、逐行判定并计数反馈，不算失败；通过过滤的有效行在**单事务**内整批插入，任一真实写入异常（非跳过类）整体回滚、零落库。不引入「部分成功」中间态，沿用 F-04 已确认的「单事务原子性」口径。
  4. `description` 拼接口径：商品说明（微信「商品」/支付宝「商品说明」）为主，账单「备注」非空且 ≠「/」时以 `｜` 分隔追加；拼接后超 200 字符截断；`description` 不再承载支付方式。
  5. 自动分类解析口径：映射表产出「目标内置分类名」，导入时按「当前账本内 (type, name) 精确匹配」解析为 `categoryId`；解析不到 → 回退该 type 的「其他」内置分类（「其他」名称按 DEC-007 锁定，恒可定位作兜底锚点）。
  6. `externalId` 缺失（交易订单号/交易单号为空）→ 该行按「解析失败」跳过计数。
  7. `paymentMethod` 为空/全空白 → 存 NULL。
  8. `01_prd.md` 状态由「已确认」转「评审中」（新增需求待 Architect/Designer 评审后转回「已确认」，符合 document_rules 状态流转及 DEC-010 先例「已确认文档修改先记 DEC 再动正文」）。
- 原因：F-09 编号冲突是客观既成事实（原立项编号与现有 PRD 冲突）；当前无人值守，编号/优先级/事务/映射口径等属可自决的「小项」，先自决并留痕，避免卡住；重编号已发布功能的风险大于顺延编号。
- 影响：`01_prd.md`（状态→评审中，§4/§6/§8 增 F-12）、`07_decisions.md`（本条）、`CHANGELOG.md`（[Unreleased] 记录）；后续 Architect 需在 `02_architecture.md`/`03_database.md` 落地 `transactions` 4 新列 + `UNIQUE(ledgerId, externalId)` + CSV/XLSX 解析与自动分类映射（DB 版本 1→2 迁移），Designer 落地入口与提示页 UI。
- 替代方案：重编号使账单导入占 F-09（弃：需改写 F-09 暗色/CHANGELOG/优先级多处引用并违反「已确认文档尽量不改编号」）；编号留待人工拍板（弃：无人值守且可自决，卡住无益）；写入用「逐行保序部分成功」（弃：引入半导入脏数据态、与 F-04 原子口径矛盾）。
- 最终选择：**F-12 + P2 + 逐行过滤&单事务整批写入 + 上述 description/自动分类/externalId/paymentMethod 口径**。

---

## DEC-016: F-12 微信账单 XLSX 解析路线（手动 zip+XML，不引 Apache POI）

- 日期：2026-08-24
- 决定：微信 `.xlsx` 账单解析**手动解析 zip 容器 + XML**：用 JDK 内置 `java.util.zip.ZipFile` 打开 xlsx（本质 zip），读 `xl/sharedStrings.xml`（`t="s"` 单元格反查共享字符串）与 `xl/worksheets/sheet1.xml`，用 `XmlPullParser`（`android.util.Xml.newPullParser()`，`org.xmlpull.v1` Android 内置）流式解析，**不引入 Apache POI 或任何第三方 xlsx 库**。
- 原因：① xlsx 是「zip + OOXML」公开格式，本需求只读 `sheet1` 首表 + `sharedStrings` 这一条窄路径，`ZipFile + XmlPullParser` 足够；② Apache POI（poi-ooxml，约 10MB+、全量解析模型）体积大、方法数多（对 method count 有压力），引入即构成 DEC-001「技术栈不可改」的依赖变更；③ 与 DEC-011（F-07 报表零图表库）同「能原生就不引第三方」风格，本地记账应用不值得为单一窄格式引入重库。代价：需自行处理 OOXML 细节（命名空间、sharedStrings 反查、inlineStr、日期序列数），属有限可控复杂度，以下列要点收敛并强制单测覆盖。
- 实现要点与风险（TASK-042 落地 + fixture 单测覆盖）：
  1. **命名空间**：`sheet1.xml` 根 `<worksheet xmlns=".../spreadsheetml/2006/main" ...>` 带默认命名空间。`XmlPullParser` 应一致按 localName 匹配（无前缀的 `<row>`/`<c>`/`<v>`/`<is>`/`<t>` 等 `getName()` 即 localName；属性用 `getAttributeValue(null, "r")`/`getAttributeValue(null, "t")` 取），避免被默认命名空间/前缀干扰。不要用 `r:id` 这类带前缀名匹配。
  2. **sharedStrings 反查**：`xl/sharedStrings.xml` 的 `<si><t>文本</t></si>` 单文字节点 + 富文本 `<si><r><t>text</t></r>…</si>`（需拼接多个 `<r><t>`）；`xml:space="preserve"` 的空格须保留。建立 `List<String>`（index = `<si>` 顺序），`t="s"` 单元格的 `<v>` 存下标 → 反查。微信账单表头与中文文本多走 sharedStrings。
  3. **inlineStr 兼容**：单元格可能 `t="inlineStr"`，文本直接在 `<is><t>…</t></is>`；`t="str"`（公式结果字符串）也按 `<v>` 直读。三种 cell 类型都要支持，**不能只写死 `t="s"`**。
  4. **日期序列数（PRD DEC-015）**：微信「交易时间」为 Excel 序列数（如 `46257.7435...`）。取小数点**左侧整数部分**（字符串 `substringBefore('.')`），`LocalDate.of(1899, 12, 30).plusDays(整数天数)` 得 `yyyy-MM-dd`，时分秒丢弃；全程字符串/整数拆分，**禁 Double**。序列数以 `<v>` 文本字面量读入（不 `toDouble`）。
  5. **金额**：`<v>` 数值单元格按文本字面量读入（如 `8.7`/`6.50`），交 `MoneyFormatter.toFen`（BigDecimal setScale(2)，§9.2），**不 parse 为 Double**。
  6. **列定位与稀疏 cell**：`<c r="A1" t="s"><v>…</v></c>` 的 `r` 属性前缀字母 = 列（A/B/C…）。动态表头探测（同 CSV，§16）：首行表头 cell 按列名关键词（交易时间 / 收 / 支 / 金额(元) / 交易对方 / 收付款方式 / 商品 / 备注 / 交易单号 / 交易类型 等）记列偏移。**cell 可能稀疏**（空 cell 缺 `<c>`），按 `r` 前缀列字母计算绝对列偏移，不能假设每 row 有相同数量 `<c>`。
  7. **版本漂移风险**：微信「账单明细」不同版本导出件的列名/列序可能微调——**动态表头探测不写死列序**，只匹配关键词（与 CSV 同径）。
- 影响：新增 `data/local/import/WechatXlsxParser.kt`（ZipFile+XmlPullParser）+ 单测 fixture `.xlsx`；不改 `gradle/libs.versions.toml`/`app/build.gradle.kts`（零新依赖，DEC-001 不变）。
- 替代方案：引 Apache POI（poi-ooxml，弃：体积/method count 压力 + 构成 DEC-001 依赖变更，换取一条窄路径解析不划算）；引轻量 `fastexcel`/`xlsx-streamer`（弃：仍是第三方依赖，OOXML 窄路径 ZipFile+Xml 足够）。
- 最终选择：**手动 zip+XML（ZipFile + XmlPullParser），零第三方 xlsx 库，DEC-001 技术栈不变**（不构成 DEC-001 变更，无需用户拍板）。

---

## DEC-017: F-12 账单文件读取与临时文件生命周期

- 日期：2026-08-24
- 决定：
  1. **读取口径**：XLSX 需可重入/随机读（`ZipFile` 要求可 seek 的 `File`/fd），而 SAF 返回的是 `content://` Uri（可能来自云端/外部存储 provider，不可稳定重读）→ **先经 `ContentResolver.openInputStream(uri)` 把内容流式拷贝到 `context.cacheDir` 下临时文件**（`minibook_import_<uuid>.xlsx`），再 `ZipFile(tempFile)` 打开；CSV 是顺序读，**直接 `openInputStream(uri)` 流式逐行读**（GBK `InputStreamReader`），不落临时文件。
  2. **临时文件生命周期**：仅 XLSX 产生 tempFile；无论成功/失败/用户取消，编排方在 `finally` 删除该 tempFile（`tempFile.delete()`）；前缀 `minibook_import_` 供识别，极端残留（进程被杀）不敏感（cacheDir 系统可回收）。
  3. **格式探测**：优先取 SAF `displayName` 扩展名（`.csv`→支付宝、`.xlsx`→微信）分发 `BillParserFactory`；扩展名缺失/失配时按内容嗅探兜底——文件头 `PK\x03\x04`（zip 魔数）→ xlsx，否则按文本（首行含「交易时间」等关键词）按 CSV 试解；探测失败 → 报「未识别账单格式」，零落库。
- 原因：`ZipFile` 构造需要可 seek 的本地 `File`（或 fd），SAF content Uri 不稳定可重读，落盘 cacheDir 最稳；CSV 无需 seek，流式零额外拷贝、内存友好（千条级逐行）；临时文件严格清理防 cache 膨胀。
- 影响：新增域接口 `domain/usecase/import/BillFilePreparer`（纯 Kotlin：`prepare(uriString): PreparedBillFile`，含 `displayName` + 关闭钩子）与数据层实现 `data/local/import/SafBillFilePreparer`（Android 依赖 ContentResolver/Context，是解析栈唯一触碰 Android 框架的一环，与 CSV/XLSX 解析核心解耦）；`ImportBillUseCase` 编排 finally 清理；`BillParserFactory` 接收探测结果。
- 替代方案：XLSX 用 `ContentResolver.openFileDescriptor(uri,"r")` 直接 `ZipFile(fd)`（弃：远端 provider 可能不支持 fd、生命周期难管）；CSV 也落临时文件（弃：多余 IO，顺序读无需 seek）；只认扩展名不做内容嗅探（弃：用户改后缀/无扩展名直接失败，体验差）。
- 最终选择：**XLSX 落 cacheDir 临时文件 + CSV 流式 + finally 清理 + 扩展名主/内容嗅探兜底**。

---

## DEC-018: F-12 transactions 加列与 DB version 2 迁移基线

- 日期：2026-08-24
- 决定：
  1. `transactions` 新增 4 列：`externalId TEXT NULL`、`counterparty TEXT NULL`、`paymentMethod TEXT NULL`、`source TEXT NOT NULL DEFAULT 'manual'`；新增唯一索引 `UNIQUE(ledgerId, externalId)`（索引名 `index_transactions_ledgerId_externalId`）。
  2. **数据库版本 1 → 2**：本项目**首个破坏性 schema 变更**（此前 Phase 2 搜索/报表/预算「无新表无新索引」、版本维持 1），显式 `MIGRATION_1_2` 落地，`fallbackToDestructiveMigration(false)` 不变。
  3. `source` 枚举落 `domain/model/Enums.kt` 新增 `TransactionSource{ MANUAL("manual"), WECHAT_IMPORT("wechat_import"), ALIPAY_IMPORT("alipay_import") }`，Entity 以 String 存（mapper 互转，同 type/syncStatus 既有约定）；手动记账路径 `source=manual`、`externalId/counterparty/paymentMethod=null`（domain 默认值即自动满足，`AddTransactionUseCase` 零改动）。
  4. **SQLite 唯一索引对 NULL 的语义（幂等关键）**：SQLite 中 UNIQUE 索引把 NULL 视为互异（任意多条 NULL 均不冲突）→ 手动记录 `externalId=NULL` 天然不参与 `UNIQUE(ledgerId, externalId)` 冲突，**无需部分索引（`WHERE externalId IS NOT NULL`）或触发器**，即满足 PRD「手动记录 externalId=null 不参与冲突」。
  5. 迁移 SQL（`MIGRATION_1_2` 顺序：先 4 列 `ADD COLUMN`，再 `CREATE UNIQUE INDEX`）：
     ```sql
     ALTER TABLE transactions ADD COLUMN externalId TEXT;
     ALTER TABLE transactions ADD COLUMN counterparty TEXT;
     ALTER TABLE transactions ADD COLUMN paymentMethod TEXT;
     ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'manual';
     CREATE UNIQUE INDEX index_transactions_ledgerId_externalId
         ON transactions(ledgerId, externalId);
     ```
     > `ADD COLUMN ... NOT NULL DEFAULT 'manual'`：SQLite 允许对既有行加 NOT NULL 列，前提是提供非空默认（`'manual'` 满足）。
  6. Entity 侧 `@ColumnInfo(defaultValue = "'manual'")`（source 列）+ `@Index(value=["ledgerId","externalId"], unique=true, name="index_transactions_ledgerId_externalId")`；`exportSchema=true` 生成 `2.json`，配 `MigrationTestHelper` 迁移测试。
- 原因：PRD/DEC-015 已锁「4 列 + 唯一索引」口径，本条把「落库表达」与「SQLite NULL 语义」固化，消除「唯一索引对 NULL 是否去重」的实现歧义；版本 2 是事实上的首个迁移（此前全功能叠在 version 1）。
- 影响：DATABASE §2/§4.1/§5.1/§7.5、`MiniBookDatabase`（version=2 + MIGRATION_1_2）、Entity/Domain/Mapper/Enums 四层贯通、`RoomDataSourceImpl.getById` 的 Cursor 映射补新列。
- 替代方案：externalId 用哨兵空串替代 NULL（弃：污染语义、无法与「无 externalId」区分）；部分唯一索引 `WHERE externalId IS NOT NULL`（弃：SQLite 需单独部分索引 DDL，而 NULL 本就互异、无必要）；破坏性重建（弃：违反 `fallbackToDestructiveMigration(false)`）。
- 最终选择：**加 4 列 + 普通唯一索引（NULL 互异天然达标）+ 版本 2 + 显式 MIGRATION_1_2**。

---

## DEC-019: F-12 自动分类映射与幂等计数机制

- 日期：2026-08-24
- 决定：
  1. **映射表落 domain 常量** `domain/model/BillCategoryMapping`（键 = `(platform, platformCategory 子串, type)`，值 = 目标内置分类名），内容与 PRD §F-12 表逐行一致；`platformCategory` 原始串只在解析中间模型 `ParsedBillRow` 暂存、**不落库**。
  2. **解析机制**：映射产出「目标内置分类名」→ 在**导入开始时一次性快照**「当前账本全部分类」（复用既有 `CategoryDataSource.observeByLedger(ledgerId, null).first()`）建 `Map<(type, name) → categoryId`，按 `(type, name)` 精确匹配解析；查不到 → 回退该 `type` 的「其他」内置分类（DEC-007 锁定默认名，恒可定位兜底锚点）。一次快照避免千条级逐条查库。分类为**预填**，用户可事后改名/改分类。
  3. **幂等计数机制（单事务内，配合 DEC-015「逐行过滤 + 单事务整批写入」）**：`ImportBillUseCase` 在 `TransactionRunner.runInTransaction` 事务内——① 先 `SELECT externalId ... WHERE ledgerId=? AND externalId IS NOT NULL` 取既有集合；② 有效行按文件序去重（同一 externalId 首次入列、后续计「已存在」）；③ 与既有集合相交的行计「已存在」不入列；④ 剩余行经新增 `TransactionDao.insertAll(entities)`（`@Insert(OnConflictStrategy.IGNORE)`，返回 `List<Long>`，`-1` 即被忽略）批量插入，`-1` 数量追加计「已存在」。既有集合预查与批量插入同处一个事务（SQLite 单写者无竞态），IGNORE 作残余竞态/文件内重复的最后防线。
- 原因：自动分类需可扩展且不依赖「分类名永不变」（用户可改名内置），故走「映射→内置名→(type,name) 匹配快照→兜底其他」；幂等计数需「行级语义 + 整批单事务」，事务内预查 + IGNORE 批量插入既保证计数确定、又无半导入中间态（延续 DEC-015）。
- 影响：`domain/model/BillCategoryMapping`、`TransactionDao.insertAll(IGNORE)`/`getAllExternalIds`、`TransactionDataSource.insertBatch`/`getExistingExternalIds`、`ImportBillUseCase`。
- 替代方案：逐行 insert 并 try/catch `SQLiteConstraintException`（弃：事务内逐行异常处理繁琐，且异常打断 Room 事务流程）；只预查不 IGNORE（可，但 IGNORE 作竞态/文件内重复最后防线更稳）；映射表放数据层（弃：映射是纯业务规则，domain 常量可单测、UI 可复用）。
- 最终选择：**domain 常量映射表 + (type,name) 快照匹配 + 「其他」兜底 + 事务内预查去重 + IGNORE 批量插入计数**。

---

## DEC-020: F-12 账单导入 UI 设计自决（入口 / 结果形态 / 字段展示槽 / 失败明细 / source 筛选）

- 日期：2026-08-24
- 决定：
  1. **入口位置**：首页快捷入口区（`HomeQuickEntries`，「报表/预算」并排 Row）增设第三格「导入账单」tile（emoji 📥）为**主入口**；设置（P-06）「数据」区加「导入账单」`SettingRow` 为**可选入口**；收支页（P-03）顶栏**不加入口**（`TransactionsScreen` 现无 TopAppBar，加顶栏属新增结构、非最小改动）。导航桥：`HomeNavigation` 增 `openImport()` + 根图 `Destination.Import`（`MiniBookNavHost` else 分支，与 Search/Reports/Budgets 同径）。
  2. **结果反馈形态**：用「**结果页态**」——`import` 全屏页内 `ImportUiState` 四态原地切换（Idle/Busy/Result/Error），**不用 ModalBottomSheet / 结果弹层**。理由：TASK-044 状态机 `Result(summary)` 即页面状态、返回栈单一；解析失败明细可能较多，Sheet 高度上限（max 420dp）会截断。完成/顶栏返回均 `popBackStack`，等价。
  3. **解析失败明细形态**：**折叠分组**（`SkippedBreakdownSection`，默认折叠、点表头展开逐条「第 k 行 · 原因」，≤3 条默认展开），并入结果页整体 `LazyColumn`；**不一次性平铺、不独立分页/弹层**。理由：失败明细是结果「二等公民」，先总后分；滚动列表避免 Modal 高度上限与分页复杂度。
  4. **字段展示槽**（均**不改金额语义色规则**）：
     - `counterparty`：`TransactionListItem` 副文本从「仅描述」升级为「0~2 行」——先 counterparty 后 description（各 `bodySmall` 省略）；详情/编辑态显示「对方」。
     - `paymentMethod`：列表行**不展示**（信息密度控制），仅详情/编辑态「支付方式」。
     - `source`：列表行分类名主文本行尾部 `SourceBadge`（仅 `source≠manual`，`secondaryContainer` 徽标，manual 零噪音）；详情恒显示「来源」；编辑态仅 source≠manual 时显示只读信息块。
  5. **source 筛选**：**可选增强、不阻塞 TASK-044 核心**；DEC-013 同径扩展 `TransactionFilter.source`（可空），UI 用收支页 `SourceFilterChipRow`（C-37）「全部来源/手动记录/微信/支付宝」，与类型筛选正交。
- 原因：入场口/结果形态/字段槽/失败明细均非 PRD 或 DEC 明文锁定（PRD §F-12 只锁「入口层级」「一次性反馈成功 N / 跳过 M 分原因」「解析失败给行号与原因」；ARCH §16.8 只给状态机骨架）；无人值守，属可自决的展示级细节，先自决留痕避免卡住 Developer。
- 影响：`05_ui.md §9`（P-15 + C-33~C-37 + 文案表 + 复用说明）、`CHANGELOG.md`；TASK-044 开发者落地依据（入口三选一并明确主/可选、结果页态、字段插入位、decl 失败折叠、source 筛选缓做）。
- 替代方案：收支页顶栏为主入口（弃：需新增 TopAppBar 结构）；结果用 ModalBottomSheet（弃：失败明细长被高度上限截断、返回栈复杂化）；解析失败一次性平铺（弃：淹没主结论）；source 筛选本期硬做（弃：PRD/ARCH 已标可选增强，硬做挤占 TASK-044 核心工时）。
- 最终选择：**首页 tile 主入口 + 设置行可选 + 结果页态 + 失败折叠分组 + 上述字段插槽 + source 筛选缓做**。

---

## DEC-021: F-12 测试口径自决（补测边界 / 迁移测试基建 / 真机降级与已知观察）

- 日期：2026-08-24
- 决定：
  1. **补测边界**：Developer 已交付 F-12 单测 16 例覆盖主路径；Tester 补测聚焦 PRD §F-12 验收中「Original 未覆盖分支」——DEC-017 格式探测（`BillParserFactory`）、纯文本转义（`BillText` 金额/日期/备注）、动态表头容错与列序重排（`BillHeaderMatcher`）、`TransactionSource` 枚举与 `Transaction` 新字段映射往返，共 +21 例，JVM 单测 111→132 全绿（`gradlew :app:testDebugUnitTest`）。
  2. **迁移测试基建**：`room-testing` 仅 `testImplementation`（JVM 源集）属错位——`MigrationTestHelper` 是 instrumented 依赖，故补 `androidTestImplementation(libs.androidx.room.testing)` + `androidTest` assets `srcDir("schemas")`（Room 导出的 `1.json`/`2.json` 需入测试 APK assets），并新增 `MigrationV1To2Test`；本阶段验收口径为 `:app:compileDebugAndroidTestKotlin` 绿（已绿），instrumented 执行留设备验证。
  3. **真机三项降级**：设备 MEIZU 21 未连接（`adb devices` 空），迁移保数据 / UI 结构 / 端到端导入转「遗留手动验证清单」（`09_test_report.md §F-12`），含精确点击路径与支付宝（成功 206 / 跳过 28）、微信（成功 297 / 跳过 2）预期计数；不阻塞提交。
  4. **已知观察（只报告，报 Developer 定夺）**：① androidTest 14 个反引号含空格方法名无法 DEX（D8 `Space characters in SimpleName`，P2，阻断 `connectedAndroidTest`，需重命名为无空格 ASCII）；② `BillColumn.COUNTERPARTY` 匹配用 `contains("对方")` 使「对方账号」亦命中（P3，当前靠「交易对方」列序在前而正确）；③ `SafBillFilePreparer` XLSX 临时文件名固定 `import_bill.xlsx`，与 DEC-017 设计 `minibook_import_<uuid>.xlsx` 不符（P3）。
- 原因：无人值守下明确「补测到何程度算完成」以免过度/漏测；Room 迁移只能在 instrumented 环境跑，无设备 + 存在 DEX 阻断时按「编译绿 + 设备执行留清单」闭环；真机项不可在无设备时伪造通过。
- 影响：`09_test_report.md §F-12`、`CHANGELOG.md`、`app/build.gradle.kts`、`app/src/androidTest/java/com/minibook/data/local/db/MigrationV1To2Test.kt`。
- 替代方案：JVM 用 Robolectric 跑迁移（弃：项目未引入、引入超补测边界）；真机未连仍标「已验证」（弃：造假）；Tester 直接重命名 androidTest 方法名（弃：属修复范畴，保留观察报 Developer）。

---

## DEC-022: F-13 邮件账单自动导入技术路线（IMAP 取件 / 链接下载 / 口令穷举）

- 日期：2026-09-23
- 决定：F-13「自动取邮箱账单并导入」采用下述路线，**全部结论以真机真邮箱 spike 实测为依据**（详见 `12_mail_bill_import.md` §2/§9）：
  1. **邮箱与认证**：仅支持 QQ 邮箱；`imap.qq.com:993` + SSL；认证用邮箱地址 + **16 位 IMAP 授权码**（非登录密码）；JavaMail 必须显式发送 IMAP `ID` 命令声明客户端身份（`mail.imap.id` 属性 + `IMAPStore.id(map)` 双保险，否则 QQ 拒绝）。
  2. **取件筛选**：**禁用 IMAP 服务端 SEARCH，改为「拉取近期邮件 + 本地过滤」**。依据：实测 `OR(FROM "alipay")(FROM "tencent")(SUBJECT "账单")` 在 QQ 上返回 **0 封**，而同一批邮件本地过滤命中 3 封真实账单——服务端 SEARCH 在本场景不可靠。
  3. **支付宝取件**：账单为邮件**附件** zip；附件文件名经 **RFC2047/RFC2231 编码**（JavaMail `getFileName()` 返回未解码原始串），须自行解码。
  4. **微信取件**：账单**不是附件**，正文只有下载链接（`tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?<签名 token>`）；实测 HTTPS GET 直连成功（`HTTP 200` / `application/zip` / 26318 B，token 即凭证、无需登录）；**链接有效期 7 天**，超期需回微信重新申请。
  5. **解密**：实测两平台 zip 均为 **ZipCrypto + Deflate（非 AES）**，`method=8`、`flags=0x0809/0x080B`（含 data descriptor 位）→ **不引入 zip4j**，用 Java 原生 `Inflater` + 自实现 ZipCrypto 解密（延续 DEC-016「零第三方解析库」口径）。
  6. **口令（本决策核心）**：解压口令**不随邮件发送**（支付宝在支付宝 App 服务消息、微信在【微信支付】公众号）；但实测口令恒为 **6 位纯数字且每期不同**（三封真实账单均测得命中；口令属凭据不落文档，两个支付宝包口令不同）→ 故采用「**本地自动穷举 6 位数字口令**」实现真·一键，**明确否决「用户输一次并记住」方案**（口令每期变化，记住无效）。
  7. **口令穷举算法口径**：两段式——① 快筛：由候选口令派生密钥、解 12 字节加密头，取第 12 字节比对，且**必须宽松接受「CRC32 高字节 或 DOS 时间高字节」两种约定**（实测 zip 带 data descriptor，单一约定会误杀正确口令）；② 权威判定：整包解密 → `Inflater` 解压 → 对**解压后的内容**算 CRC32 与 zip 记录值比对。**红线：绝不能拿解密后的压缩数据算 CRC32**（zip 存的是原始内容的 CRC），否则正确答案被全部误杀。性能上预计算前 3 位密钥状态 + 多核并行，目标中端机 ≤ 10 s。
  8. **降级**：穷举失败或加密类型非 ZipCrypto（AES）→ **明确报错，不得静默失败**；实现时须先探测加密类型，禁止对非 ZipCrypto 盲目穷举。**（实现回写 2026-09-23：非 ZipCrypto 已按 `ZipArchiveInspector` 探测（method 99 / extra `0x9901` → `Unsupported`，不进穷举）；穷举失败记录 `MailItemFailure.PasswordNotFound` 并在结果页按邮件提示；原设计设想的「降级为用户手动输入口令」的 UI 未实现，属后续增强——复用同一 `EncryptedZipExtractor` 路径的组件已具备，仅缺输入界面。）**
  9. **隐私边界**：IMAP **只读**（`Folder.READ_ONLY`，不删除/不标已读/不移动/不改 flag）；授权码经 Android Keystore（AES-256/GCM）加密落盘；凭据、口令、账单内容**一律不进日志**；出网点仅 `imap.qq.com:993` 与 `tenpay.wechatpay.cn:443`；临时文件只落 `cacheDir` 且 `finally` 删除。
  10. **与 F-12 的关系**：F-13 只是 F-12 的「取件自动化前置段」，**不修改** F-12 的解析规则/字段映射/过滤口径/幂等口径；解出的 `.csv`/`.xlsx` 交给既有 `ImportBillUseCase`。唯一衔接改动：为 `ImportBillUseCase` 增加**以文件路径为入口的重载**（邮件取件产物是私有目录 `File`，非 `content://` Uri）。
  11. **依赖与权限**：新增 `com.sun.mail:android-mail:1.6.7` + `android-activation:1.6.7`（约 1 MB，用户已同意）与 `INTERNET` 权限；补 R8 keep 规则。**不引入 zip4j**。
  12. **形态**：**手动「一键收取」**，不做 WorkManager 后台定时（用户 2026-09-23 选定）。
- 原因：用户诉求是「自动取邮箱邮件→下载→导入」；spike 逐环实测证明链路可行，且暴露出两个必须绕开的现实约束（QQ 服务端 SEARCH 不可靠、微信不发附件只有链接）。口令环节经实测确认「6 位数字 + 每期不同」，使得「自动穷举」成为唯一能实现真·一键的路径，同时「记住口令」方案被证伪。
- 影响：新增 `12_mail_bill_import.md`；`01_prd.md §F-13` 与 §7 离线条款；`02_architecture.md §17`；`04_api.md §14`；`05_ui.md §10`；`06_tasks.md` TASK-045~050；`CHANGELOG.md`；`libs.versions.toml` + `app/build.gradle.kts`（JavaMail + keep 规则）；`AndroidManifest.xml`（INTERNET）。
- 替代方案：
  - POP3 取件（弃：不支持服务端条件搜索，只能全量拉头本地过滤，且重复下载风险高）；
  - 邮箱厂商 SDK / OAuth2（弃：QQ 个人邮箱不开放第三方 OAuth2 IMAP；厂商 SDK 不通用）；
  - **已知明文攻击（bkcrack）绕开口令**（弃：实测两条目均为 Deflate 压缩，已知明文攻击要求提供**压缩后字节流**，而首块为动态 Huffman、压缩流不可预测；且本次无「同文件密文+明文」配对）→ **本条留档以免后续重复尝试**；
  - 「用户输一次口令并记住」（弃：实测口令每期不同，记住无效）；
  - 引入 zip4j 解压（弃：实测为 ZipCrypto 非 AES，原生 `Inflater` + 自实现即可，避免多余依赖）；
  - 把取件文件包成 `FileProvider` 的 `content://` Uri 以复用原导入入口（弃：成本更高且无必要地扩大 provider 暴露面）；
  - 依赖服务端 SEARCH 筛选（弃：实测返回 0，不可靠）。
- 最终选择：**如决定所述——QQ IMAP 授权码 + 本地过滤 + 支付宝取附件/微信取链接 + ZipCrypto 6 位数字口令自动穷举 + 复用 F-12 导入链路 + 手动一键收取**。
- 实施状态（2026-09-23 回写）：**已实现并通过本地构建 + 全量单测（真机验收待设备）**。契约固化见 `04_api.md §14`；已知限制与待真机清单见 `04_api.md §14.9`（含：编排 UseCase 无直接 JVM 单测、真机端到端与只读性待设备复核、release 构建未验证）。
- 附：方法学留痕（本次两次真实误判，均因工具未先经已知答案验证）
  1. **校验字节约定误设**：初期要求解密头末两字节同时匹配「CRC 高字节 + 时间高字节」，在 data descriptor 场景下会误杀正确口令；已改为「任一命中 + 整体 CRC 权威判定」。
  2. **CRC 比对口径错误**：初期拿**解密后的压缩数据**算 CRC32 比对 zip 记录的 CRC（实为**原始内容**的 CRC），导致正确答案全部被误杀，并一度得出「口令不是 6 位数字」的**错误结论**。该 bug 由「自造已知口令 zip + Python `zipfile` 独立实现交叉验证」暴露。
  - **固化要求：自研解析/爆破类工具在采信其否定结论前，必须先用已知答案做交叉验证。**

---

## DEC-023: F-13 引入网络与外部凭据的隐私/权限边界修订

- 日期：2026-09-23
- 决定：
  1. **修订 `01_prd.md §7 离线可用`**：原文「所有功能离线可用，无网络依赖（本期无云端）」改为「**除 F-13 邮箱账单拉取外**，所有功能离线可用」——F-13 是本项目**首个引入网络与外部凭据**的功能，对原条款做**显式例外化**，而非默认放宽。
  2. **网络面收敛为「单向拉取」**：仅 `imap.qq.com:993`（TLS）取邮件、`tenpay.wechatpay.cn:443`（TLS）下载账单 zip；**不上传任何用户数据**；不接入任何统计/崩溃上报/广告 SDK；不做后台联网（仅用户点击时执行）。
  3. **凭据等级对齐**：邮箱授权码与 SQLCipher master key **同级对待**——Keystore（AES-256/GCM）加密落盘、不落明文、不进日志（含异常堆栈）；设置页提供「清除邮箱凭据」一键抹除。
  4. **邮箱操作只读**：`Folder.READ_ONLY`，不删除、不标已读、不移动、不修改任何 flag；非账单邮件不下载正文、不落盘。
  5. **权限**：`AndroidManifest.xml` 新增 `android.permission.INTERNET`（当前清单为零权限）。这是本项目**首次申请权限**，需同步更新隐私说明/合规文案（「网络访问 + 邮箱凭据」条目）。
  6. **可恢复性**：断网、认证失败、链接过期等一切网络侧异常**不得影响 F-12 手动导入路径**，两者完全独立。
- 原因：项目定位为「本地优先 / 隐私优先」，且 `RemoteDataSource` 至今仅为契约占位、「云端同步本期不做」。F-13 虽为必要功能，但确实打开了网络与外部凭据两个面，必须在文档层把边界写死，避免后续以「反正已经联网了」为由无约束扩张网络面。无人值守下由 Architect 自决并留痕。
- 影响：`01_prd.md §7`；`AndroidManifest.xml`；`libs.versions.toml`/`app/build.gradle.kts`；隐私说明文案；`12_mail_bill_import.md §4`。
- 替代方案：维持原「零网络」条款并把 F-13 排除在文档外（弃：文档与实现不符，属隐患）；把 F-13 归入「云端同步」范畴一并推迟（弃：用户明确要求本期做，且它并非数据上云，只是单向取件）；不做例外说明只加权限（弃：违反「文档即契约」口径）。
- 最终选择：**显式例外化 + 单向拉取 + 凭据最高等级保护 + 只读邮箱 + 首次申请 INTERNET 权限**。
- 实施状态（2026-09-23 回写）：**已实现并通过本地构建 + 全量单测 + 真机端到端验收**——INTERNET 权限已入清单（项目首个权限）、授权码 Keystore AES-256/GCM 独立别名加密落 `noBackupFilesDir`、IMAP `READ_ONLY` + `close(false)`、下载与 IMAP 全程 TLS、异常文案不含凭据/token；真机 A1B2C3D4 实收 5 封账单邮件（微信 2 / 支付宝 3）全部成功、失败 0，日志检索无凭据/链接 token 泄漏。**剩余**：隐私说明/合规文案的补充待后续（`\Seen` 状态未逐字段核对，见 `04_api.md §14.9`）。

---

## DEC-024: F-13 解析关键路径「零正则」与 Android ICU 正则语义教训

- 日期：2026-09-23
- 决定：
  1. **F-13 邮件解析关键路径禁止使用 `java.util.regex`**，改为手写字符扫描：`MailHtmlText`（引号感知的标签/属性扫描，取 `href`/`src`）、`WechatBillLinkExtractor`（URL 扫描 + host 精确判定 `tenpay.wechatpay.cn`）、`MailAccountCodec`（凭据 JSON 编解码 + 邮箱格式校验）。
  2. **以 `IcuSafeRegexTest` 绊线测试守护**：扫描 F-13 源码中的 `Regex(` 危险字面量（如 `\}` 等 ICU 不接受、JDK 接受的转义），防回归。
  3. 该约束仅适用于 F-13 解析层（失败代价 = 闪退 / 凭据丢失 / 导入失败），不强制推广到全仓。
- 原因（真机实测教训）：Android 的 `java.util.regex` 底层是 **ICU**，与 JVM 语义存在差异。实测 `MailAccountCodec` 条目正则含 `\}` 时 **JVM 单测全绿**（JDK 接受 `}` 转义），但真机在**类初始化**阶段抛 `PatternSyntaxException` → `ExceptionInInitializerError`（属 `Error`，`catch (Exception)` 兜不住）→ 点「保存」即闪退；且迁移已把旧授权码文件改名而 json 未写出（凭据险些丢失）。此类缺陷 **JVM 单测无法覆盖**（ICU4J 不暴露其正则引擎，本地无法复现 ICU 语义）——这正是「纯 JVM 绿 ≠ 真机安全」的实证。
- 影响：`data/local/mail/MailHtmlText.kt`、`WechatBillLinkExtractor.kt`、`MailAccountCodec.kt`、`app/src/test/.../IcuSafeRegexTest.kt`；`04_api.md §14.1/§14.4`；`CHANGELOG.md`。
- 替代方案：继续用正则但逐个真机验证（弃：每条正则一次真机往返，且失败点在类初始化、代价与风险都高）；引入 ICU4J 做等价测试（弃：ICU4J 不暴露其正则引擎，无法复现，且新增依赖）；仅加 `try/catch (Throwable)` 兜底（部分采纳：读路径已改 `catch (Throwable)` + 写后**回读校验** + 失败回滚，但兜底不消除根因）。
- 最终选择：**关键路径零正则 + ICU 绊线测试 + 迁移读路径兜底与回读校验**三重防线。
- 诚实记录：本轮另有一次「微信账单取不到下载链接」故障，最初推断为「旧正则在 ICU 上匹配失败」；真机诊断（旧正则匹配次数、HTML 是否含链接、去标签后链接是否存活）证明**旧正则在真机 ICU 上匹配正常**，该推断**不成立**；确切根因已不可回溯（旧 APK 与旧文件被覆盖、且未提交版本历史）。零正则重写保留为永久防线，但**不得声称它是该故障的确切根因修复**。

---

## DEC-025: F-14 短信账单导入技术路线（读取 / 解析 / 去重 / 自动化四层口径）

- 日期：2026-10-09
- 决定：
  1. **通道定位**：F-14 是 F-12 的**第三条取件通道**（文件 / 邮件 / 短信并列），解析输出统一 `ParsedBillRow`、入库统一 `ImportBillUseCase.importPrepared`（**不新建第二条写库路径**）。
  2. **读取层**：只读 `content://sms`（`READ_SMS`）；分页游标 `date ASC, _id ASC` + 水位 `max(_id)`/`max(date)` + 「`_id` 与内容哈希」双键去重；**不申请默认短信应用角色**（非默认 App 同样可收 `SMS_RECEIVED`）。
  3. **解析层**：模板 = 发送方号码 + 抬头锚点 + 结构化捕获（**禁止整句正则**）；`externalId = sms:<sha256(发送方|日期|规范化正文)[:16]>`（短信普遍无交易单号）；**未识别必留痕**（清单 + 可导出 fixture）。
  4. **去重层（本决策核心取舍）**：**不加数据库字段**，跨源重复按「同账本 + 同方向 + 金额相等(分) + 日期(天)相同」匹配 → **唯一候选自动判重、多候选人工确认、永不自动删**；同一个匹配器被短信与账单文件**两个入口双向调用**（顺序无关）。**已知盲区**：跨天错位（23:59 / 次日 00:01、银行记账日 ≠ 支付日）会漏判，二期以 `occurredAt` 列（v4 迁移）+ ±10 分钟窗口解决。
  5. **自动化层**：L0 手动扫描（底座）+ L3 水位补扫（完整性保证）+ L1 `SMS_RECEIVED` 待确认（默认**静默聚合**）+ L2 白名单自动记账（**四条件同时成立**：白名单 + 模板命中 + 判无重复 + 渠道允许自动）；白名单「手动确认 3 次且无纠错」晋级、纠错即降级、逐项可关。
  6. **不常驻**：不申请前台服务/保活；广播按需拉起（`goAsync` ≤10s），进程被杀由 L3 补扫兜底。
- 原因：用户第一诉求「不遗漏」，同时要求「不烦人」「不重复入账」。权衡：① 分钟级时间窗需 schema 变更，而**历史账单已无时间**（时分秒在 F-12 解析阶段即被丢弃），收益有限成本明确 → 本期不加字段，把盲区写进文档；② 全自动写入存在「陌生号码误记」风险，且**应用锁开启时后台拿不到 DB master key**（代码核实：`KeyManager` 双别名 + `AppLockController.unlock(useAuth)`），故以「白名单 + 四条件」限定自动范围。
- 影响：`01_prd.md §F-14`；`02_architecture.md §18`；`04_api.md §15`；`05_ui.md §11`；`06_tasks.md TASK-051~057`；`AndroidManifest.xml`；`Enums.kt`（`TransactionSource.SMS_IMPORT`）；`13_sms_bill_import.md`。
- 替代方案：① 无条件自动记账（弃：误记风险 + 锁开启不可用）；② 先加 `occurredAt` 再实现（弃：历史无时间、收益低且拖长交付；保留为二期）；③ 以「通知监听」覆盖应用内通知（弃：本机通知使用权已被占用、侧载受限设置成本高，且应用内通知属 F-12/F-13 覆盖范围）；④ 抢默认短信应用以获得 `SMS_DELIVER`（弃：非必要且侵入用户系统设置）。
- 最终选择：**不加字段按天匹配 + 双向共用匹配器 + 白名单四条件自动 + 默认静默聚合 + 不常驻**。
- 实施状态：**设计已定稿（2026-10-09），代码待实现**（TASK-051~057）。**验证缺口如实登记**：无真实短信账单样本，解析/匹配以 fixture 单测为准；真机验收须先向用户申请。

---

## DEC-026: F-14 引入系统敏感权限（短信类）的边界修订

- 日期：2026-10-09
- 决定：
  1. **修订 DEC-023 第 5 条的表述**（原文称「首次申请权限 = INTERNET」）：F-14 引入本项目**首个系统敏感权限组**（`READ_SMS` / `RECEIVE_SMS`）+ `POST_NOTIFICATIONS`。
  2. **默认零申请**：三项权限**只在用户显式开启对应开关后**才申请；总开关关闭时零运行时权限请求。
  3. **可独立拒绝 + 逐项降级**：任一权限缺失只降级对应能力（`READ_SMS`→无扫描；`RECEIVE_SMS`→无实时；`POST_NOTIFICATIONS`→无通知），其余功能不受影响；全部被拒时仍有 **L4「分享导入」（零权限）** 可用。
  4. **短信内容最小化**：识别成功的短信**原文不落库**（只存抽取结果）；未识别样本本地保留默认 **30 天**、可一键清空/关闭保留；日志与通知**禁止**出现原文、卡号全文、余额；去重只依赖内容哈希。
  5. **系统行为只读**：不删除/修改/标记已读任何短信；**不申请默认短信应用角色**（`ROLE_SMS` 保持 `com.android.mms`）。
  6. **零上云**：不新增任何出网点（与 DEC-023 的「单向拉取」口径一致，F-14 完全离线）。
- 原因：短信是本机最敏感的数据之一。项目定位「本地优先 / 隐私优先」，F-14 虽必须读短信，但须做到「默认不申请、可拒、可降级、原文不落库、零上云」，并把边界写进文档，避免后续以「反正已经在读短信了」为由扩张。
- 影响：`AndroidManifest.xml`（3 权限 + `SMS_RECEIVED` receiver）；`01_prd.md §F-14 与 §7 离线可用`；`05_ui.md` 隐私说明；`README.md` 权限与隐私表；`04_api.md §15`；`13_sms_bill_import.md §4`。
- 替代方案：只做 L4 分享导入（弃：用户明确要求自动识别与自动记账，短信通道价值就在"不用手动"）；权限申请与总开关解耦（弃：违背「默认零申请」最小化原则）；未识别原文一并丢弃（弃：破坏「未识别必留痕 / 可补模板」闭环）。
- 最终选择：**默认零申请 + 可独立拒绝 + 逐项降级 + 原文不落库（未识别限期保留）+ 不抢默认短信应用 + 零上云**。
- 实施状态：**设计已定稿（2026-10-09），代码待实现**（TASK-051 起）。

---

## DEC-027: F-14 真机/模拟器验收后的口径修正（渠道兜底 / 后台开库与写库前提 / 权限读取时机 / ROM 受限视图）

- 日期：2026-10-10
- 背景：用户授权真机测试后，在**小米 `某机型`（Android 16 / HyperOS）**与 **AOSP `Pixel_7`（Android 15，`adb emu sms send` 注入真实 PDU）**两端完成验收。实测暴露 5 个「编译通过 + JVM 单测全绿 + 真机静默失效」的缺陷（详见 `13_sms_bill_import.md §14.3`），据此修正四条口径。
- 决定：
  1. **银行模板的兜底渠道 = `BANK_DIRECT`**（而非 `OTHER`）。命中银行模板（95xxx 服务号 / 银行强锚点）但正文无第三方渠道提示词（银联/网银在线/代扣…）的短信，一律归 `BANK_DIRECT`。理由：`SmsChannel.allowsAutoImport` 只认 `BANK_DIRECT`，兜底 `OTHER` 会让 **L2 自动记账在最常见场景永不触发**（静默失效）。渠道抑制的本意是防 Alipay/WeChat 被账单文件覆盖，不是拦银行直连通知。
  2. **后台接收器的写库前提 = 三道闸全过**：`enabled && autoImportEnabled && !appLockEnabled`（统一口径 `SmsSettings.autoImportUsable`）**且**后台静默开库成功。`SMS_RECEIVED` 会拉起**新进程**，此时 `DatabaseGate` 未 open，任何 DB 访问都会抛 `IllegalStateException`；处置：应用锁关闭时按 DEC-002「锁关路径」静默开库（与关锁启动同一路径），应用锁开启时绝不开库。`SmsBillImportUseCase.handleIncoming` 增加兜底：DB 未就绪 → 降级「待确认」，**绝不抛异常、绝不丢件**。
  3. **权限值一律当场复查**：禁用组合期快照（点击时 + `onResume` 复查）。组合期快照会让「去系统设置授权 → 返回重试」永久卡在引导页。
  4. **接受 ROM 受限视图为能力边界**：MIUI/HyperOS 对非默认短信应用暴露 `sms_restricted`（过滤 `sp_type≠0` 的服务/通知类会话），本机 App 可见 1 条 / shell 可见 9 条。决定：不申请默认短信应用角色（沿用 DEC-026 第 5 条）；**oracle 对齐（shell 计数 = App 计数）不再作为漏读判据**；L4 分享导入升为兜底优先级。
- 原因：优先级的第 ① 条「不遗漏」与第 ③ 条「不记错」都要求把「分母由谁决定」「什么条件下才允许写库」写成显式口径；这四条若不落文档，后续维护者会重复踩坑（尤其缺陷 D/E 的症状是「没有日志、没有通知、什么都不发生」）。
- 影响：`13_sms_bill_import.md`（§7 R11、§12.1、§14、§13.2/13.3）；`09_test_report.md`（F-14 验收节）；`06_tasks.md`（TASK-057 收口）；`CHANGELOG.md`（Fixed 条目）；`README.md`（测试数字）。
- 替代方案：兜底 `OTHER` 但允许 `allowsAutoImport`（弃：会让未知渠道也自动写库，违背「不记错」）；后台不开库、只累计待确认（弃：等于 L2 只在 App 前台可用，与设计承诺不符）；申请默认短信应用角色以取得完整视图（弃：隐私与架构成本过高，且需用户让出系统默认应用）。
- 最终选择：**银行兜底 `BANK_DIRECT` + 写库三道闸（含后台静默开库）+ DB 未就绪降级待确认 + 权限当场复查 + 接受 ROM 受限视图并上调 L4 优先级**。
- 实施状态：**已实现并复测通过（2026-10-10）**：模拟器端 L2 后台自动记账实测 `自动 1，待确认 0`；真机端授权后不重启即进入扫描；JVM 全量 52 类 / 406 例 0 失败。

---

## DEC-028: F-14 待确认可见性口径（补扫触发点 / 水位 / 可见出口）

- 日期：2026-10-10（**同日下午修订：撤销角标，见文末「修订」**）
- 背景：DEC-027 的验收覆盖了「会不会写错」，但没覆盖「用户能不能发现」。补做「杀进程 → 注入未白名单发送方的银行短信 → 开 App」后发现：默认通知方式（**静默聚合**，不弹通知）下，App 内**没有任何提示**。数据没丢（短信仍在收件箱，L0 全量扫描能重新识别，已实测），但「发现」环节断了——而「不会丢」不等于「不会漏」。据此定下四条口径（缺陷 F1/F2，证据见 `13_sms_bill_import.md §14.6`）。
- 决定：
  1. **补扫触发点 = 打开 App**（首页 `HomeViewModel.init`），与设置项文案「打开 App 时自动补扫」一致；导入页那次调用保留（幂等）。禁止再出现「开关文案与触发点不一致」。（**修订后保留**）
  2. **水位只在「已处理」的短信上推进**：自动入账 / 非账单 → 推进；待确认（未识别 / 未白名单 / 数据库未解锁）→ **不推进**（`advanceWatermark` 仅在 `advanceTo > 水位` 时下发）。**（修订后撤销：回到「读到即推进」）**
  3. **待确认必须有可见出口**：`SmsPendingTracker`（进程内 `StateFlow` 计数、键 = 幂等键 `sms:<hash16>` 或未识别 `msg:<_id>`、按内容去重）+ 首页「导入账单」入口角标；写入成功 / 用户确认 / 已存在 → 立刻销账。**不落库、不新增 schema**（DEC-026 隐私边界不变）。**（修订后撤销：跟踪器与角标全部删除）**
  4. **角标必须可读**：不得依赖语义不明的主题色（首版用 `colorScheme.onError`，本项目自定义配色未定义它，实测红底上数字不可见）；显式白色 + `defaultMinSize` + 无障碍描述「短信待确认 N 条」。**（随角标一并撤销）**
- 原因：优先级②「不烦人」的反面不是「多提醒」，而是「该提醒时没有任何提示」——**静默聚合的前提是有地方看得见**。不落库是隐私与成本约束，改用「水位不越界 + 每次补扫重新发现」换取角标重启自愈，避免为了一个计数把短信元数据写进库。
- 影响：新增 `SmsPendingTracker.kt` + `SmsPendingTrackerTest.kt`（7 例）；`SmsBillImportUseCase`（水位口径 + 各分支销账/记账）；`HomeViewModel`（补扫 + 计数出口）；`HomeScreen`（角标）；`13_sms_bill_import.md §12.2/§14.6`；`09_test_report.md`；`06_tasks.md`（TASK-058）；`CHANGELOG.md`；`README.md`。
- 替代方案：角标计数落库（弃：为一个提示引入新表/字段，且把待确认状态与短信内容耦合）；每次开 App 全量扫描（弃：与水位设计冲突，耗时/耗电上升，`MAX_MESSAGES` 上限下行为退化）；把默认通知改为「逐条提醒」（弃：默认静默是已批准的最小打扰口径，改默认属产品决策，须用户拍板）。
- 最终选择（原）：**打开 App 即补扫 + 水位不越过待确认 + 进程内角标（自愈、不落库）+ 角标必须可读**。
- 实施状态：原方案曾实现并在模拟器复测通过（角标自愈、L0 确认后消失、JVM 53 类 / 413 例）；**修订后已按下方结论回退**：JVM **52 类 / 406 例** 0 失败，两端真机/模拟器复验角标已消失（2026-10-10 10:45–10:47）。

### 修订（2026-10-10 下午）：撤销角标，改「三层兜底」

- 触发（缺陷 F3）：真机角标常驻「短信待确认 1 条」去不掉。根因：未识别条目键为 `msg:<短信_id>`，而销账路径只有「确认写入」（只覆盖已解析短信的幂等键）与「已存在/写入生效」；水位又不越过它 ⇒ 每次开 App 重新计数，永久常驻（违反优先级②）。
- 用户拍板（原话）：「**角标不要了，不需要出现已读未读的，能接收到就记账，有 App 打开扫描兜底，还有最后手动导入兜底**」。
- 修订结论（**取代上述第 2/3/4 条**）：
  1. **不做角标、不做任何「待确认 / 已读」状态**；删除 `SmsPendingTracker`（含单测）及全部销账/记账调用与 UI 接线。
  2. **水位回到「读到即推进」**：补扫只兜「广播丢失」的增量，不再承担「反复发现未处理短信」的职责。
  3. **最终模型 = 三层兜底**：① 到达即记账（L1/L2，白名单四条件 + 应用锁门控）→ ② 打开 App 补扫（L3 增量）→ ③ 手动导入（L0 全量干跑 + 确认写入，最终兜底）。
  4. 「不遗漏」由第 ③ 层保证：L0 全量扫描**与水位无关**，永远能列出可导入 / 未识别 / 重复；代价是**不再主动提示**，用户需自行进导入页（这是用户明确选择的取舍）。
  5. 补扫的 `pending` 计数**仅作日志口径**，不驱动任何 UI，不得再被解读为「待办清单」。
- 修订影响：删除 `SmsPendingTracker.kt` / `SmsPendingTrackerTest.kt`；`SmsBillImportUseCase`（构造参数、跟踪调用、水位口径回退）；`HomeViewModel`（去掉计数出口，**保留** L3 触发与日志）；`HomeScreen`（去掉角标与无用 import）；F-14 测试 8 类/80 例 → **7 类/73 例**，全量 53/413 → **52/406**；`13_sms_bill_import.md §12.2/§14.6.4`；`09_test_report.md`；`06_tasks.md`；`CHANGELOG.md`；`README.md`。

---

## DEC-029: 验收后 UI 信息架构改版 + 未识别「已看过」水位

- 日期：2026-10-10（下午，用户三点要求，验收后直接改版）
- 背景：F-14 验收收口后，用户提出三条使用体感问题（原话）：
  1. 「能不能把扫描过的做标记，否则每次重新扫描都有之前未处理的」
  2. 「导入账单页面改一下，上面区分微信支付宝账单的不要了，只留下三个按钮选项，三个按钮上面简短的说明，然后点击进入不同的功能。」
  3. 「设置页面太长了，每种设置设计汇总，然后点击进入二级页面设置。」
  第①条是「撤销角标」的必然补位：没有角标就没有主动提示，用户每次进导入页都看见同一批未识别短信（真机上就是那条快递短信），
  噪音感来自「没有区分新旧」，而不是「存在未识别」。
- 决定：
  1. **未识别「已看过」标记 = 单一时间戳水位**（`SmsSettingsStore.unrecognized_ack_ms`，只存时间戳，不含内容/号码/发送方 ——
     DEC-026 边界不变）；扫描时 `receivedAtMs <= 水位` → `SmsPendingItem.seen = true`。
  2. **推进时机 = 清单实际展示过之后**：`ImportViewModel.onReadSmsBills` 在干跑预览上屏时调用
     `acknowledgeUnrecognized(preview.unrecognized)`（推进到本次未识别条目最新时间戳）；**待确认清单页不推进**（它是「看全部」的地方）。
  3. **只影响展示分组，不动任何计数**：`未识别 N` 仍按全量呈现、`SmsScanPreview` 闭合断言不变；「已看过」条目折叠为
     「▸ 此前已看过 N 条（点击展开）」，展开可逐条看；**不删除、不过滤、不落库**，手工记一笔仍在待确认页 ⇒ 违反优先级①的风险为零。
  4. **导入页 = 三个入口**（账单文件 / 邮箱账单 / 短信账单），每个入口 = 标题 + 一行简短说明 + 按钮；微信/支付宝导出指引**折叠**
     为「如何导出账单？」（文案不丢）；返回语义改为逐层退（结果态回三入口页，入口页才退出）。
  5. **设置页 = 分组汇总页 + 四个二级页**（`settings-appearance` / `settings-security` / `settings-data` / `settings-sms`），
     每行显示一行当前状态汇总；账本仍走既有 `ledger-manage`；版本号降为页脚（原「关于」只有一行，不值得一次跳转）。
     二级页在根图而设置首页在 5 Tab 嵌套图内 ⇒ 新增 `LocalSettingsNavigation`（同 `LocalHomeNavigation` 口径），
     `MainScreen` 移除 `onNavigateToImport` / `onNavigateToMailAccount` 参数穿透。
- 原因：三条都指向同一件事——**信息要在正确的层级上出现**。未识别的「新旧」该由一次水位表达（而不是靠用户记忆），
  导入方式该在入口层只留「怎么导」（而不是先教微信支付宝怎么导出），设置该在首页只留「有哪些分组 + 现在是什么状态」。
  用时间戳而非逐条 key：未识别条目本身每次全量重扫派生、不落库，逐条 key 会无限膨胀并需额外存哈希；
  代价（同毫秒到达且上次已展示过会误标已看过）由折叠区展开 + 待确认清单 + 手动导入三层兜底，不丢件。
- 影响：`SmsSettingsStore`（新增 `unrecognized_ack_ms` + `acknowledgeUnrecognized` / `unrecognizedAckMs`）；
  `SmsModels`（`SmsPendingItem.seen`，默认 false）；`SmsBillImportUseCase`（`scan()` 标记 + `acknowledgeUnrecognized()`）；
  `ImportViewModel`（预览上屏后推进水位）；`SmsImportPresenter`（`lines()` 只出未看过 + `seenLines()` / `unrecognizedLine()`）；
  `SmsImportContent`（折叠区 UI）；`ImportScreen` / `ImportComponents`（三入口页 + 折叠导出指引 + 返回语义 + `BackHandler`）；
  `SettingsScreen`（重写为汇总页）+ 新增 `SettingsAppearanceScreen` / `SettingsSecurityScreen` / `SettingsDataScreen` /
  `SettingsSmsScreen` / `SettingsNavigation`；`Destinations`（4 条新 route）；`MiniBookNavHost`（4 个 composable + 注入桥）；
  `MainScreen`（去掉 2 个参数）；`ui/components/Settings.kt`（`TrailingValue` / `TrailingChevron` / `SettingsSubPageTopBar` 提取）；
  `SmsSettings.kt`（去掉「+ 角标」注释）；`SmsNotifyMode.displayName()` 由 private 提为 internal。
  测试：F-14 7 类/73 例 → **7 类/75 例**，全量 52/406 → **52/408**。
- 替代方案：逐条 key 集合（弃：无限膨胀 + 需存哈希）；「全部标记已看过」按钮（弃：多一次点击，且用户要的是「别重复啰嗦」不是「手动清空」）；
  直接**过滤**掉已看过条目（弃：违反优先级①不遗漏，万一有一条其实没处理就再也看不见）；导入页保留两张平台指引卡改为折叠
  以外的方案（弃：用户明确说「不要了」）。
- 最终选择：**时间戳水位 + 折叠展示（计数不变、不删件）+ 导入页三入口 + 设置分组汇总 + 四二级页**。
- 实施状态：**已实现并双端实测通过（2026-10-10 11:00–11:25）**——模拟器：二次扫描折叠「此前已看过 1 条」且计数不变；
  真机 `A1B2C3D4`：同一现象复现并消失（首次扫描显示快递短信，二次扫描折叠）；设置汇总页 + 二级页两端可见；
  JVM `:app:assembleDebug :app:testDebugUnitTest` = **BUILD SUCCESSFUL；52 类 / 408 例 / 0 失败 / 0 跳过**。
  证据与截图清单见 `13_sms_bill_import.md §15`。

---

## DEC-030: 增量 / 全量两种扫描方式（严格增量读 + 白名单空态）

- 日期：2026-10-10（下午）
- 背景：DEC-029 的「已看过」折叠只解决了「同一页里重复啰嗦」，用户第三次反馈要求更进一步——
  「我本次导入已经看过这条短信，并且不用处理了，下次再重新导入时候就不要再标记出来了，做两种扫描方式，
  一种全量扫描，一种增量扫描，比如我 12:00 扫描过了，增量只扫描 12:00 以后的，只有全量的才全部扫描出来。」
  同日用户另问「白名单在哪看，就只有清空」。
- 决策：
  1. **两个扫描模式**（`SmsScanMode.INCREMENTAL` / `FULL`），入口页短信卡给两个按钮（增量为主、全量为次）；
  2. **增量 = 严格 `date > 节点`**，为此在 `SmsBillReader` 新增 `readAfter()`，**明确不复用** L3 的 `readSince()`
     ——后者为补扫容错故意回退 `OVERLAP_MS`（3 天），拿来做手动增量必然把 3 天前看过的又翻出来；
  3. **节点 = 每一次扫描的墙钟时间**（**修订**，见下「口径修订」）：
     增量读完把节点推进到「本次扫描时间」，**不是**「这次读到的最新一条短信的时间」；
     **全量不动节点**，历史永远可由全量重见（不遗漏兜底）；增量首次无节点时以本次扫描时间为起点、返回空扫描；
  4. 扫描方式与时间节点随 `SmsScanPreview` 返回（`scanMode` / `sinceMs` / `scannedAtMs`，均有默认值），
     预览页 stepper 下方显示 `全量扫描：列出全部历史短信` / `增量扫描：本次覆盖 MM-dd HH:mm 之后的新短信，
     起点已更新为 MM-dd HH:mm`——**只有时间戳进文案，无短信内容**（DEC-026）；
  5. 「重新扫描」沿用当前模式；切模式 = 顶栏 ← 回入口页再选（不新增常驻控件）；
  6. 白名单空态**不再显示「清空白名单」**，改为说明「怎么才会有内容」；有内容时先给「白名单（N 个发送方）」标题行。
- 替代方案：增量复用 `readSince`（弃：3 天重叠 ⇒ 老条目必回锅，直接违背用户诉求）；节点用「上次手动扫描时间」独立水位
  （弃：与 L3 自动补扫水位分叉，同一份短信会被两层各算一次，语义更难解释）；把「已看过」条目从全量结果里也删掉
  （弃：违反①不遗漏，全量是最后兜底）；入口页做「模式单选 + 一个按钮」（弃：多一次点击，两个按钮更直观）。
- **口径修订（2026-10-10 13:4x，用户看到实现后的即时纠正）**：原实现把节点写成「这次读到的最新一条短信的
  `receivedAtMs`」，用户原话「增量扫描的时间节点不对，不应该是最后一条短信的时间，应该是当前扫描的时间」。
  实证两个毛病：①**空库时永远立不起节点**（模拟器上每次增量都说「首次使用已建立水位基线」）；
  ②节点显示成某条旧短信的日期（真机 `05-07 15:15`），与用户「我刚才扫过」的直觉冲突。
  改为：**节点 = 本次扫描的墙钟时间，且在读之前取**（读完到写节点之间到达的短信时间戳晚于节点 ⇒ 下次仍会被读到，不丢件）；
  **L3 `catchUp` 一并统一**（原为「读到的最新一条短信时间」，也是推进到本次补扫的墙钟时间）——两条增量路径写同一个水位，
  口径必须一致；`catchUp` 本身是 3 天重叠回扫，故推进到读之前的墙钟时间也不会漏。
- 不变量：`SmsScanPreview.assertClosed()` / `未识别 N` 全量口径 / 不删件 / DEC-026 隐私边界，全部不动。
- 实施状态：**已实现并真机实测通过（2026-10-10 13:2x–13:5x）**——真机 `A1B2C3D4`：增量 → `扫描 0 条`，
  那条已看过的快递短信**完全不出现**；全量 → `扫描 1 条 = 可导入 0 + 未识别 1 + 非账单 0 + 已存在/重复 0`
  + `▸ 此前已看过 1 条`；**时间节点复验**：进导入页 → L3 补扫写 `13:47` → 增量显示
  `本次覆盖 10-10 13:47 之后的新短信，起点已更新为 10-10 13:48`；再跑全量节点**不动**；随后增量
  `本次覆盖 10-10 13:48 …，起点已更新为 10-10 13:49`（覆盖起点 = 上次增量节点，而非全量时刻）；
  白名单空态真机确认无「清空」按钮。模拟器 `emulator-5554`（AVD 重启后数据重置）：注入 B 后增量 `扫描 1 条`
  ⇒ **只读到新到达的 B、A 不重读**；无节点时增量 `扫描 0 条` + 「起点已设为本次扫描时间」。
  JVM `:app:assembleDebug :app:testDebugUnitTest` = **BUILD SUCCESSFUL；52 类 / 410 例 / 0 失败 / 0 跳过**（本轮 +2 例）。
  证据与截图清单见 `13_sms_bill_import.md §16`。

## DEC-031: F-15 服务端技术选型（MySQL 生产 + SQLite 开发）与「双 DSN」策略

- 日期：2026-10-10
- 背景：用户要求「开发 api 服务，用 python 开发」并让数据上云。口径问答结论：**先本机跑通 / 用户名+口令 JWT /
  全量核心业务表**（见 `14_cloud_sync.md §0`）；数据库用户随后明确「**数据库不用 pg 用 mysql**」（2026-10-10）。
  本机现状：Python 3.12.0 有、**Docker 无**、**MySQL 未安装**（无服务、无客户端、3306 未监听）。
- 决策：
  1. 框架 **FastAPI + Uvicorn**、ORM **SQLAlchemy 2.0**、迁移 **Alembic**、配置 **pydantic-settings**、测试 **pytest + TestClient**；
  2. **双 DSN**：一切由 `DATABASE_URL` 驱动——开发与单测用 **SQLite**（零外部依赖，DB 环境未就绪也不阻塞），
     生产/联调切 **MySQL 8**（驱动 **PyMySQL**，纯 Python 免编译），**不改一行代码**；
  3. 用 **Python 3.12**（本机 `D:\soft\python\3.12.0`）而非 3.14：argon2/pydantic-core 等原生轮子在 3.14 上未必有预编译包；
  4. 服务端代码独立放 **`server/`**（与 Android 工程并列，不混进 Gradle 构建）；
  5. 本轮**不做**公网部署 / HTTPS / 容器化，只本机 uvicorn 跑通；上云路径见 `14_cloud_sync.md §9`；
  6. **MySQL 特有口径写进设计**（`14_cloud_sync.md §3.4`，不写清必踩）：utf8mb4 字符集 + InnoDB 引擎；
     索引列显式长度（`id`/`user_id` VARCHAR(36)、`username` VARCHAR(32)、`entity_type` VARCHAR(32)、`entity_id` VARCHAR(64)）；
     `payload` 用 JSON 类型（SQLite 侧落 TEXT，可移植）；**`updated_at` 必须 DATETIME(6)**
     （`sa.DateTime().with_variant(mysql.DATETIME(fsp=6), "mysql")`，MySQL 默认 DATETIME 无小数秒，会把毫秒截掉、
     令「同 `updated_at` = no-op」判定失真）；**用户名统一转小写**（MySQL 排序规则大小写不敏感、SQLite 敏感，
     否则单测绿而 MySQL 上 `Admin`/`admin` 撞车）；`sync_counters` 自增用 `SELECT … FOR UPDATE` 行锁；
     时间一律 UTC naive（不依赖服务器时区）。
- 替代方案：Django（弃：只要 8 个接口、无模板/Admin 需求，FastAPI 更轻且 OpenAPI 自带）；Flask（弃：需自行拼校验与文档）；
  异步 ORM（Tortoise/Gino，弃：Alembic 生态与 SQLite/MySQL 双支持不如 SQLAlchemy 稳）；SQLite 单库到底
  （弃：上云要多设备并发写、且用户已指定 MySQL，必须能切 MySQL）；PostgreSQL（**用户明确否决**：「数据库不用 pg 用 mysql」）。
- 影响：新增 `server/` 目录与 `requirements.txt`；Android 侧零影响。

## DEC-032: F-15 用户系统与令牌口径（用户名 + 口令，JWT + 刷新轮换）

- 日期：2026-10-10
- 背景：用户要求「加入用户系统」；问答确认「用户名 + 密码 + JWT，不做邮件验证」。
- 决策：
  1. 账号 = **用户名 + 口令**（用户名 3~32 位 `[A-Za-z0-9_.-]` 且唯一；口令 ≥8 位）；**不做邮箱验证与找回**（本轮零外部依赖）；
  2. 口令哈希 **argon2id**；登录失败统一 `invalid_credentials`（不泄露「用户是否存在」）；
  3. **Access JWT（HS256，默认 30 分钟）** + **Refresh 令牌（随机 32B，只存哈希，默认 30 天）**；
     刷新**必轮换**（旧令牌立即作废），登出撤销；刷新令牌可撤销状态落库；
  4. **越权隔离写进 WHERE**：所有同步读写强制 `user_id = current_user.id`，**禁止**「先按 id 查出再判归属」；
  5. 轻量限流（登录/注册 10 次/分钟/IP+用户名）；服务端日志只记 `user_id`、计数、错误码，**不记 payload**（DEC-034）。
- 替代方案：邮箱 + 口令（弃：本轮无 SMTP，验证/找回邮件发不出去）；手机号 + 验证码（弃：需短信服务商与签名报备）；
  Session Cookie（弃：移动端 Bearer 更直接、无 CSRF 面）；长期不过期的 access token（弃：泄露即长期有效）。
- 影响：`users` / `refresh_tokens` / `devices` 三表；客户端需安全存储令牌（见 DEC-034）。

## DEC-033: F-15 同步协议（三模式 / 通用记录表 / 墓碑 / LWW + 冲突留档）

> ⚠️ **部分被取代（2026-10-10）**：本决策第 2 条的「通用记录表 `sync_records`（业务字段进 `payload` JSON）」
> 已由 **DEC-037** 推翻 —— 云端改为 **6 张类型化业务表 + `sync_log`**。
> 其余部分**仍然有效**：三种手动模式、墓碑语义、LWW + 冲突留档、写协议与 `payload` 形状（DEC-036）。

- 日期：2026-10-10
- 背景：用户要求「数据上云，但是本地不能丢，保证本地与云端一致，可以手动与云端数据同步，或者本地上云或者云端下载」。
- 决策：
  1. **三种手动模式**：双向（先 push 后 pull）/ 仅上传 / 仅下载；**不做自动后台同步**（WorkManager 留接口，二期）；
  2. 云端用**通用记录表** `sync_records`（PK `user_id + entity_type + entity_id`，业务字段进 `payload` JSON），
     **不照抄 6 张类型化表**：客户端 schema 会持续演进，类型化云端表会把「加一列」变成「改服务端 + 迁移 + 契约」三件事；
     代价 = 服务端不做 SQL 级业务查询（本轮不需要），但**校验不放松**——`entity_spec.py` 为 6 类实体定义字段白名单，
     Pydantic 逐条校验，未知字段/类型错误一律拒绝；
  3. `entity_id` = **客户端生成的 UUID**（本地表主键），服务端不重编 ID ⇒ push 天然幂等；
     重复提交同一 `updated_at` = no-op（不涨 `server_seq`）；
  4. 拉取游标 = **服务端单调 `server_seq`**（`sync_counters` 事务内 `SELECT … FOR UPDATE` 自增，SQLite/MySQL 通用）：`since_seq` 断点续传、
     分页 200（上限 500）；上传游标 = 本地 `last_pushed_at`，取 `updated_at >= last_pushed_at`
     （`>=` 保证同秒多次编辑不漏，重复推送幂等无害）；
  5. **删除只走墓碑**：本地删除在同一事务写 `sync_changes(action='delete')`（复用既有预留表），上行后由对端 pull 应用；
     服务端墓碑常驻（`deleted=true` 不物理删）。**「仅下载」绝不因「云端没有」而删除本地任何行**（防新账号清空本地）；
  6. **冲突 = LWW + 留档**：以 `sync_state.base_updated_at`（上次一致版本）做**三路比较**；`updated_at` 较新者胜，
     **输家副本写入本地 `sync_conflicts`**，UI 显示冲突数并可查看——**绝不静默丢弃**（客户端时钟偏移会让 LWW 判错，
     留档是事后纠错的前提）；
  7. **「本地不丢」五道保险**：① 同步前自动本地快照（复用 F-04 明文 JSON，保留最近 3 份）② 删除只走墓碑 + 仅下载不删本地
     ③ 冲突留档 ④ 失败不推进游标（可重试、不跳变更）⑤ dry-run 预览（上传 N / 下载 M / 冲突 K / 删除 D，D>0 时二次确认）；
  8. push 中任一条校验失败 → **整批回滚**（不做部分写入），避免出现「两端各自半同步」的不一致状态。
- 替代方案：outbox 全量改造写路径（弃：要改每一处 insert/update/delete，**漏一处即静默不同步**；改用 `updatedAt` 差分，
  只侵入「删除」一处）；CRDT/字段级合并（弃：个人记账场景复杂度远超收益）；「云端为准」全量覆盖（弃：会丢本地未同步改动）。
- 影响：服务端 `sync_records` / `sync_counters`；客户端 Room **v3 → v4** 只加 `sync_state` / `sync_conflicts` 两表（既有表零改动）。

## DEC-034: F-15 上云数据边界、令牌存储与同步前快照

- 日期：2026-10-10
- 背景：本项目已有两条隐私红线——F-13 的邮箱凭据（DEC-023）与 F-14 的短信原文/发送方（DEC-026）。
  上云是第三个「数据离开设备」的动作，必须先定边界再写代码。
- 决策：
  1. **上云**：6 类业务实体（账本/分类/交易/债务/预算/分类规则）全字段，**含交易备注**（账目完整性优先；如需排除备注，二期加开关）；
  2. **不上云**：短信原文与发送方白名单、IMAP 邮箱凭据、应用锁状态与外观偏好等**设备本地性质**数据；
  3. **传输**：本机/内网开发允许 HTTP（界面明示风险）；**公网必须 HTTPS**（Nginx + 证书，二期）；不引入任何统计/上报 SDK；
  4. **令牌存储**：Android Keystore 包裹后 base64 落 DataStore，**绝不明文落盘**；401 时静默刷新一次再重试；
  5. **同步前快照**：每次同步前自动写本地明文 JSON 备份（`files/sync_backup/`，保留最近 3 份，**不随同步上传**）；
  6. 云端为**明文存储**（本轮取舍：本地仍 SQLCipher 加密）。**上公网前必须完成**：数据库不裸露公网 + 磁盘加密 +
     备份加密 + 服务端日志脱敏。
- 替代方案：云端端到端加密（弃：本轮成本与密钥管理复杂度高，需先有威胁模型）；同步时不带备注（弃：会造成两端账目不一致）；
  把快照也上云（弃：快照是本地兜底，不该反向依赖网络）。
- 影响：`SettingsCloudScreen` 明示「仅同步账目数据，不含短信原文/邮箱凭据/白名单」；`14_cloud_sync.md §6` 记录取舍与上公网前置清单。

## DEC-035: 墓碑改为「`sync_state` 差分补发」，不再在删除路径写 `sync_changes`

- 日期：2026-10-10
- 背景：DEC-033 第 5 条原定「本地删除在同一事务写 `sync_changes(action='delete')`」，实现阶段发现三个问题：
  1. 要改**至少 5 处**写路径（交易单条/批量、债务、分类(+预算)、预算、规则、账本），**漏一处即静默不同步**；
  2. 「删账本」是 `DELETE ... WHERE ledgerId = ?` 的**批量清表**（甚至 5 表连带），那些行的 id 根本没经过任何写路径；
  3. F-04「导入替换」会整库重写，同样绕过写路径。
- 决策：墓碑 = **「`sync_state` 里有该 id、本地业务表已无该 id」**。上行时补发 `deleted=true` 信封，成功后清掉该基线行。
  **零写路径侵入**，且天然覆盖上述全部删除来源（含未来的新删除入口）。
- 代价与硬约束：差分必须用**完整的本地集合**。因此同步取**全量投影**，而不是 `updated_at >= 游标` 的增量——
  若只取增量，未加载的行会被误判成「本地已删」，进而**误发墓碑删掉云端数据**。这条约束写在
  `SyncEntityAdapter` 的类注释里，改动该处必须先读 DEC-035。
- 替代方案：outbox/变更表（DEC-033 已否）；在每个删除路径写墓碑（否：漏一处即静默不同步）；
  用 `sync_changes` 存量行反推删除（否：该表本轮本来就没写入，存量无意义）。
- 影响：`SyncPlan.tombstones`、`SyncUseCase` 上行批次含墓碑；`sync_changes` 表本轮**保持不写**（保留为二期变更审计）。
  `14_cloud_sync.md §4.2 / §4.6 / §5.5` 的墓碑口径以本条为准（§12.2 记为差分）。

## DEC-036: 云端 payload = Room 行原样 JSON（实体 `@Serializable`）+ 跨语言契约断言

- 日期：2026-10-10
- 背景：服务端白名单（`entity_spec.py`）的键名就是 camelCase 列名；若客户端手写一层「实体 → DTO」mapper，
  两侧漂移是**不可见**的——直到真机同步时才以 `validation_error: 未知字段 / 缺少字段` 的形式爆炸。
- 决策：
  1. 6 张业务实体直接标注 `@Serializable`，上行 = 实体序列化、下行 = 反序列化后 `INSERT OR REPLACE`。
     `SyncEntityAdapter` 因此只有三个动作：全量投影 / 按 payload 落地 / 按 id 删除。**少一层命名转换 = 少一类静默错位**。
  2. 新增 `SyncPayloadContractTest`：解析 `server/app/entity_spec.py` 的 6 类字段白名单，与各实体序列化器的
     `elementNames` **逐字比对**；找不到 `server/` 时用 `Assume` **跳过（如实记 skipped，不冒充通过）**。
  3. 可空字段补 `= null` 默认值：服务端按 `explicitNulls=false` 存储会**省略 null 键**，回传时缺键必须仍能反序列化。
- 附带决策（同批）：上行判据从「`updated_at >= last_pushed_at`」改为「**与 `sync_state` 基线不一致**」，
  `last_pushed_at` 退化为纯展示字段。好处：时钟回拨不再漏推，且与 DEC-035 的墓碑差分共用同一份完整本地集合。
- 代价：业务实体带上序列化注解（无运行时副作用；Room schema 不受影响——`4.json` 相对 `3.json` 的差异仅两张新表）。
- 影响：`SyncEntityAdapter` / `SyncRegistry` / `SyncPayloadContractTest`；`14_cloud_sync.md §12.2` 记为差分。

## DEC-037: 云端存储改为 6 张真实业务表 + `sync_log`（推翻 DEC-033 的通用记录表）

- 日期：2026-10-10
- 背景：DEC-033 用一张 `sync_records` 把业务字段全塞进 `payload` JSON。用户验收云端库时否决：
  「不要这样，要在云端做明确的表，设计表结构」「你上云的数据都啥，用户的账单数据没有上？」——
  JSON 存储让云端**无法用 SQL 查账**（`SUM(amount) GROUP BY date` 都做不到），「数据上云」只剩搬运。
- 决策：
  1. 按实体建 **6 张类型化业务表**：`ledgers` / `categories` / `transactions` / `debts` / `budgets` /
     `user_category_rules`，PK (`user_id`,`id`)，每个业务字段一列（金额 `BIGINT` 存「分」、`date` VARCHAR(10)、
     `month` VARCHAR(7)），按查询形状建索引；业务表之间**不建外键**（同批父子实体可能乱序到达）。
  2. 变更流独立成 **`sync_log`**（PK `user_id`+`server_seq`）：每次 push 追加一条；它是**拉取游标的唯一来源**，
     `status.counts` 也按它统计 distinct `entity_id`（含墓碑）⇒ 与旧「记录行数」语义一致，客户端无需改。
  3. **墓碑 = 业务表物理删行 + 日志保留 `deleted=1`**：业务表只放活数据，云端查账不被墓碑污染。
  4. `created_at_raw` / `updated_at_raw` 保留客户端**原始 ISO 串**（逐字回传，`base_updated_at` 的 no-op 判定依赖），
     `updated_at` 另存归一化 `DATETIME(6)` 专供 LWW 比较。
  5. 字段白名单加 **`max_length`**，与列宽逐一对齐（`test_typed_storage.py` 断言），超长在写库前就 422。
  6. `entity_store.shred()` / `reassemble()` 负责 `payload ⇄ 列` 双向搬运：**上行/下行 payload 形状与键名完全不变**
     （DEC-036 继续有效）⇒ **Android 端一行未改**。
  7. 迁移 `0002_typed_entities`：建表 → 拆列回填 → 删旧表；`downgrade` 反向拼回 payload 形状。回填对脏行宽容
     （拆不出列的行只留日志 + 告警），**不因一行脏数据让整次迁移失败**。
- 代价：客户端 schema 每加一列要同步改服务端模型 + 迁移；换来云端可直接 SQL 聚合 / 建索引 / 接报表。
- 关键不变量：push 返回的 `server_seq` **不得**用于推进客户端游标（会永久跳过同窗口的其它设备变更），
  只有 pull 成功才推进游标 —— 首次双向同步因此合法地停在游标 0。
- 踩坑（已修）：客户端 `explicitNulls=false` ⇒ 可空字段整个缺键，拆列必须 `.get()`（否则真实历史行被判脏跳过）；
  业务表的实体列叫 `id` 而非 `entity_id`；离线 `--sql` 模式必须跳过数据搬运。
- 影响：`models.py` / `entity_store.py`（新）/ `entity_spec.py` / `sync_service.py` / `alembic/versions/0002_typed_entities.py`（新）/
  `tests/test_typed_storage.py`（新）/ `tests/test_migration.py`；`14_cloud_sync.md §3.3/§3.4/§4.6/§13`。

## DEC-038: 自动云同步（前台触发）与设置页「账号」一级入口

- 日期：2026-10-12
- 背景：用户纠正方向——原话「**不对，你做的不对，我要的是 app 登录，不是做一个云同步的功能。是 app 能登录，
  在设置里可以自动云同步或者手动同步**」。即 ① 登录是**账号能力**，不能埋在「数据 → 云同步」二级页里当附属品；
  ② 同步要**自动**与**手动**并存。用户在当轮进一步选定：自动同步 = 「打开 / 回到 App 时静默同步」，
  登录入口 = 「设置首页新增「账号」行」。
- 决策：
  1. 设置首页新增一级「**账号**」行（未登录「未登录 · 点此登录云端账号」／已登录「已登录：<用户名>」），
     点击直达 `settings-cloud`（账号与同步页）；经 `SettingsNavigation.openCloud()` 走根图导航。
  2. 自动同步 = **打开 / 回到 App 时静默跑一次双向同步**（`MainActivity.onStart()` → `AutoSyncManager.runOnForeground()`）：
     距上次同步 ≥ **5 分钟**节流；已登录且开关打开才跑；开关默认**开**、可在页面关闭。
  3. **危险计划绝不自动执行**：`SyncPlan.needsConfirm`（`deleteCount > 0 || conflictCount > 0`，与手动同步的
     二次确认**共用同一处判定**）⇒ 直接跳过，留给用户手动确认。静默删账目比「少同步一次」糟得多。
  4. 失败**不打扰**用户（不弹窗）；游标语义与手动同步完全一致：**失败不推进**，下次幂等续上。
  5. **不做**后台定时（WorkManager 周期任务最短 15 分钟、需新依赖 + 常驻 worker）：无明确需求，本轮按最小面落地。
  6. 手动三模式（双向 / 仅上传 / 仅下载）**全部保留**：那是「我看清数字再决定」的通道，不与自动同步互斥。
  7. 登录失败**必须可见**：`SyncAuthStore.saveSession` 落盘后**读回验证**（读不回即抛可读错误），
     杜绝「服务端 200、本地却没登录、界面无任何提示」的静默失败；`KeystoreSecretCipher` 失败重试一次后
     抛自解释消息（首次生成密钥可能因无网触发系统远程密钥供给失败，属暂时性）。
- 理由：自动同步的价值是「不用手动点」，不是「关着 App 也在跑」；而任何自动化都**不得**成为静默损坏本地数据的路径。
- 取代关系：本决策**部分取代 DEC-033 第 8 条**（原「自动同步留到 Phase 2，本期只做手动」）；DEC-033 的协议本体
  （三模式 / 墓碑 / LWW / 游标不推进）不变。
- 影响：`MainActivity` / `AutoSyncManager`（新）/ `SyncAuthStore` / `SecretCipher` / `SyncPlan` / `SyncPresenter` /
  `SettingsScreen` / `SettingsViewModel` / `SettingsCloudScreen` / `SettingsCloudViewModel` / `SettingsNavigation` /
  `MiniBookNavHost`；测试 `AutoSyncManagerTest`（新）/ `SyncAuthStoreTest` / `SyncAuthStoreDeviceTest`（新）；
  `14_cloud_sync.md §4.8`、`06_tasks.md` TASK-071、`10_release_notes.md`。

## DEC-039: 账号页与同步页分家；服务地址内置（不暴露、不可改）；注销只删云端

- 日期：2026-10-12
- 背景：DEC-038 把设置首页的「账号」入口指向了 `settings-cloud`，用户实测后提四点：二级页标题是「云同步」
  而入口写「账号」（名不符实）；「云端账号配置不要了」（页面上那张含服务器地址的账号配置卡）；
  要一个能**改资料 / 退出 / 注销**的用户信息区；以及「服务地址不要暴漏出来」（地址是运维细节，
  不该出现在记账 App 的界面里）。
- 决策：
  1. **职责分家**：`settings-account`（标题「账号」）= 账号自己（登录 / 注册 / 资料 / 口令 / 退出 / 注销）；
     `settings-cloud`（标题「云同步」）= 数据怎么同步（自动同步开关 + 手动三模式 + 状态 + 冲突留档）。
     设置首页「账号」行 → 账号页；「数据」页里的「云同步」入口 → 同步页。**二级页标题必须与入口同名**。
  2. **服务地址不进 UI**：地址由 `BuildConfig.SYNC_BASE_URL`（构建类型注入）提供，**release 版界面无任何地址输入 /
     展示**；仅 debug 版保留输入框（开发要临时指向别的机器）。内置地址缺失时明确提示
     「本安装包未内置服务器地址」，不静默失败。
  3. **注销 ≠ 删本地**：注销只删云端账号与云端副本（6 张业务表 + `sync_log` + 刷新令牌 + 设备 + 用户行），
     本地 Room 数据一条不动；退出登录同理。这条必须在界面文案里写出来，否则用户不敢点。
  4. **改口令的令牌语义**：`PATCH /api/v1/me` 先验证原口令，成功后**撤销该用户全部刷新令牌**并换发新令牌对；
     客户端必须用响应里的 `tokens` 覆盖本地，否则下一次刷新会拿已撤销的令牌换，人被莫名登出。
  5. **改资料的 null 语义**：请求里「字段为 null = 不改这一项」，「空串 = 清除昵称」。客户端靠 `explicitNulls=false`
     （null 不上线）实现，并有单测锁死——这条写错会误清用户显示名或误改口令。
  6. 账号页显示名 = 昵称优先、否则回退用户名（纯函数 `SyncPresenter.accountDisplayName`）；口令**只活在内存**，
     操作后立即清空，绝不落盘（落盘的只有 Keystore 加密令牌）。
- 理由：账号是身份、同步是数据流向，两者生命周期与失败模式都不同，混在一页必然出现「改个昵称要滚到同步页」
  这类错配；地址对用户是纯噪声，暴露它既无价值又暗示「可以随便填」，反而制造支持成本。
- 影响：`AccountViewModel`（新）/ `SettingsAccountScreen`（新）/ `SettingsCloudScreen` / `SettingsCloudViewModel` /
  `SettingsNavigation` / `Destinations` / `MiniBookNavHost` / `SettingsScreen` / `SyncDtos` / `SyncRemote` / `SyncApi` /
  `SyncPresenter` / `app/build.gradle.kts`（`buildConfigField`）/ `server/app/{models,schemas,routers/auth}.py` /
  `server/alembic/versions/0003_user_nickname.py`（新）；`14_cloud_sync.md §4.9`、`06_tasks.md` TASK-073、
  `10_release_notes.md`。


## DEC-040: 登录 / 注册独立成页；账号资料扩充为 4 项；云同步入口只留一处（2026-10-12 第二轮）

- 日期：2026-10-12
- 背景（用户原话）：「注册时间展示的不对」「登录和注册单独做页面，要做的像一个 APP 的登录注册页面一样」
  「云同步功能在账号页面有，在数据页面还有，只保留一个就行」「用户的资料有点少，你看相应的增加几个」。
- 决定：
  1. **认证独立成页**：`settings-login` / `settings-register` 两个整页；新增 `AuthViewModel` 只管认证，
     账号页 `AccountViewModel` 只管资料与会话。理由：登录 / 注册是"进 App 的门"，混在账号页里既不像 App，
     又让失败只能靠一闪而过的 toast 承担（真机「注册失败」提示不准的教训）。
  2. **时间口径写进服务端契约**：所有时间出参必须是**带 `Z` 的 UTC** ISO 串，客户端只负责转本地时区；
     客户端再兜一层"缺时区按 UTC 解释"。理由：注册时间差 8 小时的根因是"服务端 naive UTC + 客户端
     `Instant.parse`"两边都没兜住，契约模糊必然复发，双保险。
  3. **资料字段选 邮箱 / 个性签名 / 地区**：都是可空短文本列，**不改客户端 Room、不动本机数据**；
     头像需要文件上传接口，本轮不做。清空语义统一为"传空串 = 清除"，且**只提交改动过的字段**，
     改一项不会顺手清掉另一项。
  4. **「上次登录」只认首发刷新令牌**（`rotated_from IS NULL` 的最新一条）：令牌轮换是后台行为，
     算成"登录"会让这个数字失去意义。
  5. **云同步入口只留一处**（设置 → 数据 → 云同步）：账号页与同步页职责分家后，重复入口等于两种走法。
- 影响：`server/app/{models,schemas,routers/auth}.py`、`server/alembic/versions/0004_user_profile_fields.py`（新）、
  `server/tests/test_auth.py`、`app/src/main/java/com/minibook/ui/settings/*`、
  `app/src/main/java/com/minibook/ui/navigation/*`、`app/src/main/java/com/minibook/data/remote/SyncDtos.kt`；
  `14_cloud_sync.md §4.10`、`06_tasks.md` TASK-074、`10_release_notes.md`、`CHANGELOG.md`。

---

## DEC-041: 视觉现代化只做 token 级改造 —— 不升 material3 1.5.0-beta01、图标自带矢量、层级用 tonal surface（2026-10-12 第三轮）

- 日期：2026-10-12
- 背景（用户原话）：用户先要求「你好好调查一下正经的 app 登录注册页面怎么设计的」，随后确认方向为
  **「清爽现代：浅底 + 大圆角卡片 + 彩色小图标」**、范围 **「全部页面统一美化」**。三路调研（平台规范线、
  国内形态与合规线、2025–2026 视觉趋势线）结论与出处见 `16_auth_ux_and_visual_plan.md` §1/§2/§6/§7。
- 决定：
  1. **不升级到 `material3 1.5.0-beta01`**：Expressive 组件（`MaterialExpressiveTheme`、`MotionScheme`、
     `MaterialShapes`、`Morph`、`ButtonGroup`、`FloatingToolbar` 等）只在 1.5.0 线；1.4.0-beta01 发布说明
     原文已把 `ExperimentalMaterial3ExpressiveApi` **移除**。本仓库 Compose BOM `2025.09.01` ⇒ material3
     **1.4.0 稳定版**。为预览版 API 付出「API 仍在改名」的维护代价，不值当；改用**自定义 token**
     （色彩 / 形状 / 字体 / 间距）达成主要观感提升。
  2. **图标自带矢量**（决策 E）：新增 `ui/components/MiniBookIcons.kt`，不引入 `material-icons-extended`
     （既省体积、也避开 TASK-020 的依赖边界）；配套 `MiniBookIconsTest` 遍历断言**全部路径可解析**——
     矢量数据写错只在运行时崩（F-15o 的教训：编译绿 + JVM 全绿都抓不到）。
  3. **层级用 M3 tonal surface，不用阴影与 `surfaceVariant + alpha`**：补齐 `surfaceContainer*` 全族，
     面板/卡片按 `Low / High / Highest` 分层。理由：M3 Expressive 明确工具栏「无阴影」、靠色面分层；
     而此前全仓库 `surfaceContainer*` **0 命中**，层级只能靠半透明色硬凑，暗色下尤其脏。
  4. **语义色不参与动态取色**：收入绿 / 支出红是记账语义色，壁纸取色只作用于中性面与强调色；
     同时保留「颜色 + 符号」双编码（红绿色盲与中国「红涨绿跌」语境冲突）。
  5. **对比度按背景亮度算 `on*` 色**：`Theme.kt` 不再无条件 `onPrimary = Color.White`（浅色强调色底上
     白字过不了 WCAG 1.4.3）。
- 影响：`ui/theme/{Color,Theme,Type}.kt`、`ui/components/{MiniBookIcons,States,Cards,Settings,Navigation}.kt`、
  `ui/{main,home,budgets,billimport,reports,settings}` 下若干屏幕；
  `app/src/test/java/com/minibook/ui/components/MiniBookIconsTest.kt`（新）；
  `16_auth_ux_and_visual_plan.md §6`、`06_tasks.md` TASK-078/079/080、`09_test_report.md`、`10_release_notes.md`、`CHANGELOG.md`（F-15q）。

## DEC-042: 认证 UX 与合规口径 —— 眼睛图标 / autofill / imeAction / 字段级错误 + LiveRegion（口令下限 / 协议勾选 / 首启形态见 DEC-043）（2026-10-12 第三轮）

- 日期：2026-10-12
- 背景：登录 / 注册已是独立整页（DEC-040 / F-15m），但控件细节离"像正经 App"还差一截：口令可见性靠页面里
  多余的按钮、无 autofill 语义、键盘会首字母大写、错误只在按钮下方出现一句且读屏不播报。依据见
  `16_auth_ux_and_visual_plan.md` §1（NIST SP 800-63B-4 / OWASP ASVS 5.0 V6 / OWASP Authentication
  Cheat Sheet / Android 无障碍与 autofill 文档 / WCAG 3.3.1）。
- 决定：
  1. **口令可见性放输入框 trailing 图标**（Material `password_toggle` 惯例）：NIST 明确 SHOULD 允许显示口令，
     也避免"眼睛按钮"占掉一整个行动区。
  2. **带 autofill 语义**：`ContentType.Username / Password / NewUsername / NewPassword`，让系统密码管理器
     能填充与保存（NIST：SHALL allow password managers & autofill）。
  3. **键盘不帮倒忙**：`KeyboardCapitalization.None` + 关闭自动纠错（口令不该被纠错）；`imeAction` 串成
     「下一步 → 完成」，最后一项回车即提交。
  4. **错误落到字段 + 读屏可播报**：`isError` + `supportingText`（WCAG 3.3.1 错误须文本、不能只靠颜色），
     页面级错误 `liveRegion = LiveRegionMode.Polite`。
  5. **提交中禁用全部输入**，避免重复提交与状态错乱。
  6. 暂**不去掉**注册页「确认口令」：WCAG 2.2 SC 3.3.7（Redundant Entry）是去掉的依据，但用户当前首要诉求是
     「不记错」；用户已于 2026-10-12 拍板 B = **保留**（见 DEC-043）。
- 影响：`ui/settings/{AuthComponents,LoginScreen,RegisterScreen}.kt`；
  `16_auth_ux_and_visual_plan.md §4`、`06_tasks.md` TASK-075、`09_test_report.md`、`CHANGELOG.md`（F-15p）。

## DEC-043: 用户拍板 A/B/C/G 的落地口径 —— 口令维持简单校验、确认口令保留、法务文本单一副本 + 注册勾选默认不勾、首页 Hero 卡只给绝对差额（2026-10-12 第四轮）

- 日期：2026-10-12
- 背景：`16_auth_ux_and_visual_plan.md` §4 列出决策 A–G。第一版汇报后用户拍板（原话）：
  "A 弱口令的不用加，就简单的校验就行 B按你建议 C 运营主体和联系方式暂不声明 G一起做了把"。
  **D（首启告知弹窗形态）用户未表态**。
- 决定：
  1. **口令策略（A）= 不加弱口令黑名单**：维持「用户名 3–32 位 `^[A-Za-z0-9_.-]{3,32}$` + 口令 ≥ 8 位」，
     客户端与服务端同规则前置校验。**已知偏离**（NIST SP 800-63B-4 单因子口令 SHALL ≥15、ASVS 5.0 V6
     `6.2.4` 要求比对本机 top-3000 弱口令）——这是用户明确取舍，记在案不是疏漏；将来要收紧只需在
     `SyncPresenter.registerError` 与服务端 `RegisterRequest` 同步加规则。
  2. **注册页「确认口令」（B）= 保留**：用户把「不记错」排在 WCAG 2.2 SC 3.3.7（Redundant Entry，本可省一次输入）
     之前。不一致时错误**挂在该字段下**（`SyncPresenter.confirmError`），不重复显示两遍。
  3. **法务文本（C）= 两份纯文本 + 单一副本**：
     - 正文：`app/src/main/res/raw/privacy_policy.txt`（11 节）与 `app/src/main/res/raw/terms_of_service.txt`（10 节）；
       **界面直接读它**（`LegalScreen` + `LegalDocument` 枚举），上架材料也从它复制，避免三份漂移。
     - 入口：设置 → 关于 → 隐私政策 / 用户协议（2 跳可达，符合 191 号文一.2「进主界面后 >4 次点击即违规」）；
       注册页勾选框旁、登录页底部各有一对链接。
     - 同意：注册页**默认不勾选**（191 号文三.4 禁止默认勾选），未勾选时本地拦下并直说原因
       （`SyncPresenter.agreementError`）；**登录页不放勾选框**（三.2 禁止频繁征求同意），只保留
       「登录即表示你已阅读并同意」。
     - **运营主体 / 联系方式 / 注册地按用户要求暂不声明**：正文里明写「本版本为上线前草稿…尚未声明」，
       `docs/17_privacy_policy.md` §5 与 `docs/18_terms_of_service.md` §4 把它列为**上架前必须清零**项。
  4. **首启告知（D）= 本轮不做阻塞式弹窗**（用户未表态）：只做「2 跳可达 + 注册时同意」。
     严格按 191 号文一.1（首启须明显提示阅读隐私政策）这仍是缺口，已记入 `docs/17` §5.2，
     将来实现时形式建议「同意 / 暂不使用云端功能」，**不得挡住本地记账**。
  5. **首页 Hero 卡（G）= 只给可核对的绝对差额，不给百分比**：`NetBalanceTrend(deltaFen, hasPrevious)` +
     `HomeViewModel.previousBalanceOrNull(MonthSummary)`。上月收支全为 0 ⇒ 视为「上月无记录」，不显示对比
     （否则本月任意金额都会显示成「较上月 +¥X」，是假信息）；差额为 0 ⇒ 「与上月持平」；
     涨跌同时用**符号与语义色**编码（红绿色盲 + 国内「红涨绿跌」语境冲突的对策，见方案 §6.6）。
  6. 其余决策沿用：**E**（图标自带 vector drawable，不引 material-icons）、**F**（不升 material3 1.5.0-beta01）
     已在 DEC-041 落地；**D** 见本节第 4 条。
- 影响：
  - 新增 `app/src/main/res/raw/{privacy_policy,terms_of_service}.txt`、
    `app/src/main/java/com/minibook/ui/settings/LegalScreen.kt`、
    `app/src/test/java/com/minibook/ui/settings/LegalTextTest.kt`、
    `docs/17_privacy_policy.md`、`docs/18_terms_of_service.md`；
  - `ui/settings/{AuthComponents,RegisterScreen,LoginScreen,AuthViewModel,SyncPresenter,SettingsNavigation,SettingsScreen}.kt`、
    `ui/navigation/{Destinations,MiniBookNavHost}.kt`、
    `ui/components/Cards.kt`、`ui/home/{HomeScreen,HomeViewModel}.kt`；
  - `docs/16_auth_ux_and_visual_plan.md` §4/§5、`docs/06_tasks.md` TASK-076/077/081、
    `docs/09_test_report.md`、`docs/10_release_notes.md`、`docs/CHANGELOG.md`（F-15r/F-15s）。

## DEC-044: 未登录态与设置页结构按用户口径定案；内置分类图标换自带矢量集（2026-10-12）

- 背景：用户 2026-10-12 三条指令（原话）：「1、新修改的还是不对，用户未登录时候不要展示一个大框，做成一个让用户
  登录的按钮即可。2、账号设置提到设置页面第一行。再还有现在的内置分类这套图标感觉太旧了，能不能设计一批新的」。
  上一版 F-15t 的「资料头卡」被明确否决。
- 决策：
  1. **未登录态只留登录按钮**：账号页未登录时是一个满宽「登录」主按钮 + 「还没有账号？注册新账号」文字链
     （`GuestLoginButtons`）。理由：资料头卡（头像 + 标题 + 副标题 + 双按钮）是内容型 App 拉转化的版式，
     记账工具的未登录态应该轻——用户来这里是记账，不是来被劝登录的。注册入口降级为文字链，因为登录页自身
     已有「还没有账号？去注册」，两处不重复占位。
  2. **设置页「账号」提为第一行**：它是唯一「身份 / 跨设备」入口，未登录时也是最可能的行动点；其余分组顺序
     （账本 / 外观 / 安全 / 数据 / 短信账单）不变。
  3. **内置分类图标从 emoji 换成本身矢量集** `CategoryIcons`（24 个，路径取自 Material Symbols Rounded，
     经 Iconify 抓取后内联；24×24 网格）。理由：emoji 无法按分类色着色、各机型字形不一，"显旧"的根因在此。
     **口径**：`Category.icon` 仍是 String，存 `"food"` 这类稳定 key；**不改 Room schema、不做数据迁移**；
     历史 emoji 由 `LEGACY_EMOJI`（24 条）映射回同批矢量，用户自输的未知串走文本兜底；渲染统一入口
     `CategoryGlyph`；分类编辑器**取消手输 emoji**，改成 6×4 图标网格（老数据 emoji 不点就不改、原样保留）。
     选 Iconify 而非引 `material-icons-extended` 依赖：仓库禁改依赖，且 `material.icons` 已废弃（DEC-041）。
- 影响：分类图标渲染层唯一入口收敛到 `CategoryGlyph`（9 处调用点）；`CategoryIconsTest` 3 例在 JVM 侧逐个解析
  全部路径（F-15o 教训：编译绿、单测绿仍可能运行时崩）；`BuiltInCategories` 的 KDoc 写明 `icon` 是稳定 key。
- 已知偏离 / 后续：真机验收待用户许可；分类图标网格未提供无障碍标签（图标是抽象图形，语义由分类名承载）。

## DEC-045: 账本图标与预算删除语义（2026-10-13）

- 背景：用户五条要求中，③「账本的图标也换一下」、④「预算填写了不能删除或者清空」、⑤「分类预算删除后不立马刷新，返回再进来才会删除」属功能 / 数据语义问题；①「显示名改一下，叫昵称」、②「账号ID不要展示」属文案问题。
- 决策 1（账本图标）：内置与自建账本图标由 emoji 换成自带 Material Symbols Rounded 矢量，稳定 key + `LEGACY_EMOJI` 兼容映射；**只换渲染层，不改数据库、不做数据迁移** —— 历史 emoji 值经 `LedgerIcons.resolve()` 直接映射到对应矢量，未知串回退为文本，绝不空白。理由：emoji 在不同机型字形不一、无法随主题着色、无法与选中态联动；而数据库里存的是字符串，迁移会碰用户数据（违背「不记错」），映射是零风险的。
- 决策 2（预算删除）：总额预算补「删除月度总预算」入口（原先只有分类预算能删）；分类预算删除立即刷新由 **Room 失效通知**修复（根因见 `docs/06_tasks.md` TASK-083 ⑤），而不是靠手动刷新或重建页面。理由：删除是用户可见的破坏性操作，必须即时、可见、可确认。
- 决策 3（图标选择器）：账本编辑器的 emoji 手输框一并移除，改为 16 格矢量网格；用户若要自定义 emoji 需另行提出（当前口径：统一视觉优先）。
- 影响：`app/src/main/java/com/minibook/ui/components/LedgerIcons.kt`（新）、`app/src/main/java/com/minibook/ui/components/Pickers.kt` / `app/src/main/java/com/minibook/ui/settings/LedgerManageScreen.kt` / `app/src/main/java/com/minibook/ui/components/LockComponents.kt` / `app/src/main/java/com/minibook/ui/settings/LedgerEditorSheet.kt`、`app/src/main/java/com/minibook/domain/usecase/ledger/BootstrapUseCase.kt` / `app/src/main/java/com/minibook/domain/usecase/export/ImportAllUseCase.kt` 的 `DEFAULT_ICON`、`app/src/main/java/com/minibook/domain/usecase/budget/ClearTotalBudgetUseCase.kt`（新）、`app/src/main/java/com/minibook/ui/budgets/BudgetsViewModel.kt` / `BudgetsScreen.kt` / `BudgetEditorSheet.kt`、`app/src/main/java/com/minibook/data/local/db/BudgetDao.kt` + `app/src/main/java/com/minibook/data/local/db/RoomDataSourceImpl.kt`。
- 已知偏离：用户已拍板「A 弱口令的不用加」（见 DEC-043），本轮未收紧口令；真机仅做安装与启动验证、未做逐页走查（装机由用户当次授权）。
- 未做：`app/src/main/java/com/minibook/data/local/sms/SmsNotificationHelper.kt:72/:94` 仍用 `R.mipmap.ic_launcher` 做通知小图标（与 F-15o 同类隐患，未改）；`app/src/main/java/com/minibook/ui/settings/LedgerManageScreen.kt` KDoc 里「Text(←) 占位」的旧描述本轮未改。

## DEC-046: 短信导入来源口径 —— 从扫描反推、默认全选、与自动记账分离（2026-10-13）

- 背景：用户要「多家银行只导入指定的一家」。但发送方无法预枚举：银行端口共用（`95xxx` 段 + `106` 通用端口），同一号码既发账单也发营销；用户也明确表示「我可能给不了你发送号码」。
- 决定：**不做静态号码表**，改为**从扫描结果反推来源清单**：
  1. `sync` 之外单独维护来源配置（`SmsWhitelistEntry` 增 `importEnabled`，与 `autoEnabled` 是两件事：允许导入 = 要不要这笔；允许自动记账 = 要不要免确认）；白名单存储格式升为 5 段，旧 4 段读入时 `importEnabled = true`（老用户升级默认允许，不需要迁移脚本）。
  2. 扫描聚合出「号码 + 条数 + 最后时间 + 猜出的机构名」，**默认全选**（宁可先都进来再让用户关，也不能因默认关闭而漏单——不漏单优先级最高）。
  3. 只有**真正出过账单**的来源才登记进配置，营销号/验证码号不占用清单。
  4. 关掉某来源后，该来源的短信进「非账单」计数，**绝不删除任何已入账交易**。
- 影响：`domain/model/SmsModels.kt`、`data/local/sms/SmsWhitelistStore*.kt`、`domain/usecase/sms/SmsSenderNamer.kt`、`domain/usecase/sms/SmsSenderSummaryBuilder.kt`、`domain/usecase/sms/SmsBillImportUseCase.kt`（`scan()`）、`ui/billimport/*`（来源弹框）、`ui/settings/SmsSettings*`（双开关）。
- 已知局限：机构名靠词表 + 正文签名猜，猜不出就显示号码（不硬写）；106 端口仍可能把同一号码的营销与账单混在一起，只能靠内容判定（见 DEC-047 的营销过滤）。

## DEC-047: 分类识别口径 —— 本地学习为主、归一化两档强度、内置词典只作建议（2026-10-13）

- 背景：用户要求「跨写法/门店也能匹配」（如 `罗森(北京)有限公司朝阳门店` 与 `罗森(北京)有限公司房山店`），并提出「我测不到的商户内置肯定没有」；我给出众包方案后用户改要求：「能不能联网搜一波大的，然后当做内置的，然后学习的就都在 App 本地」。
- 决定：
  1. **归一化核心词**（`CategoryRuleNormalizer`）：剥括号 → 全角转半角/去空白/小写 → 按长词在前剥后缀表 → 去首尾标点；**刻意不剥裸「店/市/区/县/中心/广场」**，否则会把「如家酒店」「天猫超市」咬坏。强度分两档：`STRONG`（原串包含 / 归一化相等 / 核心词 ≥3 字被包含）与 `WEAK`（公共前缀 ≥2 字，如罗森两家门店）。
  2. **L2（到达即记账）只认 STRONG**：自动记账绝不能靠模糊匹配；`WEAK` 只用于用户看得见、改得动的手动预览与「待确认」路径（`allowWeakRuleMatch`）。学习侧改存核心词，旧规则不迁移、原串包含仍是 STRONG（行为不退化）。
  3. **学习完全留本地**：不做众包、不上传任何规则。理由是个人转账的对手方就是自然人姓名，上传即泄露第三方个人信息，且与「本地不收集」的隐私政策承诺冲突；同时也免账号、离线可用。
  4. **内置商户词典**（`res/raw/merchant_dictionary.json`，1315 条，仅映射 15 个内置支出分类名）只作**手动导入时的建议**，**不参与 L2**——内置词典错了只影响一次可见的预览，不会静默污染全部账目。规则源优先级：本地学习 > 内置词典 > 内置类型映射 > 「其他」。
- 影响：`domain/usecase/category/CategoryRuleNormalizer.kt`、`UpsertUserCategoryRuleUseCase.kt`、`ImportBillUseCase.kt`（第 1 步 + 第 1.5 步）、`SmsBillImportUseCase.kt`（两条预览路径传 `allowWeakRuleMatch = true`）、`MerchantDictionary` 相关 4 个文件、`di/DataSourceModule.kt`；文档 `docs/19`、`docs/20`。
- 已知局限：公共前缀会把「中国移动」与「中国联通」判成 WEAK（故只用于预览）；词典是种子集不是全集，长尾仍落「其他」+ 本地学习补齐。
## DEC-048: 数据库门控口径 —— Flow 门控、suspend 硬失败（F-16i，2026-10-13）

- 背景：真机崩溃 `DatabaseGate 未解锁/未打开，无法提供 LedgerDao`（crash buffer 全栈），根因是 `AppLockController.lock()` 先关库后翻锁状态、UI 生命周期恢复立刻重启上游 Flow，旧实现在调用点同步抛错。
- 决策一（Flow）：**返回 `Flow` 的数据源方法一律走 `openState.flatMapLatest` 门控**——门关时切 `emptyFlow()`（不发射、不抛异常；下游 `StateFlow` 保留旧值，不闪空列表），解锁时切到新建库的 DAO。**不得写成 `filterNotNull().flatMapLatest(...)`**：那样重锁事件到不了 `flatMapLatest`，旧库流不会被取消（会被继续订阅在已关闭的 `MiniBookDatabase` 上，回归测试已抓到）。
- 决策二（suspend）：**一次性 `suspend` 查询/写入保持 `error(...)` 硬失败**，不改成「返空 / 返 null」。理由：静默返空会把「没解锁」误报成「没有数据」，违反优先级「不记错」；这类方法只应在解锁后被调用。
- 决策三（后台）：接收器类路径（`SmsReceivedReceiver`、自动同步）继续自行 catch 错误（既有行为，不因门控改动而放宽）。
- 影响：`DatabaseProvider.openState` 成为新的稳定接口；测试替身默认实现 `flowOf(database)` 即可；`GatedFlowTest` 锁定三条语义。
- 已知偏离：suspend 路径在「关库→锁屏」窗口内仍可能抛错，且未做真机复现路径回归（记入 `docs/09_test_report.md` 遗留风险）。
## DEC-049: 应用锁要求设备已录入生物识别；Keystore cipher 失败一律非致命（F-16j，2026-10-13）

**决策一**：开启应用锁前必须同时满足「有系统锁屏」（DEC-003）与「已录入指纹/人脸」。只设 PIN 时不满足——Android 13+ 的 Keystore 不允许生成 auth-gated key，强行继续只能崩溃。不满足时**只引导、不启用、不降级明文**（隐私优先：宁可不开锁，也不静默退回未加密）。

**决策二**：`AppLockController` 中所有 `KeyManager.create*Cipher()` 调用一律 `runCatching`，失败按「未授权 / 切换失败」回调，**不得让它主线程抛出**（解锁失败回调 `false` 保持锁定，开关切换失败回调 `AUTH_FAILED`）。理由：key 可能因用户移除生物识别而永久失效（`KeyPermanentlyInvalidatedException`），这是可预期的用户行为，不该变成崩溃。

**决策三**：`hasEnrolledBiometric()` **不并入 `DEVICE_CREDENTIAL`**。BiometricPrompt 支持「设备凭据」降级，但 Keystore 的 auth-gated key 不接受它；合并判断会让分诊放过崩溃场景。

**已知偏离**：无自动测试覆盖（Keystore / BiometricPrompt 依赖真实设备），仅模拟器端到端验证；真机成功路径待验证。
## DEC-050: 首页账号入口只做标题旁小链接；图标同名碰撞按分类名改道（F-16k / F-16l，2026-10-13）

**决策一：首页的账号入口收成「首页」标题旁的可点小字，不再用卡片。** 用户明确要求「不用那么大…在首页两个字后面加个『未登录』『昵称』作为链接就可以了」。卡片占一整行、把结余卡压到第二屏；标题旁的 `labelLarge` 小字既保留了「当前账号是谁 / 未登录」的信息，又让首屏回到记账本身。可点区域仍按 Android 触控规范给到 **≥44dp**（`defaultMinSize(minHeight = 44.dp)`），不是纯文字点击区。

**决策二：`CategoryGlyph` 的图标解析增加「按分类名改道」，用来解同名图形碰撞。** `Gift` 与 `RedPacket` 两条矢量都是「盒 + 蝴蝶结」，历史上「礼金」存 `redpacket`、🧧 也映射到同一张，于是礼金与红包看起来完全一样。修法不是改用户数据，而是 **`resolve(raw, name)` 在渲染时按名字改道**（名为「礼金」且解析得 `RedPacket`/`Gift` → 渲染新增的 `GiftMoney`），并把 `name` 参数化到 `CategoryGlyph` 上，由持有 `Category` 对象的调用点传入。

**理由**：改数据要迁移、要同步、要处理老账本，代价高且可能出错；渲染层改道是**零数据风险**的（老账本渲染出来就对了），且可回退。选择「按名改道」而不是「换掉 `redpacket` 的 key」是因为用户的**自建「红包」分类**也在用 `redpacket`，换 key 会连带把它改掉。

**边界**：图标选择器（`CategoryEditorSheet`）**刻意不传 `name`**——选图预览必须原样显示用户刚点的那一张，否则点「红包」看到的却是手捧心，属于欺骗性反馈。新增的 `GIFT_MONEY` 也登记进 `pickerKeys`（列表 25 项），任何人都能自己选它。

**已知偏离**：`Gift`（人情）与 `RedPacket`（红包）本身仍视觉相近，本轮只解决「礼金 vs 红包」同图问题；`CategoryEditorSheet` 误点即改选中项属既有交互，未一并调整。无自动测试覆盖最终像素，视觉结论来自模拟器目视 + uiautomator 结构核对。

## DEC-051: 上云形态定案 —— 库迁腾讯云、**在服务器上打镜像** + compose 部署、联调走 SSH 隧道（2026-10-21）

- 背景：用户要求把数据库与后台服务都搬到腾讯云那台，并明确「后台服务器不要直接部署，在服务器打镜像，然后用 docker 部署」；随后要用模拟器连线上服务器测试。
- **决策一（部署形态）**：**在服务器上构建镜像 + `docker compose` 运行**，不在服务器上装 venv 跑 uvicorn。理由：① 宿主机不必装 Python 依赖，环境与本地可复现；② 资源上限（`mem_limit` / `cpus` / `pids_limit`）与日志轮转在 compose 里统一钉死 —— 2C2G 小机器上这点尤其重要（MySQL 已占 640m，API 限 448m）；③ 回滚 = 换镜像标签，不必在宿主机上找残留进程。部署三件套（`Dockerfile` / `docker-compose.yml` / `.dockerignore`）**进仓库**，**口令与密钥只落服务器 `.env`**（`.gitignore` + `.dockerignore` 双重排除 + `chmod 600`）。
- **决策二（不新建库、不动既有 MySQL 部署）**：复用腾讯云上已在跑的 `mysql:8` 容器（`/opt/docker/mysql/`）及其 `mysql_default` 网络，API 容器用**容器名** `mysql` 直连 49779；**数据库端口不对公网发布**，API 也**只绑 `127.0.0.1:8000`**。
- **决策三（迁移保号）**：`JWT_SECRET` 与迁移前 host **保持同值**，用户口令与 `caching_sha2_password` 插件也保持不变 ⇒ 迁移后老设备手里的 access/refresh 令牌继续有效，**不需要全员重新登录**（迁过来的 18 条 `refresh_tokens` 仍可用）。
- **决策四（外网入口）**：本轮**不开放公网端口**，模拟器联调走 SSH 隧道（`127.0.0.1:8000 → 服务器 127.0.0.1:8000`；模拟器用 `10.0.2.2` 命中宿主机回环）。理由：放行端口是用户的控制台动作，且正式对外前必须先有 HTTPS（release 包禁明文 HTTP，`SYNC_BASE_URL` 目前还是空串）。**要开放需用户决定**。
- 影响：新增 `server/Dockerfile`、`server/docker-compose.yml`、`server/.dockerignore`；`server/requirements.txt` 增 `cryptography==50.0.2`（MySQL 8 `caching_sha2_password` 首次 full-auth 的硬依赖，见 §14.3）；`docs/14_cloud_sync.md` 新增 §14 上云实录。
- **已知局限（诚实挂账）**：① 公网端口未开放，**未验证「经公网直连」这条路径**（隧道联调走的是同一个线上进程与线上库，但入口不经公网）；② 登录限流仍是单进程内存实现 ⇒ **仍不可多实例**（DEC-033 该项未决不变）；③ 除 compose 的 `restart: unless-stopped` 外没有额外守护；④ **没有自动/异地备份**：本次迁移的一次性 dump 留在两台机器的 `minibook-migrate/` 目录里，那是应急快照，不是备份机制。

## DEC-052: 设备归属语义定案 —— `devices.id` 即设备主键、一台设备同一时刻只属一个账号；登录/注册一律「改归属」（2026-10-22）

- 背景：`devices.id` 用客户端上报的 `device_id` 做主键，同一台设备换账号（注册**或登录**）时旧实现走 INSERT 撞主键：注册被误译成 `username_taken`（App 提示「该用户名已被占用」而库里根本没有该用户名），登录因**无异常兜底**直接 500（TASK-088 ④ / TASK-089）。
- **决策**：设备行已存在就**改归属**（`user_id = user.id` + 刷新 `last_seen_at` / `name` / `platform` / `app_version`）；INSERT 只在设备行不存在时执行。`register()` 里 `IntegrityError → username_taken` 仅保留给真正的「并发注册同名」。**未新增错误码、未改客户端** —— 客户端 `SyncErrorCode.UNKNOWN` 本来就对未知码前向兼容，为一次修复去动协议面属于过度设计。
- 理由：① 个人记账场景下同一台设备同一时刻只服务一个账号，改归属符合直觉，且**不会丢业务数据**（业务行按 `user_id` 隔离，设备行只是登记）；② 一个 `device_id` 在 `devices` 里只留一行，避免主键膨胀与「同设备多行」脏数据；③ 不为「两台设备共用一个 `device_id`」这种伪场景引入方言 upsert（MySQL 与 SQLite 语法不同，会牺牲双 DSN 一致性）。
- **已知残余（诚实挂账）**：并发下两个请求同时插同一**新** `device_id`，仍可能有一个撞主键（注册侧被译成 `username_taken`、登录侧 500）。窗口极小且**自愈**（下一次请求即走改归属），故不为此引入 SAVEPOINT 复杂度。
- 影响：`server/app/routers/auth.py` 的 `_touch_device()`；新增 2 条回归测试；镜像重打并部署（回滚标签 `minibook-api:pre-fix-20261021`）。完整验证链见 `docs/14_cloud_sync.md §15.0`。

## DEC-053: 线上公网入口 = 明文 HTTP + 无关端口 38080（暂不加证书、不开 443）（2026-10-23）

- 背景：用户要求「手机在别的地方也能连上线上服务、用最少的端口」，并明确两条边界：**暂时不加证书**、**443 不批准**，改用「另外的无关端口」转发。
- **决策**：不走 HTTPS、不加反代，直接用 **Docker 端口映射**把 API 容器的 8000 发布到主机 **38080**（腾讯云安全组只放行这一个端口，3228 SSH 维持现状）；接口文档（`/docs`、`/redoc`、`/openapi.json`）在生产关闭（`.env` 里 `ENABLE_DOCS=false`）；App **正式包也指向** `http://<远程公网IP>:38080`。
- 理由：① 不做 HTTPS 就不需要 80/443、也不需要域名，正好满足「最少端口」；② **刻意不用反代**（socat/nginx）——反代会让容器只看到网关 IP，而登录限流按 `request.client.host` 计数；实测 Docker 端口映射把**真实公网 IP** 带进了容器（线上日志记为 `<本机公网IP>`），限流因此仍然有效；③ 少一个组件 = 少一处内存开销与故障面（2C2G 小机，MySQL 已占 640m）。
- **正式包的明文放行是收窄的**：`network_security_config` 只对 `<远程公网IP>` 这一个 host 开 `cleartextTrafficPermitted`（默认仍禁明文），`SyncUrlValidator` 同口径——正式包只放行**内置入口的 host**，其它地址必须 `https://`；调试包另有 debug 同名资源整体放开（内网任意 IP 联调）。清单一旦指定 networkSecurityConfig，`android:usesCleartextTraffic` 在 API 24+ 即被忽略，故调试包的放开必须靠 debug 资源覆盖。
- **已知代价（用户知情并接受）**：口令与 bearer 令牌在公网**明文**传输，链路上可被中间人看到，只剩 argon2id + 限流兜底；`/docs` 已关，但端口本身对全网可见、会被扫描探测。
- **回滚**：compose 去掉 `38080:8000` 一行 + `up -d`（公网立即断，回环隧道不受影响）；App 侧把 `SYNC_BASE_URL` 改回 `https://…` 或留空。
- 后续若要发正式版给他人使用：应回到域名 + HTTPS（Let's Encrypt 不给裸 IP 签证书，见 `docs/14_cloud_sync.md §15`）。
- **2026-10-23 修订（用户要求地址与口令不进仓库）**：正式包不再把 host 写进 `network_security_config`（地址一进仓库就违反该要求）——平台层改为整体放开明文，**应用层**由 `SyncUrlValidator` 只放行内置入口的 host；内置地址本身改为构建时从本机 `sync.properties`（gitignored）注入。这不是安全上的等价替换，是一次**明确让步**，记在 DEC-054。

## DEC-054: 远程地址与口令一律不进版本库 —— 配置拆两处 + gitignore 加固 + 提交前体检（2026-10-23）

- 背景：用户 2026-10-23 指示「远程服务器配置文件拆分成两份，一份本地开发的一份远程开发的。提交代码前一定要检查，千万不要把远程服务器地址以及一些账号密码提交到仓库中。」体检确认：**口令从未入库**（DB 口令、测试账号口令、keystore 都不在仓库），但**真实公网 IP 出现在 7 个受版本控制的文件里**（`app/build.gradle.kts`、`network_security_config.xml`、`SyncUrlValidatorTest.kt` 与 5 份 docs）。
- **决策**：① App 入口地址只从仓库根 `sync.properties`（已 gitignore，模板 `sync.properties.example`）读出并注入 `BuildConfig.SYNC_BASE_URL`（debug 缺省回落 `http://127.0.0.1:8000`；release 缺省空串 = 未配置）；② 服务端配置模板拆两份：`server/.env.local.example`（本地 SQLite + 开发密钥）与 `server/.env.remote.example`（远程 MySQL + 仅占位符），真实值只落 `server/.env` 或服务器 `/opt/docker/minibook-api/.env`；③ `.gitignore` 改为「`server/.env*` 只放行 `*.example`」并忽略 `sync.properties`；④ 新增 `tools/check-secrets.ps1`（默认暂存区、`-All` 全量）作为**提交前置检查**；⑤ 已落地的 docs 里真实地址一律占位符化。
- 理由：① 地址与口令属于部署机密，进了仓库就永久留在历史里（清理要重写历史或换口令），而「gitignore + 模板 + 体检」是零依赖的标准做法；② 用 properties 文件而不是 `-P` 命令行参数：换机器/CI 只要放一份文件，且缺省行为明确（release 未配置时 App 明确提示，不静默连错）；③ 体检脚本是「防复发」：规则漏了就等于白拆，所以覆盖公网 IP 端点、真实口令、私钥、禁提交文件四类，并留 `-All` 供收口兜底。
- 被否决的替代方案：把地址留在 `gradle.properties`（入库，等于没改）；塞进 `local.properties`（本就被忽略，但已被 Android SDK 语义占用，混淆且非标准）；用 `-PsyncBaseUrl=` 每次构建命令行注入（容易漏、缺省行为不明确）；写进 Manifest `meta-data`（同样入库）。
- 影响：`app/build.gradle.kts`、`app/src/main/res/xml/network_security_config.xml`（删掉写死 host 与 debug 覆盖资源）、`app/src/test/.../SyncUrlValidatorTest.kt`（样本改 TEST-NET 文档段地址）、`sync.properties(.example)`、`server/.env.local.example` / `.env.remote.example`（`server/.env.example` 删除）、`.gitignore`、`tools/check-secrets.ps1`、`server/README.md`、`docs/*`。
- **已知代价（诚实挂账）**：① 平台层不再做 host 级明文固定（只留应用层校验）——正式版没有地址输入框、校验器仍只放行内置 host，实际暴露面未变，但确实是安全性上的一次让步；② 新增一个维护面（脚本）；③ 正则是启发式，base64 之类的机密形态不在覆盖内，仍需人工过一眼 diff。
- 验证：`check-secrets.ps1 -All` 对 **518 个文件 0 命中**（退出码 0）；受版本控制文件里真实公网 IP **0 命中**；正式包真机安装后界面无地址栏、手机 `curl` 直连 `/healthz` **200**。完整记录见 `docs/14_cloud_sync.md §17` 与 `docs/09_test_report.md` 最新章节。

## DEC-055: `sync_counters` 属部署状态 —— 缺行自愈 + 清库边界写进流程（2026-10-23）

- 背景：用户报「手机上的云同步怎么失败了」。定位为一次「清空所有表行」把 `sync_counters` 的种子行与 `alembic_version` 一并删掉，`next_seq()` 的 `UPDATE … value+1` 影响 0 行 ⇒ **所有账号 push 全 500**；而 `current_seq()` 对缺行容忍（`or 0`）⇒ 表现为「pull 200 / push 500」，且该 500 是刻意返回的 `ApiError`（日志无异常行），极难自查。
- **决策**：① `next_seq()` 缺行时**自愈** —— 按 `MAX(sync_log.server_seq)` 补种后重试一次，仍失败才报 `server_error`；② 明确「清库边界」：只删业务表行，**永不删** `sync_counters` / `alembic_version`；③ 版本表为空时用 `alembic stamp`，**不用** `alembic upgrade head`。
- 理由：① 计数器行是**部署状态**不是业务数据，把它当可清理数据会直接打断写路径；② 补种值必须取 `MAX(sync_log.server_seq)` 而非 0 —— `sync_log` 主键是 `(user_id, server_seq)`，从 0 重发会撞已有行；③ 「快速失败」的原始意图（提示迁移未完成）在「有人手动清库」这个真实场景里代价过大（整站写不可用），故改为自愈 + 保留失败兜底。
- 被否决的替代方案：计数器换 MySQL 自增列 / `LAST_INSERT_ID()`（破坏「双 DSN 同一套代码」）；`SELECT MAX(server_seq)+1 FOR UPDATE`（锁范围大、并发差）；把 `sync_log` 主键改成自增 `id` 并以其为 seq（最简洁，但要改协议 + 迁移 + 游标语义，收益不抵改动面）；每用户一行计数器（`shard` 预留路径，本项目量级用不上，且多出「注册插行 + 缺失自愈」复杂度）。
- 影响：`server/app/services/sync_service.py`（`_bump` / `_seed_counter` / `next_seq`）、`server/app/models.py`（`SyncCounter` 注释）、新增 `server/tests/test_counter_selfheal.py`、`docs/14_cloud_sync.md §18`、`server/README.md`。
- **已知代价（诚实挂账）**：① 自愈只在**缺行**时生效，若计数器值被人工改小仍会撞 `sync_log` 主键（自愈不校验语义）；② `shard` 列仍是无用预留（恒 0）；③ 报错文案从「请先执行 alembic upgrade head」改为「补种失败」，旧文档若引用该字符串需注意。
- 验证：新增 2 例回归测试（注入缺陷版 ⇒ 两条均红）；双 DSN 各 **78 例 / 0 失败**；部署后线上发号实测 `server_seq_global` 2314→2315；用户手机导入备份后同步成功（云端 `demo_user` 行数与备份逐项吻合）。
