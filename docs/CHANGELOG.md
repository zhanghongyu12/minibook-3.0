# Changelog

本项目所有重要变更均会记录在此文件中。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## v1.0.0 收尾

P2 技术债清账 + 文档收口（本地 debug 交付，不涉及真机 / release / 云端）。

### Added

- 导入账单平台提示卡可点击直接选文件（4ccaca1）。
- 设置页主题颜色调色盘（d0759b3）。
- 主题色覆盖容器色与底部菜单栏（0f748f7）。

### Changed

- **P2-5 枚举命名**：`GRANULARITY` 改名 `Granularity`（类名大驼峰），同步报表 ViewModel/Screen/UseCase 与测试引用（b9c0e6f）。
- **F-12 P2-4 接口分层**：`BillParserFactory` 接口由 `data/local/bill` 归位 `data/datasource`（与 `TransactionRunner` 对称），实现 `DefaultBillParserFactory` 留 `data/local/bill`（7c42f4c）。
- **P2-2 超限文件拆分**：`SearchScreen.kt` / `BudgetsScreen.kt` / `SearchViewModel.kt` 拆至 ≤300 行——`OptionalDateField` 下沉 `ui/components`、搜索条件区归 `ui/search/components`、预算进度卡收敛为单一可复用组件、搜索过滤纯函数抽独立文件（f4fd92d）。
- **待审项 a 导航统一**：账本管理入口 `openLedgerManage()` 加入 `HomeNavigation`（CompositionLocal），Home / Settings 经 `LocalHomeNavigation` 读取，删除 `MainScreen.onNavigateToLedgerManage` 参数穿透（63eaa26）。
- **文档回写**：`docs/04_api.md §12.3` BudgetProgress 契约对齐实现（`month` 为 `String`(yyyy-MM)、总预算扁平 `totalBudgetFen`/`totalUsedFen` 0 哨兵、`category` 非空 + 孤儿预算跳过）。

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

- **邮件账单自动导入（F-13，设计与决策）**：新增「自动取邮箱账单并导入」功能设计——QQ 邮箱 IMAP（授权码 + `ID` 命令）拉取 → **本地过滤**账单邮件（实测服务端 `SEARCH` 在本场景返回 0，禁用）→ 支付宝取邮件附件 / 微信取正文下载链接（HTTPS，链接 7 天有效）→ **自动穷举 6 位数字口令**（实测口令恒为 6 位纯数字且每期不同）→ ZipCrypto 解密 + 原生 `Inflater` 解压 → 交给 F-12 既有导入链路。新增 `12_mail_bill_import.md`（含全部实测证据与排除记录）+ `DEC-022`（技术路线）+ `DEC-023`（网络/凭据隐私边界修订）+ `01_prd.md §F-13` + `02_architecture.md §17` + TASK-045~050。
  - 已排除路线（留档避免重复尝试）：POP3、邮箱厂商 SDK/OAuth2、**已知明文攻击（bkcrack）**——实测两条目均为 Deflate 压缩，该攻击要求提供「压缩后字节流」而动态 Huffman 首块不可预测；以及「记住口令」方案（口令每期变化，已证伪）。
  - 依赖：新增 JavaMail（`android-mail`/`android-activation` 1.6.7，约 1 MB）+ `INTERNET` 权限；**不引入 zip4j**（实测为 ZipCrypto 非 AES，原生方案足够）。

