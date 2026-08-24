# 审查报告

> 状态：已确认
> 最后更新：2026-08-24
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

## F-12 审查报告（微信/支付宝账单导入，2026-08-24）

### 审查信息

| 项目 | 内容 |
|------|------|
| 审查日期 | 2026-08-24 |
| 审查人 | Reviewer |
| 审查范围 | **F-12 微信/支付宝账单导入全量交付**（相对架构/设计之后的代码与测试）：`be1ccdd..HEAD`（`e13eee0` 数据层 4 列迁移+解析器+导入用例 / `913e7f0` UI 接线 / `d9a091a` 测试+报告 / `49c0868` 三缺陷修复），56 文件 +3890/-49 |
| 代码版本 | HEAD `49c0868`（本地 master，未 push；`origin/master` 尚在 `634c65f` 基线） |
| 依据 | `01_prd.md §F-12`、`02_architecture.md §16`、`03_database.md §2/§4.1/§7.5`、`04_api.md §13`、`05_ui.md §9`、`07_decisions.md` DEC-015~021、`06_tasks.md` TASK-040~044、`09_test_report.md §F-12`、`08_review.md` 既有格式 |

### 逐大类结论

> 文件路径相对 `app/src/main/java/com/minibook/`（测试文件相对 `app/src/test/...`）。

**1. 功能完整性（PRD §F-12 十项验收）—— 全部达成**

- 动态表头探测、支付宝 GBK+`\t` trim、微信 XLSX（sharedStrings/日期序列 1899-12-30 整数拆分/金额 `<v>` 字面量）逐条实现并单测锁定（`AlipayCsvParserTest` / `WechatXlsxParserTest` / `BillHeaderMatcherTest`）。
- 过滤（不计收支/交易关闭/「/」/解析失败）、幂等（externalId 唯一 + NULL 互异 + 文件内去重 + IGNORE 计 -1）、自动分类映射+「其他」兜底、单事务整批写入、结果反馈计数——与 DEC-015/019 一致（`ImportBillUseCase` 逐行浏览确认）。
- 字段映射（type/amount 分/counterparty/paymentMethod/description 合并/externalId 前缀/source/categoryId）逐项正确；「当前状态/对方账号/商户单号」不落库（表头探测未注册该列）。

**2. 金额精度 —— 通过**

`grep -nE "double|float|toDouble|toFloat|\\*100"` 于 F-12 新文件：零命中。金额全程 `MoneyFormatter.toFen`（BigDecimal→`setScale(2,HALF_UP)`→`movePointRight(2)`→`longValueExact`，`MoneyFormatter.kt:28-41`）；微信日期序列数 `substringBefore('.')` 字符串拆分、`LocalDate.plusDays`（`WechatXlsxParser.kt:135-144`），微信金额 `<v>` 字面量（`WechatXmlRows.kt:83`）。无手写 `*100`。

**3. 迁移正确性（DB 1→2）—— 通过**

`MIGRATION_1_2`（`MiniBookDatabase.kt:76-87`）4 条 ADD COLUMN → 1 条 CREATE UNIQUE INDEX，顺序/默认值/索引名与 03 §7.5 逐字一致；`DatabaseGate.open` 已接 `.fallbackToDestructiveMigration(false)` + `.addMigrations(MIGRATION_1_2)`（`DatabaseGate.kt:64-65`）；`2.json` 14 列 + `source TEXT NOT NULL DEFAULT 'manual'` + 唯一索引名一致；Entity 侧 `@ColumnInfo(defaultValue="'manual'")` + `@Index(unique=true, name=...)`；手动记账走 `AddTransactionUseCase`（Domain 默认值 source=MANUAL、三列 null，零改动）。`MigrationV1To2Test` 断言质量合格（旧数据保留 / source=manual / 三列 NULL / 重复 externalId 抛 SQLiteConstraintException / NULL 互异）。

**4. 架构/分层一致 —— 通过（2 处留痕例外）**

