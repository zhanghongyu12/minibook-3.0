# 审查报告

> 状态：已确认
> 最后更新：2026-08-21
> 维护者：Reviewer

> **说明**：Stage 8 审查已于 2026-08-20 实际执行（3 个 Reviewer 子 agent 按层并行），
> 当时未按角色产出独立审查文档回填本文件。本文件于 2026-08-21 依 `docs/06_tasks.md`
> 协调日志与 git 历史（b4aa338 / 5315fc3 / 2ea7424）如实回填，并转「已确认」。

## 最新审查

### 审查信息

| 项目 | 内容 |
|------|------|
| 审查日期 | 2026-08-20 |
| 审查人 | Reviewer ×3（Stage 8 三层并行子 agent：安全层 / 业务·数据层 / UI 层） |
| 审查范围 | 全量源码——安全层（`data/local/security` 双钥系统与解锁路径）、业务·数据层（`domain/usecase` + `data/local/datasource`）、UI 层（`ui/` 全部页面 / ViewModel / Sheets）。即 Stage 6 编码产出（TASK-001~030）+ Stage 7 测试骨架后的工作树 |
| 代码版本 | 提交 `1d09ae1`（Stage 7 测试落地，2026-08-20 08:44）对应工作树——即 Stage 8 首个修复提交 `b4aa338` 之前的源码状态（`fa17eeb` 文档重组织前的源码） |

### 问题汇总

> 文件列路径相对于 `app/src/main/java/com/minibook/`。

| 编号 | 严重程度 | 文件 | 位置 | 描述 | 状态 |
|------|---------|------|------|------|------|
| CR-1 | Critical | `data/local/security/AppLockController.kt`、`ui/settings/SettingsViewModel.kt`、`ui/settings/SettingsScreen.kt` | `setAppLockEnabled(true)` / 锁状态切换（关→开） | DEC-002 双钥系统半实现：开启应用锁仅写 DataStore flag，未将 master key 重包裹为 auth 密钥；flag 为 true 但 blob 仍为 plain 包裹，下次用 auth key 解 plain 会触发 AEADBadTagException 锁屏卡死 | 已修复（b4aa338） |
| CR-2 | Critical | `data/local/security/AppLockController.kt`、`data/local/security/KeyManager.kt` | 首启 `unlock(useAuth=false/true)` | DEC-002 首启 master key 生成/落盘零调用方：全新安装无 key blob，解锁路径未接 `generateMasterKey → persistMasterKey`，打不开加密库 | 已修复（b4aa338） |
| CR-3 | Critical | `data/local/security/AppLockController.kt` | unlock auth 路径 `decryptBlobWith` / `databaseGate.open` | auth 解锁 GCM 解密失败无 try/catch 兜底，异常未捕获导致锁屏崩溃（对齐 downgradeToPlainKey 兜底模式） | 已修复（b4aa338） |
| W-1 | Warning | `domain/common/BusinessErrors.kt` + 各业务 UseCase | 校验失败抛出路径 | 业务层错误分类不自洽：UseCase 校验失败用 require / IllegalArgumentException 而非 BusinessError 子类，KDoc 与实际不一致，UI 无法按类型捕获 | 已修复（5315fc3） |
| W-2 | Warning | `domain/usecase/transaction/UpdateTransactionUseCase.kt`、`domain/usecase/debt/UpdateDebtUseCase.kt` | Update 路径 | Update 未镜像 Add 侧校验（金额>0 且≤上限、分类非空、备注≤200、姓名非空），领域不变量存在空洞 | 已修复（5315fc3） |
| W-3 | Warning | `domain/usecase/ledger/BootstrapUseCase.kt` | 首启引导三步 | 插默认账本→克隆分类→写 currentLedgerId 未包单事务，半初始化态可能被幂等守卫（`countByLedger==0`）永久跳过 | 已修复（5315fc3） |
| W-4 | Warning | `data/local/datasource/RoomDataSourceImpl.kt` | `deleteById` / `deleteByLedger` | 数据源层 delete 自身不原子（三表分发 / 四表批删未包事务），存在部分删除残留风险 | 已修复（5315fc3） |
| W-5 | Warning | `domain/usecase/transaction/DeleteTransactionUseCase.kt`、`UpdateTransactionUseCase.kt` | 存在性校验 / ledgerId | 交易删除/更新未按当前账本圈定：未注入 LedgerProvider 经当前账本视图校验，Update 未锁定 ledgerId，存在跨账本误操作风险 | 已修复（5315fc3） |
| U-1 | Warning | `ui/transactions/*`、`ui/debts/*`、`ui/categories/*` | ViewModel 观察流 / Screen 三态 | 三页缺 Error 态：观察流未 catch 折叠为 Error 态、无 `refresh()`/`retry()` 重订阅（对齐 HomeViewModel 四态模式，CancellationException 重抛不吞） | 已修复（2ea7424） |
| U-2 | Warning | `ui/home/HomeScreen.kt` | `HomeErrorContent` | Home 错误态无「重试」按钮，出错后只能依赖手动触发刷新 | 已修复（2ea7424） |
| U-3 | Warning | `ui/lock/LockScreen.kt` | 静默开库失败态 | 锁关闭路径静默开库失败时页面卡死：失败态无「重试」按钮调 `viewModel.unlock` | 已修复（2ea7424） |
| U-4 | Warning | `ui/sheets/TransactionSheet.kt`、`DebtSheet.kt`、`RepaySheet.kt` | saved 处理 | Sheet snackbar 重放：saved 后未 `clearSnackbar`，下次打开重复弹「已保存」 | 已修复（2ea7424） |