- **邮件账单自动导入（F-13，实现）**：按 DEC-022 路线全量落地——QQ IMAP 授权码连接（`ID` 命令双保险、`READ_ONLY` 只读、30 天窗口拉头 + `MailBillMatcher` 本地过滤、**零服务端 SEARCH**）→ 支付宝取附件（`MailFilenameDecoder`：RFC2047 GB18030 Q/B + RFC2231 `name*N` 续行）/ 微信取正文链接（`WechatBillLinkExtractor` 精确 host+路径）→ `MailBillDownloader` HTTPS 下载（每跳强制 https、≤5 重定向、30s 超时、异常文案不含链接 token）→ `ZipArchiveInspector` 加密类型探测（method 99 / extra `0x9901` → 拒绝穷举）→ `ZipPasswordCracker` 6 位数字穷举（两段式：12 字节头快筛宽松双约定 + 解压后内容 CRC32 权威判定；前缀密钥状态预计算 + `Dispatchers.Default` 分片并发 + 4096 批次取消检查）→ `EncryptedZipExtractor` 解压（GBK/CP437 条目名）→ 交 F-12 链路（`ImportBillUseCase.importLocalFile` 与 `invoke` 共用 `importPrepared` 核心，口径零分叉）。凭据：`MailCredentialCipher`（Keystore AES-256/GCM 独立别名）+ `MailAccountStore`（落 `noBackupFilesDir`，解密失败防御性置空）+ 新增 P-16 邮箱账单设置页（`mail_account` 路由）；导入页新增 `MailBusy`/`MailResult`/`MailNotConfigured`/`MailError` 四态 + `MailImportPresenter`（9 分支失败文案 `when` 穷尽）。
  - 规模：新增主源码 17 文件 / 2344 行、测试 7 文件 / 951 行 / 70 例（`MailBillMatcherTest` 9 / `MailFilenameDecoderTest` 16 / `WechatBillLinkExtractorTest` 9 / `ContentDispositionParserTest` 6 / `ZipCryptoTest` 12 / `ZipPasswordCrackerTest` 6 / `MailImportPresenterTest` 12）；`assembleDebug` 绿（APK ≈35.8 MB）；全量单测 40 类 / 260 例 0 失败。
  - 真实账单验收（本地实跑，测试按约定删除）：三份真实账单 zip 判型均为 ZipCrypto、**全部命中 6 位数字口令**（单份 90~760 ms，多核亚秒级），解出字节与独立 Python oracle 完全一致。
  - 如实限制（详见 `04_api.md §14.9`）：手动输入口令 UI 未实现（穷举失败降级为结果页 `PasswordNotFound` 提示）；`ImapMailClient`/`MailBillDownloader`/`MailAccountStore` 无 JVM 单测（JavaMail 无假服务器、Context/Keystore 无替身，未引入新测试依赖）；`totalMailCount == billMailCount`（窗口内邮件总数不可得不伪造）；真机端到端、release 构建待设备/后续。
  - 文档回写：`04_api.md §14`、`05_ui.md §10`、`06_tasks.md` TASK-045~050、`07_decisions.md` DEC-022/023 实施状态、`12_mail_bill_import.md §11`。

- **邮件账单自动导入（F-13，分步骤进度可视化 + 多邮箱）**：一键收取升级为「看得见每一步」——5 步竖向 stepper（连接邮箱 / 拉取邮件 / 筛选账单邮件 / 处理账单邮件 / 完成汇总，每步附中文摘要：`已连接（IMAP/SSL）` / `窗口内共 N 封` / `命中 N 封（微信 x / 支付宝 y）` / `成功 N 封 / 失败 M 封` / `新导入 … / 已存在 …`）+ 第 4 步展开区（`第 i/N 封 · 文件名` + 子步骤如 `下载账单` / `解压账单` + 口令穷举百分比）+ 逐封结果行卡（`✅/❌ 平台 · 文件名 — 结果`）。设置页「邮箱账单」更名**「邮箱凭据」**并支持**多账号**（`MailAccountStore.accounts` 列表 + `upsert`/`delete`：0 个引导配置、1 个直接收取、≥2 个弹「选择邮箱」选择框 —— 选一个 / 全部收取 / 取消）。新增 `MailImportProgress` / `MailImportStep` / `MailImportStepState` / `MailImportSummary` 等模型（`domain/model/MailImportModels.kt`）。
- **邮件账单自动导入（F-13，进度页与结果页合并为单页）**：按用户要求**成功或失败都不再切换版式**——`MailBusy` / `MailResult` / `MailError` 三态统一渲染同一个 `MailImportContent`（单页单滚动容器：`verticalScroll` 内容区 + 钉底「重新选择 / 返回」按钮），成功后**原地追加**结果摘要与失败明细，失败时**原地追加**错误卡；删除旧的独立结果页组件（`ImportMailResultContent`），`ImportScreen` 的 F-12 SAF 文件导入链路不动。
- **测试**：F-13 JVM 单测大幅扩展——`MailHtmlTextTest` 19 例（含真机邮件 HTML 形态的实体转义 / 自闭合标签 / 引号内 `>` 边界）+ `WechatBillLinkExtractorTest` 21 例（含尾随标点、host 伪装、端口与路径边界）+ `MailImportStepTrackerTest` 7 例（进度快照累积回归，含真机 `○○○○✓` 场景复刻）；全量 **44 类 / 327 例 0 失败**。
- **真机验证（A1B2C3D4，2026-09-23）**：一键收取端到端通过——窗口内 13 封邮件命中 5 封账单邮件（微信 2 / 支付宝 3）**全部成功**（`成功 5 封，失败 0 封`；两封微信账单分别映射 211 / 231 条且幂等去重生效，二次收取 `新导入 0 条，已存在 896 条`）；子步骤文案与进度头部 `邮箱 k/N` 按预期渲染；多邮箱「选一个 / 全部收取 / 取消」实测可用；授权码缺失的账号在 stepper 上以 `✗ 连接邮箱 · 授权码不可用（解密失败），已跳过该邮箱` 显式呈现。

