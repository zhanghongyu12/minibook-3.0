# Changelog

本项目所有重要变更均会记录在此文件中。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added

- **首页分类联表**：首页最近交易列表显示真实分类名/图标/颜色（经 ObserveCategoriesUseCase 的 id→Category 映射，替代原 null 占位 ❔）。
- **可测性改造**：DatabaseGate 提炼 DatabaseProvider 接口，RoomTransactionRunner/RoomDataSourceImpl 改为依赖接口（加密语义不变），使事务原子性可在 androidTest 用内存库测试。

### Changed

### Deprecated

### Removed

### Fixed

- **事务原子性测试**：补全 TransactionRunnerAtomicityTest 三个空骨架用例为真实断言（deleteById 三表分发 / runInTransaction 异常回滚 / deleteByLedger 四表批删），内存库 + DatabaseProvider 替身，无新增依赖。

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