合计：**3 项 Critical + 9 项 Warning**，全部修复并提交（`b4aa338` 安全缺陷 4 文件 193 行；`5315fc3` 业务·数据警告 13 文件 177 行；`2ea7424` UI 警告 11 文件 251 行）。

### 严重程度定义

- Critical: 必须修复，阻塞发布
- Warning: 建议修复，不阻塞但影响质量
- Suggestion: 可选优化，提升可维护性

---

## 安全发现

本次审查发现 **2 项 DEC-002 双钥系统安全严重缺陷**（CR-1 / CR-2），均已修复：

1. **双钥系统半实现——「锁开启（关→开）」重包裹路径缺失（CR-1）**
   DEC-002 要求「一把 master key + 两把包裹 key」（`minibook_master_key_auth` 需生物识别授权 / `minibook_master_key_plain` 静默），锁状态切换时用当前 key 解出 master key 再以目标 key 重包裹覆盖 `minibook_key.bin`。实际实现中 `setAppLockEnabled(true)` 只写了 DataStore flag，未真正把 master key 升级为 auth 包裹，导致「flag 为 true ⟺ blob 为 auth 包裹」这一不变量被破坏。用户开启应用锁后，下次解锁用 auth key 解密 plain 包裹的 blob，触发 AEADBadTagException 锁屏卡死，且安全语义不成立（开关状态与加密包裹不一致）。
   修复：新增 `AppLockController.upgradeToAuthKey`（认证后解 plain 包裹 → `persistMasterKey(useAuth=true)` 重包裹为 auth 包裹，幂等）；`setAppLockEnabled(true)` 改为先 upgrade 成功再落标志；`SettingsScreen` 开启开关补传 activity 作为 BiometricPrompt 宿主。

2. **首启 master key 生成/落盘零调用方（CR-2）**
   全新安装时无 `minibook_key.bin`，但解锁路径未接线 master key 的生成与落盘（`generateMasterKey → persistMasterKey` 无调用方），导致全新安装打不开加密库。
   修复：`unlock(useAuth=false)` 首启走 `firstLaunchOpenPlain`（生成 master key → plain 包裹落盘 → 开库）；`unlock(useAuth=true)` 首启走 `authFirstLaunchOpen`（先生成 master key → BiometricPrompt 认证（ENCRYPT cipher 绑定 auth key）→ `persistMasterKeyWithCipher` 落盘 auth 包裹 → 开库）；**锁开启首启禁止生成 plain 包裹**（遵循 DEC-002）。`KeyManager` 新增 `createMasterKeyEncryptCipher` / `persistMasterKeyWithCipher`（`persistMasterKey` 复用二者，行为不变）。

附带 1 项崩溃（CR-3）：unlock auth 路径 GCM 解密失败无兜底，`decryptBlobWith` + `databaseGate.open` 未 try/catch，异常导致崩溃；修复为失败回调 `onResult(false)`，对齐 `downgradeToPlainKey` 兜底模式。

