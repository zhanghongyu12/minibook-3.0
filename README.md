# MiniBook · 本地优先记账（Android）

> 一个 **完全本地** 的个人记账 App：账目存在手机里、用 SQLCipher 加密，不注册、不联网同步、无遥测。
> 除了「从邮箱收账单」这一条用户主动触发的取件链路外，App 不访问网络。

![平台](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84)
![语言](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF)
![UI](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.09.01-4285F4)
![测试](https://img.shields.io/badge/JVM%20单测-45%20类%20%2F%20333%20例%20全绿-brightgreen)
![版本](https://img.shields.io/badge/version-1.0.0-blue)

---

## 这是什么

MiniBook 是一个 Android 记账应用（`com.minibook`），目标是把「记一笔」和「对账」这两件事做得尽量省事：

- **手动记**：收支、债务（借入/借出/收款/还款）、分类、多账本、预算、报表、搜索。
- **不用手抄**：把**微信/支付宝账单文件**（F-12）或**邮箱里的账单邮件**（F-13）直接导入，自动解析成交易并幂等去重。
- **隐私优先**：数据库整体用 SQLCipher 加密；邮箱授权码用 Android Keystore（AES-256/GCM）单独加密，只落 `noBackupFilesDir`；没有账号体系、没有云端。

> 本项目的仓库原先放的是「AI 协作开发模板（AI Work Model）」的说明文档，与 App 无关。
> 原文件已原样备份到 [`docs/archive/README-legacy-ai-work-model-2026-07-24.md`](docs/archive/README-legacy-ai-work-model-2026-07-24.md)。

## 功能一览

| 编号 | 功能 | 说明 |
|---|---|---|
| F-01 | 收支记录 | 金额以「分」为单位的 `Long` 存储，无浮点误差；来源/对方/支付方式等字段 |
| F-02 | 债务管理 | 借入/借出、收款/还款、结清与余额联动 |
| F-03 | 分类管理 | 图标 + 颜色 + 支出/收入方向；删除分类会级联清理其预算行 |
| F-04 | 数据导入导出 | 备份 JSON 导出/导入，含 `externalId` 等字段，防止还原后重导重复入账 |
| F-05 | 应用锁 | BiometricPrompt，首启生成主密钥，双钥系统包裹 |
| F-06 | 搜索 | 关键字 + 条件过滤（Phase 2） |
| F-07 | 报表统计 | Canvas 自绘图表，零第三方图表库（Phase 2） |
| F-08 | 预算管理 | 按分类/周期预算与进度守卫（Phase 2） |
| F-09 | 暗色模式 | 亮/暗双主题，语义色 token 统一 |
| F-10 | 数据加密 | Room + SQLCipher 全库加密 |
| F-11 | 多账本 | 账本切换、隔离统计 |
| F-12 | 微信/支付宝账单导入 | 读取账单文件（CSV/XLSX/zip），解析 → 预览 → 单事务整批入库，`UNIQUE(ledgerId, externalId)` 幂等 |
| F-13 | 邮件账单自动导入（Phase 3） | QQ 邮箱 IMAP **只读**拉取 → 本地匹配 → 下载 → 口令穷举解压 → 复用 F-12 入库链路 |

### F-13 的两个关键设计

1. **每种账单只取最新一封**：同一平台（微信/支付宝）在拉取窗口内可能有多封账单邮件。`MailBillSelector` 只保留 `sentDate` 最新的一封，其余**不下载、不处理**，在进度里逐封显示
   `⏭ 支付宝 · … — 已有更新的同类账单，跳过`，并在筛选摘要里披露
   `命中 N 封（微信 x / 支付宝 y），每种账单只取最近一封 → 待处理 K 封`。
   口径是**只选不删**：已导入的历史数据不受影响，重跑幂等。
2. **全程只读邮箱**：`folder.open(Folder.READ_ONLY)` + `close(false)`，不写 `\Seen`、不 delete/copy/append、**零服务端 SEARCH**（拉取邮件头后本地过滤）。

### 导入页（看得见每一步）

一键收取是一个**单页**流程（进度与结果同页，成功/失败都不换版式）：5 步 stepper（连接邮箱 / 拉取邮件 / 筛选账单邮件 / 处理账单邮件 / 完成汇总，每步带中文摘要）+ 第 4 步展开区（`第 i/N 封 · 主题` + 子步骤 + 口令穷举百分比）+ 逐封结果行（`✅/⏭/❌`）+ 钉底的「重新选择 / 返回」。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言/UI | Kotlin 2.2.0、Jetpack Compose（Material 3，BOM 2025.09.01） |
| 架构 | 单 Activity + Compose Navigation、MVVM + UseCase 分层、Hilt 依赖注入 |
| 存储 | Room 2.8.4 + SQLCipher 4.6.1（全库加密）、DataStore（偏好） |
| 安全 | Android Keystore（AES-256/GCM）、BiometricPrompt、`noBackupFilesDir` |
| 网络 | JavaMail（`com.sun.mail:android-mail` 1.6.7）——**仅** F-13 邮件拉取与账单下载使用 |
| 构建 | AGP 8.13.0、KSP 2.2.0-2.0.2、JDK 21、`minSdk 26 / targetSdk 36 / compileSdk 36` |
| 测试 | JUnit 4.13.2 + kotlinx-coroutines-test + Room testing（JVM）；androidx.test（instrumented） |

## 构建与运行

前置：**JDK 21**、Android SDK（platform 36 + build-tools）、Windows 下用仓库自带的 Gradle Wrapper。

```powershell
# 调试包 + 全量 JVM 单测
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest

# 产物
#   app\build\outputs\apk\debug\app-debug.apk
```

安装到设备：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 出正式（签名）Release 包

签名口令与密钥路径放在 **仓库根目录的 `keystore.properties`**（该文件与 `*.jks` 都已在 `.gitignore` 中忽略，**不会进版本库**）：

```properties
storeFile=keystore/minibook-release.jks
storePassword=…
keyAlias=minibook
keyPassword=…
```

```powershell
.\gradlew.bat :app:assembleRelease
# 产物：app\build\outputs\apk\release\app-release.apk（已签名）
```

- 该文件缺失时 `assembleRelease` 仍会成功，但产物**未签名、无法安装**——这是刻意设计，避免克隆仓库后构建失败。
- ⚠️ **签名密钥必须备份**（`keystore/` + `keystore.properties`）。密钥丢失后，已安装的用户将**无法覆盖升级**。
- 当前 `release { isMinifyEnabled = false }`：release 与 debug 同为**非混淆、非裁剪**构建；`app/proguard-rules.pro` 中的 JavaMail keep 规则为**备用配置**，尚未实际生效。若要开启混淆，请把 `isMinifyEnabled`/`isShrinkResources` 打开并**重跑真机验证**（JavaMail 有反射与服务发现）。

## 测试与验证

| 项 | 状态 |
|---|---|
| JVM 单测 | **45 类 / 333 例 / 0 失败**（`.\gradlew.bat :app:testDebugUnitTest`） |
| 构建 | `:app:assembleDebug`、`:app:assembleRelease` 均 BUILD SUCCESSFUL |
| 真机端到端（F-13） | **已通过**（设备 `A1B2C3D4`）：拉取 → 命中 → 裁剪 → 逐封处理 → 幂等入库；含「多封同类账单只处理最新一封」与「跳过行/FILTER 披露」实测 |
| 真机（F-12） | **已补验**（2026-08-24：安装 + DB 迁移保数据 + 支付宝端到端 + 幂等去重通过；微信单独重导与 4 笔解析失败明细见 `docs/09_test_report.md`） |
| instrumented（androidTest） | 用例已编写；**真机执行仍挂账**（见 `docs/09_test_report.md`） |

单测覆盖的重点是**纯 Kotlin 可测边界**：账单解析（CSV/XLSX/zip）、文件名解码（RFC2047/2231）、HTML 取链、ZipCrypto 判定与口令穷举、导入编排与幂等、每种账单只取最新一封的选择器、进度与文案 presenter。
不可 JVM 测的 I/O 边界（`ImapMailClient`、`MailBillDownloader`、SAF 文件准备等）以**真机端到端**覆盖，未引入额外测试依赖。
如实限制与未验证项集中记录在 [`docs/04_api.md`](docs/04_api.md) §14.9「真机验证结论与已知限制」。

## 权限与隐私

| 权限 | 用途 |
|---|---|
| `INTERNET` | 仅 F-13 邮件账单导入：连接 IMAP 拉取邮件、HTTPS 下载账单文件 |

- 数据只存在设备本地：Room + **SQLCipher 加密**；邮箱授权码经 Android Keystore 加密后落 `noBackupFilesDir`。
- 没有账号、没有自建服务端、没有行为统计/崩溃上报 SDK。
- 邮箱侧**全程只读**，不改动任何邮件状态；异常文案中不包含下载链接的 token。
- 备份 JSON 由用户主动导出，App 不自动上传。

## 目录结构

```
app/src/main/java/com/minibook/
├── data/            数据层：Room（DAO/Entity/迁移）、mail（IMAP 客户端与凭据）、security（Keystore 加解密）
├── domain/          领域层：model / repository 契约 / usecase（含 bill 账单导入、mail 邮件账单、zip 解压与口令穷举）
├── ui/              Compose UI：billimport（导入页 + 进度/结果）、mail（邮箱凭据设置）、main/navigation、各功能页
└── di/              Hilt 模块
app/src/test/        JVM 单测（45 类 / 333 例）
app/src/androidTest/ instrumented 用例（真机执行挂账）
docs/                项目文档（见下）
```

## 文档地图

| 文件 | 内容 |
|---|---|
| [`docs/01_prd.md`](docs/01_prd.md) | 产品需求（F-01 ~ F-13） |
| [`docs/02_architecture.md`](docs/02_architecture.md) | 架构设计（分层、模块、关键口径） |
| [`docs/03_database.md`](docs/03_database.md) | 数据库设计（表结构、迁移 1→2） |
| [`docs/04_api.md`](docs/04_api.md) | 接口契约 + **§14 邮件账单导入** + **§14.9 真机验证结论与已知限制** |
| [`docs/05_ui.md`](docs/05_ui.md) | UI 设计与文案（含 §10 导入页逐条文案/布局纪律） |
| [`docs/06_tasks.md`](docs/06_tasks.md) | 任务清单与协调日志 |
| [`docs/07_decisions.md`](docs/07_decisions.md) | 决策记录（DEC-001 ~ DEC-023） |
| [`docs/08_review.md`](docs/08_review.md) | 代码审查报告 |
| [`docs/09_test_report.md`](docs/09_test_report.md) | 测试报告 |
| [`docs/10_release_notes.md`](docs/10_release_notes.md) | 发布说明 |
| [`docs/12_mail_bill_import.md`](docs/12_mail_bill_import.md) | F-13 邮件账单导入设计说明 |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | 变更日志 |
| [`docs/archive/`](docs/archive/) | 历史归档（含本 README 的原「AI 协作模板」版本） |

## 已知限制

- **release 未做混淆/裁剪**（`isMinifyEnabled = false`），`app-release.apk` 体积因此偏大（约 31 MB）。
- 邮件账单导入的 `LinkExpired`（链接过期）与 `UnsupportedEncryption`（AES 加密 zip）分支**尚无真实样本**，仅代码级与单测覆盖。
- 口令穷举失败时**不支持手动输入口令**，只提示 `PasswordNotFound`。
- 邮箱侧 `\Seen` 状态为**代码级保证**（`READ_ONLY` + 不写 flag），未逐字段比对邮箱服务器状态。
- instrumented 用例未在真机跑；`app/schemas` 迁移测试已备好用例。

## 版本

当前 **v1.0.0**（`versionCode 1`）。发布产物见本仓库 **Releases** 页面的 `minibook-v1.0.0-release.apk`。
