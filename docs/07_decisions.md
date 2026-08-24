# MiniBook Android 重构版 - 决策记录

> 状态：已确认（本文件为决策档案，持续追加）
> 维护者：全员可追加（Architect 汇总本阶段决策）
> 最后更新：2026-08-21
> 说明：DEC-001 为技术栈选型；DEC-002~DEC-009 对应阶段 3 拍板的 8 项最终决定（D-1~D-8，与 ARCHITECTURE §14 / DATABASE 落地口径一一对应）。编号递增不复用；已确认决策不可删除，只能标记为「已废弃」。

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

（本文件为设计文档，随实现对细节的差分由后续修订更新；版本历史由协调者统一管理。）