## 性能发现

本次审查未发现阻塞性性能隐患。现有设计已规避常见性能风险：金额存储与展示经 `MoneyFormatter` 整数拆分（无 Float/Double 中间态）；DAO 查询一律带 `ledgerId` 过滤；列表 `LazyColumn` + `key=id`；账本切换 `flatMapLatest` 重订阅 + `WhileSubscribed(5000)` 防闪烁。存在少量非阻塞性技术债（如 `@IoDispatcher` 限定符接线、material-icons 正式依赖、BackupV1 补 `appVersion/exportedAt` 字段），属可维护性范畴，列入下一步建议，不阻塞发布。

## 总体评价

Stage 6~7 产出整体质量良好。业务层分层清晰（domain / data / UI 三层契约与 API、DATABASE 对齐），安全关键点（SQLCipher 整库加密 + Keystore 双钥体系）实现思路正确，但 **DEC-002 双钥系统存在完整性缺陷**——「关→开」重包裹路径缺失与首启密钥生成未接线，属于安全语义半实现，另有 auth 解锁 GCM 失败无兜底崩溃，3 项 Critical 均在发布前修复。9 项高价值警告（业务错误类型化、Update 校验对齐、Bootstrap/数据源事务原子性、交易按账本圈定、三页 Error 态/重试、snackbar 重放）分两批修复完毕。修复后 `assembleDebug` 通过、JVM 单测 56 例全绿；但 3 个 instrumented 用例（净额口径 / 月度汇总 / 迁移原子性）仅编写编译通过，未在设备执行，属发布已知限制。

## 下一步建议

- 后续迭代在真机/模拟器跑通 instrumented 测试（SQLCipher/Keystore 双钥往返、净额 CASE WHEN 口径、迁移事务原子性），验证 Stage 8 修复后的安全与事务路径。
- 处理非阻塞技术债：`@IoDispatcher` 限定符接线、material-icons 正式依赖、BackupV1 补 `appVersion`/`exportedAt` 字段、lifecycle-process 依赖集收敛。
- Phase 2 功能（搜索 F-06 / 报表 F-07 / 预算 F-08）与 Phase 3 云同步（SyncChange 预留接口）保持占位，本期不实现。
- release 构建当前为 unsigned，发布前需用户决策正式签名 keystore。

---

## Review 检查清单

每次审查必须逐项确认：

- [x] 代码符合架构设计（模块划分、依赖关系）
- [x] 代码符合编码规范（命名、注释、文件组织）
- [x] 无安全漏洞（注入、XSS、权限漏洞）——Stage 8 发现 2 项 DEC-002 安全缺陷（CR-1/CR-2）已修复
- [x] 无性能隐患（N+1 查询、内存泄漏、阻塞调用）
- [x] 错误处理完整（无空 catch、边界条件覆盖）——W1/W2/U1~U4 修复后对齐
- [ ] 测试覆盖充分（公共函数有测试、AAA 模式）——JVM 56 单测全绿；3 个 instrumented 用例未在设备执行，待后续验证
- [x] 无调试代码残留（console.log、print 等）

---

## Phase 2 审查报告（搜索 / 报表 / 预算 F-06 / F-07 / F-08，2026-08-21）

### 审查信息

| 项目 | 内容 |
|------|------|
| 审查日期 | 2026-08-21 |
| 审查人 | Reviewer |
| 审查范围 | **Phase 2 全部代码提交** `git diff 4e1d55e..HEAD -- app/src/main`（48 文件）+ 测试 `app/src/test`（8 文件 / 新增 36 例）——数据层（搜索过滤 DEC-013 + 报表聚合）、领域层（搜索/报表/预算 UseCase + BudgetProgress 模型）、UI 层（搜索/报表/预算三页 + 自绘图表组件 + 首页入口 + 导航桥） |
| 代码版本 | 编码 14 commit（`fc28d98`→`8b0588e`，含 5 个 Phase 2 设计文档 commit）+ 测试 1 commit（`a3764b4`，92 例全绿）；工作区干净（`git status` 无改动） |
| 依据 | `docs/07_decisions.md` DEC-010~013、`docs/02_architecture.md §15`、`docs/03_database.md §10`、`docs/04_api.md §12`、`docs/05_ui.md §8`、`.ai/rules/*` |