分层 ui→domain→data 无逆向新增；`SafBillFilePreparer` 是解析栈唯一 Android 边界（`data/local/bill/SafBillFilePreparer.kt`，其余纯 Kotlin、JVM 可测）；DI 装配完整（`di/ImportModule.kt` 两绑定）。包名 `bill`（`import` 关键字）已留注（`ImportComponents.kt:32-33`）。例外：① `BillParserFactory` 接口落 `data/local/bill`、`ImportBillUseCase`（domain）直 `import com.minibook.data.local.bill.*`——延续既有 domain→data 技术债（见 P2-4）。② XLSX 用纯 regex 解码替代 DEC-016 指定的 XmlPullParser（见遗留项）。

**5. 错误/边界 —— 通过（1 处轻微计数偏差）**

空文件/表头未命中→`Unrecognized`、全跳过→`BillImportResult.success=0`、externalId 缺失/金额 0/负/非法→`PARSE_ERROR`、文件损坏→`Unrecognized`（`ZipFile` 异常兜底 `WechatXlsxParser.kt:49-51`）、非目标格式→工厂 null→「未识别账单格式」。文案精确对齐 §9.6。轻微：微信表头命中后的空白行被计入 NEUTRAL（见 P2-6）。

**6. 代码质量 —— 良好**

KDoc 完整（职责/决策引用/偏离留痕）、命名清晰、无空 catch、无调试残留。`ZipFile`/`InputStream`/tempFile 均 `.use`/finally 关闭（`SafBillFilePreparer` tempFile 已改 `<uuid>`，`BufferedInputStream.mark/reset` 不丢字）。已知残留：`BillColumn` 未再注册「对方账号/商户单号」（49c0868 改为精确 `== "交易对方"`，P3-1 已修）。

**7. 测试充分性 —— 通过（1 项报告口径失真）**

132 JVM 例全绿，覆盖与 PRD 十项验收逐条对得上（解析/表头/金额/枚举往返/mapper 往返/分类映射/幂等均测）。`MigrationV1To2Test` 断言质量合格。androidTest 反引号含空格方法名已全部改 ASCII（49c0868，HEAD 无残留）。失真：09 报告称「事务写异常：注入异常→0 插入」无对应用例（见 P2-3）。

**8. 偏离与遗漏 —— 见「问题清单」与「遗留项」**

新增下载发现 1 项 P1（F-04 备份 JSON 未携带 4 新字段，非任务预列的已知偏离）；task 预列的已知偏离（bill 包名/regex 解码/详情「—」/C-37 source 筛选/真机未验/eg/ 未提交）逐条评估见「遗留项」，均可接受或已留痕。

### 问题清单（按严重度）

