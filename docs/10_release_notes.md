# MiniBook Android — 发布说明

> 状态：已确认
> 最后更新：2026-08-20
> 维护者：Coordinator

## v1.0.0（MVP 首版，2026-08-20）

MiniBook Android 重构版首个正式版本。Kotlin + Jetpack Compose + Material 3 技术栈，
本地优先（SQLCipher 加密 + Keystore 密钥保护），云端出口预留（Phase 3）。

### 已交付功能（MVP / P0）

| 编号 | 功能 | 说明 |
|---|---|---|
| F-01 | 收支记录 | 收入/支出记录，金额按「分」存 Long（杜绝浮点）；日期/分类/备注 |
| F-02 | 债务管理 | 借/还平级模型（lend/repay × owe_me/i_owe 四态），明细 + 按对象双视图，净额口径 CASE WHEN SQL |
| F-03 | 分类管理 | 内置分类（不可删/不可改 type+isBuiltIn，DEC-007）+ 自定义分类；删除被引用分类走迁移 |
| F-04 | 数据导入导出 | 明文 JSON 全量备份（DEC-005）；legacy（旧版）JSON 导入默认账本（DEC-004）；排除 DB+key（DEC-006） |
| F-05 | 应用锁 | 生物识别解锁 + 冷启动锁门（key(isLocked)）+ 离开超时重锁；无锁屏不降级明文（DEC-003） |
| F-09 | 暗色模式 | 跟随系统 / 手动亮暗，Material 3 语义色 + 动态配色（Android 12+） |
| F-10 | 数据加密 | SQLCipher 整库加密；双钥系统（auth + plain 包裹同一 master key，DEC-002），关锁 DB 仍加密 |
| F-11 | 多账本 | 数据按账本隔离，创建/切换/删除（默认账本禁删），账本切换自动重订阅 |

### 技术栈（DEC-001 锁定）

- Kotlin 2.2.0 + KSP，Jetpack Compose BOM 2025.09.01，Material 3
- Room 2.8.4 + SQLCipher 4.6.1，Android Keystore（AES-256/GCM）
- Hilt 2.58，Biometric 1.1.0，DataStore
- AGP 8.13.0，compileSdk 36，minSdk 26，targetSdk 36，JDK 21

### 构建

- `./gradlew :app:assembleDebug` → debug APK
- `./gradlew :app:assembleRelease` → release APK（**当前为 unsigned**，未配置签名 keystore）
- `./gradlew :app:testDebugUnitTest` → JVM 单元测试（56 例，全绿）
- `./gradlew :app:connectedAndroidTest` → instrumented 测试（需设备/模拟器，3 个用例：净额口径 / 月度汇总 / 迁移事务骨架）

### 安全实现要点

- **双钥系统（DEC-002）**：SQLCipher master key（32B）由 AndroidKeyStore 两个别名包裹——
  `minibook_master_key_auth`（需生物识别授权）与 `minibook_master_key_plain`（静默）。
  切换锁状态时用当前可用 key 解出 master key 再以目标 key 重包裹覆盖 `minibook_key.bin`；
  关闭应用锁时 DB 仍加密（不降级明文）。
- **冷启动锁门（ARCH §12）**：NavHost 以 `key(isLocked)` 重建，锁定态仅注册 Lock 目标（不可达即安全）。
- **首启引导**：首次启动无 `minibook_key.bin` 时按当前锁状态生成 master key 并落盘（锁关闭→plain 包裹、锁开启→认证后 auth 包裹）。
- **金额口径**：存储一律「分」Long，展示经 MoneyFormatter 整数拆分（无 Float/Double 中间态）。
- **净额口径（PRD F-02）**：lend×owe_me=+、lend×i_owe=−、repay×owe_me=−、repay×i_owe=+。

### 已知限制 / 待办

- **release 未签名**：本地无 keystore，release 构建产出 `app-release-unsigned.apk`。发布到应用商店前需配置正式签名。
- **instrumented 测试未执行**：3 个用例（净额/monthlySummary/迁移原子性）已编写并编译通过，待设备验证（Stage 9 后续或交付测试团队）。
- **Phase 2 功能**：搜索（F-06）、报表统计（F-07）、预算管理（F-08）为占位页，本期不实现。
- **Phase 3 预留**：云端同步（SyncChangeRepository 接口已定义，未接线实现）。
- **历史 debt（非阻塞）**：lifecycle-process 依赖集、@IoDispatcher 限定符、material-icons 正式依赖、BackupV1 补 appVersion/exportedAt 字段等。

### 版本信息

- versionName: 1.0.0
- versionCode: 1
- applicationId: com.minibook
