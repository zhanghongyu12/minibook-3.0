# Changelog

本项目所有重要变更均会记录在此文件中。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

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

### Changed

### Deprecated

### Removed

### Fixed

- **事务原子性测试**：补全 TransactionRunnerAtomicityTest 三个空骨架用例为真实断言（deleteById 三表分发 / runInTransaction 异常回滚 / deleteByLedger 四表批删），内存库 + DatabaseProvider 替身，无新增依赖。
- **P1-1 孤儿预算静默丢失**：删除分类时于同一事务内级联清理该分类的预算行（`BudgetDao.deleteByCategoryId` + UseCase 事务闭合），防分类删除后其预算在 budgets 表成孤儿、从预算进度静默消失。
- **F-12 评审收口（1×P1 + 5×P2）**：备份 JSON 的 `TransactionDto` 补交易 4 新字段并双向映射（P1-1，`source` 旧 JSON 缺省兜底 `manual`，防备份/迁移还原后丢 `externalId` 致重导重复入账）；工厂按 zip 魔数前置嗅探（防 `.csv` 名 zip 内容失配）；编辑弹层加来源/对方/支付方式只读信息块；补 `insertBatch` 抛异常回滚单测；修正支付宝 CSV KDoc「换行」声明；微信 XLSX 空白行跳过（对齐 CSV 口径）——详见 08_review.md 与 cb679a7。

### Security

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