### 逐大类结论

> 文件路径均相对 `app/src/main/java/com/minibook/`。

**1. DEC 合规 —— 通过（三项全部合规，grep 佐证）**

- **DEC-011（图表纯 Compose Canvas、零第三方）**：`ui/reports/components/` 五组件（LineChart/BarChart/DonutChart/ChartLegend/ChartEmptyState）全部 `Canvas`/`DrawScope` 自绘（`drawPath`/`drawLine`/`drawRoundRect`/`drawArc`）。`grep -rniE "vico|mpandroidchart|compose-charts" gradle` 无命中；`git diff 4e1d55e..HEAD -- gradle libs.versions.toml app/build.gradle.kts` 为空——依赖与版本清单零变更。
- **DEC-012（仅 App 内告警）**：`grep -rn "POST_NOTIFICATIONS\|WorkManager" app/src/main` 无命中（通知栏未实现）。页内超支 = `BudgetsScreen` `OverBudgetLabel`「已超支 ¥X」+ 进度条 `semanticExpense` 红（`BudgetsScreen.kt:283-285`）；首页入口 = `HomeQuickEntry` 叠加 `error` 色红点（`HomeScreen.kt` + `HomeViewModel.budgetOverBudget`）。
- **DEC-013（扩展 observeFiltered 加参，Repository 签名不变）**：`TransactionDao.observeFiltered` 5→8 参（`remark`/`amountMin`/`amountMax`）、`DebtDao.observeFiltered` 3→4 参（`partyName`），SQL 与 03 §10.2 逐行一致；`TransactionFilter` 4→7 字段、`DebtFilter` 2→3 字段；`RoomDataSourceImpl` 两处转发新字段（`RoomDataSourceImpl` diff +remark/amountMin/amountMax、+partyName）；`TransactionDataSource/DebtDataSource.observeByLedger(ledgerId, filter)` 与 `TransactionRepository/DebtRepository` 观察方法**签名未变**（filter 引用透明）。

**2. 架构一致性 —— 通过（1 处例外计入问题清单）**

- **分层依赖方向**：ui→domain→data 无逆向新增。Phase 2 的 9 个新 UseCase（`domain/usecase/{search,report,budget}`）依赖 domain 模型 + 既有 Repository 契约；ViewModels 只依赖 domain.usecase/model；Screens 只依赖 domain.model + ui 组件。**注**：观察类 UseCase 直注 `data.local.datastore.LedgerDataStore`（domain→data 一例）是**延续 Phase 1** `ObserveTransactionsUseCase`（TASK-013）的既有范式，KotlinDoc 已显式留痕并注明待 `LedgerProvider.observeCurrentLedgerId()` 落地后替换，非本阶段新增。
- **金额全程 Long 分**：`grep -rnE "double|float|toDouble|toFloat" app/src/main/java/com/minibook` 仅命中 3 处 UI 展示几何（`BudgetsScreen.kt:456` 进度条比例、`DonutChart.kt:103` 扫描角、`LineChart.kt:131/207` 刻度步长与坐标归一），**domain 层零命中**。金额存取/比较/超支判定全部 Long 分整数运算（`BudgetProgress.overBudget` 等派生属性为 Long 比较/减法）。
- **observe / 取值路径统一**：6 个 observe UseCase（Search×2 / Report×3 / Budget×1）均 `LedgerDataStore.currentLedgerId.flatMapLatest` 切账本重订阅 + 空串保活；取值路径 Set/SetCategory/Clear Budget UseCase 均 `LedgerProvider.currentLedgerId()`。口径统一。
- **方法契约与 03/04 一致**：DAO→DataSource→Repository 新聚合方法（`observeDailyTrend`/`observeMonthlyTrend`/`observeCategoryAgg`）三签一致，SQL 与 03 §10.3 相符；`BudgetRepository.setTotalBudget` 在 `transactionRunner.runInTransaction` 内「先删后插」（DEC-008）。**例外**：`BudgetProgress` 模型形状与 04 §12.3 漂移（见问题清单 P1-1 / P2-1）。

