# MiniBook Android 重构版 - 决策记录

> 状态：已确认（本文件为决策档案，持续追加）
> 维护者：全员可追加（Architect 汇总本阶段决策）
> 最后更新：2026-08-18
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

（本文件为设计文档，随实现对细节的差分由后续修订更新；版本历史由协调者统一管理。）