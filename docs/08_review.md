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

## 历史审查记录

- **Stage 8（2026-08-20）**：3 个 Reviewer 子 agent 按层并行（安全层 / 业务·数据层 / UI 层），审查基线为 `1d09ae1`（Stage 7 测试落地）的工作树。发现 **3 项 Critical（2 项 DEC-002 双钥系统安全缺陷 + 1 项 auth 解锁崩溃）+ 9 项 Warning（W1~W5 业务层错误类型化/校验对齐/事务原子性/账本圈定，U1~U4 三页 Error 态/重试/snackbar 重放）**，全部修复并提交（`b4aa338`、`5315fc3`、`2ea7424`）。本报告于 2026-08-21 依协调日志与 git 历史回填，转为「已确认」。