**3. 编码规范 —— 通过为主 + 建议**

- KDoc 完整（类头职责/参数/决策引用/observe 特殊性留痕），命名清晰，文件头注释齐全，无空 catch、无调试残留。
- 软限制（单文件 ≤300 行）：**3 文件超限**——`ui/budgets/BudgetsScreen.kt`（647）、`ui/search/SearchScreen.kt`（685）、`ui/search/SearchViewModel.kt`（429），详见待审项 (b)；`ui/search/TransactionDetail.kt` 与 `DebtDetail.kt` 因边界约束（不可改 `ui/sheets/*`）合理新建只读 Sheet。
- 小瑕疵：`GRANULARITY` 枚举类名全大写（`domain/usecase/report/ObserveTrendUseCase.kt:20`），违反「类名大驼峰」，建议改 `Granularity`（P2）。

**4. 测试覆盖 —— 通过（闭环，遗留可接受）**

- Tester 交付 92 例全绿 = Phase 1 基线 56 + 本阶段新增 36（`BudgetProgressTest` 11 / `ObserveBudgetProgressUseCaseTest` 5 / `ReportUseCaseTest` 9 / `SearchTransactionsUseCaseTest` 3 / `SearchDebtsUseCaseTest` 4 / `SearchViewModelAmountRangeTest` 4）。核对 TASK-031~039「需测试覆盖点」：JVM 可覆盖项（预算超支口径、搜索 filter 透传、报表委托/区间/累计跑表/已删分类兜底、min>max 校验）**全部闭环**。
- 遗留可接受：instrumented 真机（DAO SQL / 迁移原子性）仍挂账、UI 交互流未单测——与 Phase 1 口径一致，见 09 §已知遗留，不阻塞。

### 问题清单（按严重度）

| 编号 | 严重度 | 文件 | 位置 | 描述 | 结论 |
|------|--------|------|------|------|------|
| P1-1 | 应改 | `domain/usecase/budget/ObserveBudgetProgressUseCase.kt`、`domain/usecase/category/DeleteCategoryUseCase.kt`、`DeleteCategoryWithMigrationUseCase.kt`、`data/local/db/entity/BudgetEntity.kt` | `ObserveBudgetProgressUseCase.kt:93-104`（`mapNotNull` 跳过孤儿）、删除链路仅删分类/迁交易、`BudgetEntity.categoryId` 无 `@ForeignKey` | **删除分类不清其预算 → 孤儿预算静默丢失**。`budgets.categoryId` 无外键/级联，`DeleteCategoryUseCase`/`DeleteCategoryWithMigrationUseCase` 只删分类/迁交易、不清 budgets；`ObserveBudgetProgressUseCase` 又把「分类不在分类流内」的预算整行 `mapNotNull` 静默跳过。三者叠加 = 用户删除一个已设预算的分类后，该分类预算在页面**凭空消失、无任何提示**（预算行仍留库，但不可见）。与 04 §12.3 原文设计「`category: Category?`，分类已删 null 兜底展示」相悖。 | 转 Developer 修复：推荐在分类删除事务内**级联删除/迁移其 budgets**（最彻底）；或回退为 §12.3 的 null 兜底展示（以 categoryId 显示「已删分类」）。二者择一即可闭合。 |
| P2-1 | 建议 | `domain/model/BudgetProgress.kt`（54 行） vs `docs/04_api.md §12.3`（464-487） | 模型整体形状 | `BudgetProgress` 与「已确认」契约 §12.3 漂移：`month` 文档写 `YearMonth`、实现用 `String`（yyyy-MM，与 `Budget.month` 一致，**实现更合理**）；总预算文档写 `total: BudgetLineProgress?`、实现扁平为 `totalBudgetFen/totalUsedFen`（0 哨兵）；分类进度文档写 `category: Category?`、实现 `category: Category`（非空）+ 孤儿跳过（即 P1-1 根源）。实现内部自洽且被 16 例测试锁定，行为正确，**问题仅在文档未回写**。 | 建议 Architect 回写 §12.3 对齐实现（或 Reviewer 记 DEM 后由 Architect 收口）。 |
| P2-2 | 建议 | `ui/search/SearchScreen.kt`、`ui/budgets/BudgetsScreen.kt`、`ui/search/SearchViewModel.kt` | 全文件 | 3 文件超 300 行软限（685/647/429），详见待审项 (b)。 | 本次不拆；拆分方案见 (b)。 |
| P2-3 | 建议 | `ui/budgets/BudgetsScreen.kt:454-456` | `BudgetProgressBar` | 进度条比例 `usedFen.toFloat()/budgetFen.toFloat()` 对金额分做浮点——仅 display 比例，超支判定（`over`）仍为 Long 整数，正确性无损；仅极端金额（>2^24 分 ≈ ¥16.7万）下进度条比例有可忽略视觉误差。 | 可接受，保持 Long 语义即可，无需改。 |
| P2-4 | 建议 | `ui/home/HomeViewModel.kt`（`budgetOverBudget`） | `observe(YearMonth.now())` | 首页超支红点月份在 VM 构造时固定为当月，跨月不自动滚动（VM 重建才更新）；预算页本身可切月、入口仅看当月，极低频。 | 可接受；后续可改为「可观察的 month 源」提升严谨性。 |
| P2-5 | 建议 | `domain/usecase/report/ObserveTrendUseCase.kt:20` | `enum class GRANULARITY` | 枚举类名全大写，违反 Kotlin 类名大驼峰约定（`coding_rules`）。 | 改名 `Granularity`（含 3 处引用），纯重构。 |
| P2-6 | 建议 | `ui/search/`（`toDebtFilter`） | `kind = if (direction!=null) LEND else null` | 方向筛选仅覆盖「借」类（LEND）记录，还款类（REPAY×owe_me/收到还款 等）无法按方向过滤，仅能经姓名检索——与 05 §8.1「(kind 口径)」标注一致，属有意窄化，非缺陷。 | 可选增强：若未来需按「收到还款/我还款」检索，将 `kind` 独立开放为筛选项。 |