### Changed

- **F-13 微信取链关键路径「零正则」重写（Android ICU 教训）**：`MailHtmlText.toPlainText` 改为手写引号感知 HTML 扫描器（`tagKindAt` / `findTagEnd` / `findUrlAttribute`，`>` 在引号内不算标签结束），`WechatBillLinkExtractor.extract` 改为手写 URL 扫描 + 精确匹配（host 必须全等 `tenpay.wechatpay.cn`、可选端口后必须紧跟 `/`，防仿冒域名），两文件**彻底不含 `Regex(` / `toRegex`**；`IcuSafeRegexTest` 绊线测试持续守护（扫描 F-13 源码中的危险正则字面量）。
- **F-13 邮件账单导入「每种账单只取最近一封」**：`MailBillImportUseCase` 在 FILTER 后经 `MailBillSelector`（`domain/usecase/mail/MailBillSelector.kt`，纯 Kotlin、JVM 可单测）按平台（支付宝/微信）各保留 `sentDateMillis` 最新一封，同平台旧邮件**不处理**——`itemLines` 留 `⏭ {平台} · {主题} — 已有更新的同类账单，跳过`，FILTER 摘要追加「，每种账单只取最近一封 → 待处理 K 封」；`billMailCount` 仍按命中数计。新增 `MailBillSelectorTest` **6 例**（空输入 / 每平台各一封全保留 / 同平台多封取最新 / 真机窗口形态 2 微信 + 3 支付宝 → 只留 2 封 / 同毫秒按主题字典序兜底且与输入顺序无关 / 单平台多封仅留一封并校验 `hitCount = kept + skipped`；`kept`/`skipped` 顺序保持由前两例断言）；全量 **45 类 / 333 例 0 失败**。**真机验证（A1B2C3D4，2026-10-09）**：窗口内 3 封命中 3 封（微信 1 / 支付宝 2）→ 摘要实测「每种账单只取最近一封 → 待处理 2 封」，旧支付宝账单以 `⏭ …已有更新的同类账单，跳过` 显式跳过，`成功 2 封 / 失败 0 封`、幂等 `新导入 0 条 / 已存在 240 条`；「选中的确为最新」由「保留的 ✅ 行为 21 条（≠ 旧账单 111 条）」判定。2026-09-23 那次的「5 封 → 2 封」已无法复现（旧账单不在收件箱了），差异与依据见 `04_api.md §14.9`。

### Deprecated

### Removed

### Fixed