| 编号 | 严重度 | 文件 | 位置 | 描述 | 建议方向（不修） |
|------|--------|------|------|------|------|
| P1-1 | P1 应改 | `util/BackupJsonModels.kt`、`util/BackupJsonMapper.kt` | `BackupJsonModels.kt:68-79`（`TransactionDto` 仅 9 字段）；`BackupJsonMapper.kt:76-87`（`toDto` 丢弃 4 新字段）、`BackupJsonMapper.kt:140-153`（`toDomain` 用默认值 source=MANUAL/三列 null） | **F-04 备份 JSON（BackupV1）未携带 F-12 四个新字段**：导出→导入（备份还原/换机迁移）会静默丢失导入交易的 `source`（徽标消失）、`counterparty/paymentMethod`（详情变「—」）、`externalId`（幂等判据丢失 → 还原后再导入同一账单将**重复入账**）。违反「数据不丢」与「全量备份」语义，DEC-018「四层贯通」未枚举 BackupJsonMapper（设计缺口）。 | 扩展 `TransactionDto`（+4 字段，可空/默认）+ `toDto`/`toDomain` + `MapperRoundTripTest` 补断言；或补一条 DEC 明确「backup v1 排除 F-12 字段」并给出升级/迁移口径。二选一后回写本表结论。 |
| P2-1 | P2 | `data/local/bill/BillParserFactory.kt`、`data/local/bill/SafBillFilePreparer.kt` | `BillParserFactory.kt:32-38`（扩展名主判先于 zip 魔数）；`SafBillFilePreparer.kt:49`（`isXlsx = ext==xlsx || 魔数`） | **内容嗅探兜底在「扩展名失配」场景失效**：`.csv` 命名但内容为 zip（用户改名 .xlsx→.csv）时，preparer 按魔数已写成 Xlsx 输入、factory 却按 `ext==csv` 分发到 `AlipayCsvParser` → 输入类型失配 → 返回「支付宝账单需 CSV 输入」的 `Unrecognized`。DEC-017「扩展名失配→内容嗅探兜底」只覆盖了「未知/空扩展名 + zip 魔数」，未覆盖「已知但错误扩展名」。非崩溃（优雅回未识别零落库），属健壮性缺口。 | `resolve` 将 `magicBytes.isZipMagic()` 判定前置于扩展名（或 ext 与魔数冲突时魔数优先），使「扩展名失配」真正走内容嗅探。 |
| P2-2 | P2 | `ui/sheets/TransactionSheet.kt`（未改动） | 全文件（新字段展示缺失） | **§9.3.3「编辑态只读信息块」未实现**：设计（05_ui §9.3.3 + CHANGELOG:19「TransactionSheet 增展示」）要求在编辑导入交易时于表单中显示只读「来源：微信 · 对方：… · 支付方式：…」；`TransactionSheet.kt` 未加任何 source/counterparty/paymentMethod 展示。数据无损失（`TransactionDao.update` 只写 type/amount/categoryId/description/date/updatedAt，不覆盖新字段，`TransactionDao.kt:44-52`），属纯展示缺口 + CHANGELOG 与实现不符。 | 编辑态 `source≠MANUAL`（或两字段任一非空）时加只读信息块；或回写 CHANGELOG/§9.3.3 标注「编辑态展示本期未做」。 |
| P2-3 | P2 | `app/src/test/java/com/minibook/domain/usecase/bill/ImportBillUseCaseTest.kt` | 全 6 例（无「写入异常→回滚」用例）；对照 `docs/09_test_report.md:76` | **「事务写异常整体回滚零落库」无对应单测**：`ImportBillUseCaseTest` 6 例中「缺少其他兜底分类」在 `insertBatch` 之前抛 `error`（`ImportBillUseCase.kt:123`），`FakeTransactionRunner` 仅 `block()`（`FakeRepositories.kt:193-196`）、`FakeTransactionDataSource.insertBatch` 无可抛异常注入；并无「insertBatch 抛异常验证回滚」用例。09 报告「✅ ImportBillUseCaseTest（注入异常→0 插入）」口径失真。真实回滚语义仅由 instrumented `TransactionRunnerAtomicityTest`（RoomTransactionRunner 层）间接保障。 | 补一例：fake 注入 `insertBatch` 抛异常 → 断言异常上抛且零落库；或更正 09 报告口径为「经 TransactionRunnerAtomicityTest 间接覆盖」。 |
| P2-4 | P2 | `data/local/bill/BillParserFactory.kt`、`domain/usecase/bill/ImportBillUseCase.kt` | `BillParserFactory.kt:15`（接口声明于 data.local.bill）；`ImportBillUseCase.kt:6`（domain import data.local.bill.*） | **domain UseCase 直依赖 data 包的接口**：`BillParserFactory` 契约落在 `data/local/bill`（与 impl `DefaultBillParserFactory` 同包），而既有的『域可依赖』契约（`TransactionRunner`/各 `*DataSource`）都收在 `data/datasource`。属层级放置不一致，KDoc 已自注「与 TransactionRunner 同径」（后者实际在 data.datasource），轻微漂移。 | 将 `BillParserFactory` 接口并入 `data/datasource`（与 TransactionRunner 对称），或下沉 domain。纯重构，不改行为。 |
| P2-5 | P2 | `data/local/bill/AlipayCsvParser.kt` | `AlipayCsvParser.kt:126-129`（KDoc）+ `:43`（`useLines` 逐行读） | **KDoc 声明「字段内逗号/换行/转义引号正确处理」与实现不符**：`splitCsvLine` 只处理逗号/`""` 转义，`useLines` 按换行切分，引号包裹字段内的**内嵌换行**（多行字段，RFC 4180 场景）会被跨行拆断、表头/数据错位。支付宝「交易流水证明」实际极少含内嵌换行，属文档夸大 + 极端边界未覆盖，非当前数据缺陷。 | KDoc 改为「逗号/`""` 转义（不含多行字段）」，或补齐跨行引号字段的状态机扫描。 |
| P2-6 | P2 | `data/local/bill/WechatXlsxParser.kt` | `WechatXlsxParser.kt:60-70`（表头命中后逐行，未跳过空白行）；对照 `AlipayCsvParser.kt:46`（`isBlank() continue`） | **微信表头命中后的空白行计入 NEUTRAL 跳过**：`mapRows` 对空 cells（`{}`）仍走 `parseRow` → 收/支空 → `NEUTRAL`，若账单尾部存在空行（Excel 常见）会虚增「不计收支」计数与其行号明细。与 CSV 侧「isBlank 跳过」口径不一致。 | 在 `mapRows` 内对空 cells 行（size==0）跳过不计数，对齐 CSV 语义。 |