> 无 P0（阻断）项：未发现崩溃/数据损坏/跨账本泄漏/资金口径错误。

### 已知待审项结论（a~d）

- **(a) 首页导航桥 `HomeNavigation` + `LocalHomeNavigation`（CompositionLocal）与 `ledger-manage` 参数式回调并存**：结论 = **保留两种并存，本次不统一（P2 技术债）**。二者解决同一「嵌套图→根图」跨层导航问题（`MainScreen.onNavigateToLedgerManage` 参数式 vs `LocalHomeNavigation` CompositionLocal），并存无害但属同题两解。评估：三入口 + 未来可能更多入口，若参数式需逐一穿透 `MainScreen` 签名（正是本次弃用参数式的理由），故 **CompositionLocal 是更优方向**。最小落地建议（本次不做）：后续择机**全迁 CompositionLocal**——把 `openLedgerManage()` 加入 `HomeNavigation`、删除 `MainScreen.onNavigateToLedgerManage` 参数（Home/Settings 改读 Local），既可统一机制又缩减 MainScreen 签名；不推荐全迁参数式（签名膨胀）。
- **(b) `BudgetsScreen.kt`（647）/`SearchScreen.kt`（685）超 300 行软限是否下沉**：结论 = **建议拆分，本次不强制**。`SearchScreen` 中 `OptionalDateField`（C-10 可清空扩展，通用性强）宜下沉 `ui/components/`；`DebtDirectionSelector`/`CategoryFilterChip`/条件区可归入 `ui/search/components/`。`BudgetsScreen` 中 `TotalBudgetCard`/`CategoryBudgetRow` 可收敛为单一可复用进度卡（配合 (c)）。拆分纯可维护性，不改行为。
- **(c) C-32 `BudgetProgressCard` 未在 `ui/components/` 落地（页内私有）是否补抽**：结论 = **本阶段不补，保留页内私有**。首页超支入口实际落为「预算入口 error 红点」（`HomeQuickEntry + budgetOverBudget:Boolean`），仅需布尔 `hasOverBudget`，**并不需要完整进度卡**，故「复用于首页超支入口」的前提不成立。页内总额卡（3 列+编辑按钮）与分类行（图标+2列+点击编辑）形态差异大，抽成带范式参数的单卡当前无第二消费方、收益低。待出现第二消费方（如首页嵌进度卡）再抽。文档 §8.4 C-32「复用行内变体」与实现「页内两私有卡」存在轻微出入，可接受，建议回写 §8.4 或本报告留痕即可。
- **(d) 债务搜索方向映射 `kind=LEND + direction`（借出=OWE_ME、借入=I_OWE）与 DebtSheet 语义一致性**：结论 = **一致，通过**。`DebtSheet` 借出按钮 = `setKind(LEND)+setDirection(OWE_ME)`、借入 = `LEND+I_OWE`（`DebtSheet.kt:122-140`）；`SearchViewModel.toDebtFilter`（`SearchViewModel.kt:399-403`）在 direction 选中时强制 `kind=LEND`、未选方向 = 不过滤，逐态对齐。`DebtDetail.kt` 的 `debtKindLabel/debtNetContribution` 四态映射亦与 `DebtDao` 净额四态表一致。唯一语义窄化：方向仅匹配「借」类（不含 REPAY）记录，与 05 §8.1「(kind 口径)」一致，属有意为之（P2-6）。