- **事务原子性测试**：补全 TransactionRunnerAtomicityTest 三个空骨架用例为真实断言（deleteById 三表分发 / runInTransaction 异常回滚 / deleteByLedger 四表批删），内存库 + DatabaseProvider 替身，无新增依赖。
- **P1-1 孤儿预算静默丢失**：删除分类时于同一事务内级联清理该分类的预算行（`BudgetDao.deleteByCategoryId` + UseCase 事务闭合），防分类删除后其预算在 budgets 表成孤儿、从预算进度静默消失。
- **F-12 评审收口（1×P1 + 5×P2）**：备份 JSON 的 `TransactionDto` 补交易 4 新字段并双向映射（P1-1，`source` 旧 JSON 缺省兜底 `manual`，防备份/迁移还原后丢 `externalId` 致重导重复入账）；工厂按 zip 魔数前置嗅探（防 `.csv` 名 zip 内容失配）；编辑弹层加来源/对方/支付方式只读信息块；补 `insertBatch` 抛异常回滚单测；修正支付宝 CSV KDoc「换行」声明；微信 XLSX 空白行跳过（对齐 CSV 口径）——详见 08_review.md 与 cb679a7。
- **微信下载链接丢失（F-13 实现期发现并修复）**：`ImapMailClient.ExtractedContent.bodyText()` 原仅取 `text/plain` 部分、`stripHtml()` 原先移除标签导致 `href` 中的 URL 一并丢失 → 微信账单（正文链接仅在 HTML `<a href>`）取不到下载链接。修复：`bodyText()` 拼接 plain+html 两路、`stripHtml()` 先提取并保留 `href`/`src` URL 再去标签。
- **JavaMail 打包冲突（F-13 实现期发现并修复）**：`android-mail`/`android-activation` 1.6.7 的 `META-INF/NOTICE.md`（及其他 META-INF 元数据）与既有依赖打包冲突导致 `assembleDebug` 失败 → `packaging` 块 `excludes` 排除 5 个冲突元数据文件；另补 R8 `-dontwarn` 与 keep 规则（`javax.mail.**`/`com.sun.mail.**`/`javax.activation.**`）。
- **邮箱凭据保存即崩溃 + 设置页恒显示「未配置」（F-13，真机实测发现并修复）**：`MailAccountCodec` 的条目正则含 `\}`，Android ICU 在**类初始化**时抛 `PatternSyntaxException` → `ExceptionInInitializerError`（属 `Error`，`catch (Exception)` 兜不住）→ 点「保存」即闪退、设置页恒显示「未配置」、且迁移已把旧授权码文件改名而 json 从未写出（旧凭据险些丢失）。修复：codec 改为**零正则**手写字符扫描（含邮箱格式校验），`MailAccountStore.loadOrMigrate` 读路径改 `catch (Throwable)`、写入后**回读校验通过才删旧文件**、失败整体回滚并保留旧文件（迁移失败日志只记异常类名、不含凭据），`upsert` 回滚 catch 同步加固为 `Throwable`。
- **步进行长摘要把标签挤成竖排（F-13，真机发现并修复）**：`MailImportStepRow` 里摘要 `Text` **没有任何宽度约束**，而 Compose 的 `Row` 会**先量无权重子项** → FILTER 摘要加长后（「…，每种账单只取最近一封 → 待处理 K 封」）吃满整行，标签「筛选账单邮件」被挤到近乎 0 宽、**竖排成单字**（真机 2026-10-09 用户截图复现；同一缺陷对失败行长摘要「授权码不可用（解密失败），已跳过该邮箱」同样成立，属既有隐患被新文案放大）。修复：标签与摘要**各带权重**（`StepRowLabelWeight 1f : StepRowSummaryWeight 2.4f`）且摘要 `TextAlign.End`，超宽只在自己那一列折行；短摘要行的渲染与修复前逐像素一致。真机复验（A1B2C3D4，2026-10-09）：5 行标签统一 `x[133-426]`（293px）、摘要统一 `x[450-1152]`（702px），筛选行摘要折两行且标签正常，uiautomator dump 全页**无文字重叠、无越界、无单字竖排**；布局纪律已写入 `05_ui.md §10`。
- **微信账单「邮件里没有找到下载链接」（F-13）**：真机 2 封微信邮件全部失败 → 定位后用零正则重写取链路径（见 Changed）并在真机复测通过（`成功 5 封，失败 0 封`）。**如实记录**：真机诊断（旧正则匹配次数 / HTML 是否含链接 / 去标签后链接是否存活）证明**旧正则在真机 ICU 上匹配正常**，最初「ICU 语义差异导致匹配失败」的推断**不成立**；旧实现的确切根因已不可回溯（旧 APK 与旧文件均被覆盖、且未提交版本历史）。结论以真机端到端通过为准，并以零正则重写 + ICU 绊线测试作为永久防线。
- **收取成功后 stepper 显示 `○○○○✓`（F-13，进度快照不累计）**：每份快照把未列出的步骤一律补 `PENDING`，已完成的步骤被后续快照打回待执行。修复：新增 `MailImportStepTracker` 累积步骤状态（每账号一份实例），同一快照序列正确显示 `✓✓✓✓✓`。
- **被跳过的邮箱在 UI 上不可见（F-13，多账号真机验证暴露）**：跳过分支只往 `itemLines` 追加 ❌ 而不发进度快照 → UI 永远看不到，用户会误以为「全部收取」覆盖了所有邮箱（静默漏数据提示缺失）。修复：跳过账号也发快照（stepper「连接邮箱」标 `FAILED` + 原因，其余 `PENDING`，携带累积逐封明细）。

### Security

- **F-13 隐私/权限边界（DEC-023）**：本项目**首次引入网络与外部凭据**，对 PRD §7「所有功能离线可用、无网络依赖」做**显式例外化**（仅 F-13 邮箱账单拉取）。边界写死为：邮箱授权码与 SQLCipher master key **同级保护**（Keystore AES-256/GCM，不落明文、不进日志）；IMAP **只读**（不删除/不标已读/不移动/不改 flag）；出网点仅 `imap.qq.com:993` 与 `tenpay.wechatpay.cn:443`（均 TLS）；**不上传任何用户数据**、不接统计/上报 SDK、不做后台联网；临时文件只落 `cacheDir` 且 `finally` 删除。

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
