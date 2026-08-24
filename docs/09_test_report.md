# 测试报告

> 状态：已确认（F-12 真机项待设备补验）
> 最后更新：2026-08-24
> 维护者：Tester

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

### 遗留手动验证清单（设备 MEIZU 21 连上后执行）

> 前置：`adb install -r app\build\outputs\apk\debug\app-debug.apk`（覆盖安装不卸载）；**先确认设备上已有旧版（DB v1）且存在交易数据**，否则迁移验证无意义。

1. **真机迁移保数据**：安装 → `adb shell am start -n com.minibook/.MainActivity`。预期：不崩溃、正常解锁进首页、**旧交易数据仍在**（列表可见）。佐证：`adb logcat -d` 搜 `Room|SQLiteException|IllegalState|Migration` 无「迁移失败/整库重建」迹象。
2. **入口核对**：uiautomator dump 确认首页有「导入账单」tile（📥）；设置「数据」区有「导入账单」行（位于「导出备份/导入备份」之后）。
3. **导入页 Idle 态**：点「导入账单」→ 确认顶栏「导入账单」+ 引导说明 + 微信/支付宝两 `PlatformGuideCard`（含导出路径与 `.xlsx`/`.csv` 格式提示）+「去选择文件」按钮。截图：`cmd /c '"D:\soft\android-sdk\platform-tools\adb.exe" exec-out screencap -p > f12_idle.png'`。
4. **端到端导入（SAF 尽力）**：`adb push eg\支付宝交易明细*.csv /sdcard/Download/`、`adb push eg\微信支付账单流水文件*.xlsx /sdcard/Download/` → 「去选择文件」→ 系统文件选择器选 `/sdcard/Download/` 下的文件。预期结果页：
   - **支付宝**（234 笔 = 支出 214 + 不计收支 20；交易关闭 8 均属支出）→「成功导入 **206** 笔、跳过 **28** 笔（不计收支 20 / 交易关闭 8）」；
   - **微信**（299 笔 = 收入 42 + 支出 255 + 中性 2）→「成功导入 **297** 笔、跳过 **2** 笔（不计收支 2）」；
   - **重复导入同一文件** →「本次无新增交易」、跳过=已存在（206 / 297），不产生重复行；
   - 交易列表出现「微信/支付宝」`SourceBadge`；点进详情见「对方/支付方式/来源」；手动记账行无徽标。
   - SAF 系统选择器全自动较难：至少验证到「拉起系统文件选择器」与「取消后回 Idle 无提示」即可，其余留人工。

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