> 无 P0（阻断）项：未发现崩溃/数据损坏/跨账本泄漏/资金精度错误。F-12 主链路（解析→过滤→幂等→单事务写入→UI 四态）无功能性缺陷。

### 遗留项与可接受偏离（逐条评估）

| 项 | 评估 | 结论 |
|----|------|------|
| `bill` 包名（替代 `import`，Java/Kotlin 关键字不可作包段） | 已留注（`ImportComponents.kt:32-33`、`BillParserFactory.kt:8` KDOC），KSP 可编译 | 接受 |
| 纯 regex XML 解码（替代 DEC-016 指定的 XmlPullParser） | 为 JVM 可测性（`WechatXmlRows` 纯函数喂字符串），KDoc 显式留痕、`WechatXlsxParserTest` 覆盖 t=s/str/inlineStr/富文本/实体转义。风险：`<v/>` 自闭合空值单元格不匹配 `V_REGEX`（极少见）；属性值未解实体（当前只解文本）。属有限窄路径可控复杂度 | 接受（记录在案） |
| 详情空值用「—」（对方/支付方式对 manual 记录也显示占位） | §9.3.2 明文指定「空值占位 —」，非偏离；与 §9.3.3「编辑态才隐藏」的层级差异已记 P2-2 | 符合设计 |
| C-37 source 筛选（收支页）未做 | PRD/ARCH/TASK-044 明文「可选增强、不阻塞核心」，`ImportComponents.kt:35-36` TODO 留痕 | 接受（可选增强，挂账） |
| 真机未验（MEIZU 21 未连接） | DEC-021 #3 已降级为「遗留手动验证清单」（迁移保数据/UI 结构/端到端三态 + 精确点击路径与预期计数 206/28、297/2），非造假通过 | 接受（待设备） |
| `eg/` 样例账单未提交 | 含真实个人财务数据，不入 VCS 属隐私正确；真机清单引用其路径 | 接受 |
| androidTest 反引号方法名（P2-1 前期） | 49c0868 已将 13 个反引号含空格方法名全部改 ASCII（HEAD 无残留） | 已闭合 |
| `BillColumn`「对方账号」误捕（P3-1） | 49c0868 改精确 `== "交易对方"`（`BillColumn.kt:40`） | 已闭合 |
| XLSX 临时文件固定名（P3-2） | 49c0868 改 `minibook_import_<uuid>.xlsx`（`SafBillFilePreparer.kt:52,107`） | 已闭合 |

