# 测试报告

> 状态：已确认
> 最后更新：2026-08-21
> 维护者：Tester

## 最新测试（Phase 2）

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