### 与 Phase 1 报告的延续性

- Phase 1（Stage 8）报告核的是 MVP 全量源码（安全/业务·数据/UI 层），3 Critical + 9 Warning 已全修复；Phase 2 报告核的是**增量**（F-06/F-07/F-08 三页 + 数据/领域扩展），二者范围不重叠、互补。
- Phase 2 无新增 Critical；新发现集中在「数据完整性缺口」（P1-1 孤儿预算）与「文档漂移/软限/命名」类可维护性项，严重度整体低于 Phase 1 的双钥系统缺陷。
- Phase 1 报告的「下一步建议」中的 `@IoDispatcher`、material-icons 正式依赖、BackupV1 字段、instrumented 真机验证等非阻塞技术债，在 Phase 2 **仍挂账**（Phase 2 未触碰，README/09 报告已确认延续）。

### 总体评价

Phase 2 三功能实现质量良好：DEC-011/012/013 三项设计决策全部精确落地（零第三方图表、零通知栏/WorkManager、`observeFiltered` 扩展范式合规），分层依赖、金额 Long 分、observe/取值路径统一均延续 Phase 1 既定范式且 KotlinDoc 留痕充分；自绘图表组件解耦干净（Canvas 不承担算术）；测试新增 36 例精准覆盖预算口径/搜索透传/报表委托/累计跑表/min>max 校验并与既有 56 例合成 92 例全绿。**需 Developer 返工 1 项 P1（孤儿预算静默丢失）**，另有多项 P2 建议（契约回写、超限文件拆分、枚举命名等）可延后。

## 历史审查记录

- **Phase 2（2026-08-21）**：Reviewer 单实例评审 Phase 2 增量（`4e1d55e..HEAD`，14 编码 commit + 1 测试 commit）。对照 DEC-010~013 + 02 §15 / 03 §10 / 04 §12 / 05 §8 逐项核对：DEC 合规三项全过、架构一致、编码规范过·建议并存、测试 92 例全绿。发现 **1 项 P1（P1-1 删除分类不清预算 → 孤儿预算静默丢失，需 Developer 返工）+ 6 项 P2**（模型与 §12.3 契约漂移 / 3 文件超 300 行 / 进度条 float 比例 / 首页红点月份固定 / GRANULARITY 命名 / 债务方向窄化），无 P0。待审项 a~d 结论：导航桥保留两种并存（后续全迁 CompositionLocal）、超限文件建议拆（本次不强制）、C-32 不补抽、债务方向映射一致通过。详见「Phase 2 审查报告」章节。
- **Stage 8（2026-08-20）**：3 个 Reviewer 子 agent 按层并行（安全层 / 业务·数据层 / UI 层），审查基线为 `1d09ae1`（Stage 7 测试落地）的工作树。发现 **3 项 Critical（2 项 DEC-002 双钥系统安全缺陷 + 1 项 auth 解锁崩溃）+ 9 项 Warning（W1~W5 业务层错误类型化/校验对齐/事务原子性/账本圈定，U1~U4 三页 Error 态/重试/snackbar 重放）**，全部修复并提交（`b4aa338`、`5315fc3`、`2ea7424`）。本报告于 2026-08-21 依协调日志与 git 历史回填，转为「已确认」。