### 总体评价

F-12 履行质量良好：PRD 十项验收全部达成、金额全程整数分、DB 1→2 迁移 SQL/Entity/schema 三方逐字一致且 `fallbackToDestructiveMigration(false)` 未松动、分层清晰且 Android 边界唯一收敛于 `SafBillFilePreparer`、132 JVM 例 + 迁移 instrumented 覆盖与验收逐条对应、三项收尾缺陷（androidTest 方法名 / 对方账号精确匹配 / 临时文件 uuid）均已真实闭合。**无 P0**。
**需 Developer 补 1 项 P1**（P1-1：F-04 备份 JSON 未携带 F-12 四个新字段 → 备份/迁移还原后丢 source/对方/支付方式/externalId，重导同账单将重复入账），另有 6 项 P2（工厂嗅探优先级、§9.3.3 编辑态展示缺失、事务回滚单测口径失真、工厂接口层级、CSV KDoc、XLSX 空行计数）可延后/择机处理。因存在 P1 数据完整性遗漏，结论记为 **FAIL-with-issues**（有 P1 待处理，非无条件 PASS）。

---

## 历史审查记录

- **F-12（2026-08-24）**：Reviewer 评审 F-12 账单导入全量交付（`be1ccdd..HEAD`，`e13eee0`/`913e7f0`/`d9a091a`/`49c0868` 四 commit）。对照 PRD §F-12 + DEC-015~021 + 02 §16 / 03 §7.5 / 04 §13 / 05 §9 逐项核对：功能完整性/金额精度/迁移/分层/错误边界/测试全部达成或通过；**发现 1 项 P1（P1-1 F-04 备份 JSON 未携带 F-12 四个新字段，备份还原后丢 source/对方/支付方式/externalId 且幂等破坏）+ 6 项 P2**（工厂嗅探优先级、§9.3.3 编辑态展示缺失、事务回滚口径失真、工厂接口层级、CSV KDoc 多行、XLSX 空行计数），无 P0。三项收尾缺陷（androidTest 方法名 / 对方账号 / 临时文件 uuid）已确认闭合。结论 **FAIL-with-issues**。详见「F-12 审查报告」章节。
- **Phase 2（2026-08-21）**：Reviewer 单实例评审 Phase 2 增量（`4e1d55e..HEAD`，14 编码 commit + 1 测试 commit）。对照 DEC-010~013 + 02 §15 / 03 §10 / 04 §12 / 05 §8 逐项核对：DEC 合规三项全过、架构一致、编码规范过·建议并存、测试 92 例全绿。发现 **1 项 P1（P1-1 删除分类不清预算 → 孤儿预算静默丢失，需 Developer 返工）+ 6 项 P2**（模型与 §12.3 契约漂移 / 3 文件超 300 行 / 进度条 float 比例 / 首页红点月份固定 / GRANULARITY 命名 / 债务方向窄化），无 P0。待审项 a~d 结论：导航桥保留两种并存（后续全迁 CompositionLocal）、超限文件建议拆（本次不强制）、C-32 不补抽、债务方向映射一致通过。详见「Phase 2 审查报告」章节。
- **Stage 8（2026-08-20）**：3 个 Reviewer 子 agent 按层并行（安全层 / 业务·数据层 / UI 层），审查基线为 `1d09ae1`（Stage 7 测试落地）的工作树。发现 **3 项 Critical（2 项 DEC-002 双钥系统安全缺陷 + 1 项 auth 解锁崩溃）+ 9 项 Warning（W1~W5 业务层错误类型化/校验对齐/事务原子性/账本圈定，U1~U4 三页 Error 态/重试/snackbar 重放）**，全部修复并提交（`b4aa338`、`5315fc3`、`2ea7424`）。本报告于 2026-08-21 依协调日志与 git 历史回填，转为「已确认」。

