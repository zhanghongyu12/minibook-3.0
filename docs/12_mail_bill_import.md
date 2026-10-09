# MiniBook Android 重构版 — 邮件账单自动导入设计（F-13）

> 状态：设计已确认（用户 2026-09-23 确认技术路线，要求先落文档再实现）；**实现已完成（2026-09-23，本地构建 + 全量单测绿，真机验收待设备），契约已固化至 `04_api.md §14`、UI 至 `05_ui.md §10`**
> 维护者：Architect / 协调者
> 日期：2026-09-23
> 关联：`01_prd.md §F-13`、`07_decisions.md DEC-022/DEC-023`、`06_tasks.md TASK-045~050`
> 说明：本文所有「实测」结论均来自 2026-09-23 的一次真机/真邮箱验证（spike，见 §9 证据），**不是推测**；设计稿与实际实现的差异已回写至各任务「实施记录」，本文设计正文保留原貌作对照。

---

## 1. 背景与目标

### 1.1 用户诉求

> 「我现在想增加一个功能，自动取邮箱内取邮件 然后下载下来导入」

现状（F-12）是**手动导出 + 手动选文件 + 手动解压**：用户在微信/支付宝里申请账单 → 平台把账单发到邮箱 → 用户下载 → 输入解压密码解压 → 在 App 里选文件导入。链路长、每步都可能卡住（尤其**手机上下载/解压加密 zip 很麻烦**）。

### 1.2 目标

把「邮箱里已经躺着账单邮件」这一段端到端自动化，做一个 App 内**「一键收取」**：

```
点一下 → 连邮箱 → 找到账单邮件 → 取到加密 zip → 自动解出账单文件 → 走既有导入链路入库
```

**最终形态：全程无需用户输入任何密码。**（依据见 §3.4：账单 zip 口令是 6 位纯数字，可本地秒级穷举。）

### 1.3 明确不做（本期）

| 不做 | 原因 |
|---|---|
| 后台定时自动收取（WorkManager） | 用户 2026-09-23 已选「手动一键收取」；也避免后台联网的隐私与耗电争议 |
| 邮箱 OAuth2 | QQ 个人邮箱不开放第三方 OAuth2 的 IMAP |
| 除 QQ 邮箱外的邮箱 | 本期只支持 QQ（用户指定）；架构上留 `MailProvider` 抽象，后续可扩 |
| 邮件内容阅读 / 全文同步 | 只取「账单类」邮件的附件或正文链接，其余邮件一律不落盘 |
| 数据上云 | 本功能只做「从邮箱把账单文件拉到本地解析」，**不上传任何数据** |

---

## 2. 实测证据（技术路线的事实基础）

### 2.1 邮箱侧

| 验证项 | 实测结果 |
|---|---|
| 服务器 | `imap.qq.com:993`，SSL |
| 认证 | 邮箱地址 + **16 位授权码**（网页版「设置→账号→IMAP/SMTP服务」生成）；`LOGIN OK` |
| IMAP `ID` 命令 | JavaMail 需显式声明客户端身份（`mail.imap.id` 属性 + `IMAPStore.id(map)`），实测发送成功 |
| 收件箱规模 | 136 封 |
| **服务端 SEARCH** | **不可靠**：`OR(FROM "alipay")(FROM "tencent")(SUBJECT "账单")…` 返回 **0 封**，而邮件明明存在 |
| **本地过滤** | 同一批邮件按「发件人/主题关键词」本地筛选 → 命中 **10 封**，其中 **3 封是账单邮件** |

> **结论：正式实现禁止依赖服务端 SEARCH，必须「拉取近期邮件 → 本地过滤」。** 这是本次验证最重要的架构结论之一。

> **补充（2026-10-09）**：同类账单的覆盖区间相互重叠（如支付宝自定义区间流水），正式实现中 FILTER 后经 `MailBillSelector` **按平台各只保留最新一封**（`sentDateMillis` 最大；同毫秒按主题字典序兜底），其余旧邮件跳过并在进度中显式披露（`⏭ {平台} · {主题} — 已有更新的同类账单，跳过`，FILTER 摘要追加「→ 待处理 K 封」）；重复入库另由 F-12 `externalId` 幂等兜底，本步不删除任何已导入数据。

### 2.2 支付宝账单邮件（有附件）

```
发件人 : service@mail.alipay.com
主题   : <收件人姓名>的支付宝交易流水明细
结构   : multipart/MIXED
         ├─ TEXT/HTML; charset=GBK
         └─ APPLICATION/OCTET-STREAM; filename=支付宝交易明细(20260619-20260919).zip
                flags=0x0809  method=Deflate  ZipCrypto
```

- 附件文件名是 **RFC2047 / RFC2231 编码**的：`name*0="=?GB18030?Q?…?="` + `name*1="…?="`，JavaMail 的 `getFileName()` 返回**未解码的原始串**，必须自行解码（`MimeUtility.decodeText` + `name*N` 续行拼接）。
- 附件就是加密 zip，**未压缩前 27801 B / 压缩后 8341 B**。
- 正文自述：「出于安全考虑，附件已加密，**解压密码已通过支付宝服务消息发送至申请人支付宝**」→ **密码不在邮件里**。

### 2.3 微信账单邮件（**无附件，只有链接**）

```
发件人 : wechatpay@tencent.com
主题   : 微信支付-账单流水文件(20260619-20260919)
结构   : multipart/ALTERNATIVE（TEXT/PLAIN + TEXT/HTML）—— 没有任何附件
正文   : 「…文件已生成，请下载查阅…基于安全考虑，文件已加密。解压密码已通过
          【微信支付】公众号发送至申请人微信。 点击下载 7天内有效」
```

- 正文内下载链接形态：`https://tenpay.wechatpay.cn/userroll/userbilldownload/downloadfilefromemail?<1 个参数，约 298 字符>`
- **实测直接 HTTPS GET 成功**：`HTTP 200`、`Content-Type: application/zip`、`Content-Length: 26318`，无需登录、无需 Cookie —— **签名 token 即凭证**。
- 下载响应的 `Content-Disposition` 文件名（URL 编码）即：`微信支付账单流水文件(20260619-20260919)——【解压密码可在微信支付公众号查看】.zip`
- **链接有效期 7 天**，过期需回微信重新申请。
- 正文自述密码来自【微信支付】公众号 → **同样不在邮件里**。

### 2.4 加密口令（决定性发现）

**为什么必须自动破解**：两个平台的解压密码都**不随邮件发送**，只在各 App/公众号内展示。按常规做法必须让用户手动输入。

但实测表明**根本不需要**——口令是 **6 位纯数字**，且**每次不同**：

| zip | 口令 | 穷举耗时（PC / 8 线程） |
|---|---|---|
| 支付宝 `(20260619-20260919)` | 已命中（口令不落文档） | 1.0 s |
| 支付宝 `(20260501-20260824)` | 已命中（口令不落文档） | 1.0 s |
| 微信 `(20260619-20260919)` | 已命中（口令不落文档） | 1.6 s |

- 三者均为 6 位纯数字；**两个支付宝包口令不同**，证明「每次不同」。
- 因此「让用户输一次并记住」的方案**无效**（存下来的口令下次就用不上），而「每次自动穷举」**可行且极快**。
- 穷举空间 `10^6 = 1,000,000`，PC 上约 1 秒；手机端预估数秒（见 §6 性能设计）。

### 2.5 解出的账单内容（确认可被现有 F-12 链路消费）

| 平台 | 解出文件 | 大小 | 格式 |
|---|---|---|---|
| 支付宝 | `支付宝交易明细(20260619-20260919).csv` | 27801 B | **GBK 编码 CSV**（头部：`导出信息：` / `姓名：` / `支付宝账户：` / `起始时间：[…`） |
| 支付宝 | `支付宝交易明细(20260501-20260824).csv` | 45901 B | 同上 |
| 微信 | `微信支付账单流水文件(20260619-20260919)——【解压密码可在微信支付公众号查看】.xlsx` | 26932 B | **XLSX**（标准 zip，首条目 `[Content_Types].xml`） |

→ **解出的文件与 F-12 手动导入时用户选的文件完全同构**，可直接复用既有解析链路（`BillParserFactory` → `AlipayCsvParser` / `WechatXlsxParser`）。

### 2.6 「已知明文攻击」为什么不可用（排除记录）

网上常见做法是用 Biham–Kocher 已知明文攻击（`bkcrack`）绕过口令。**本场景不适用**，实测确认：

- 已知明文攻击要求提供的是 **「压缩后的字节流」**，不是文件原始内容；
- 两个条目都是 **Deflate 压缩**（支付宝 27801→8341、微信 26932→25982），首个 deflate 块为动态 Huffman，其字节取决于整块数据统计 → **压缩流不可预测**；
- 且本次也不具备「同文件 密文+明文」配对（设备未连接）。

> 结论：**放弃已知明文攻击，走 6 位数字穷举**。此记录留档，避免后续重复尝试。

---

## 3. 技术方案

### 3.1 端到端流程

```
┌─ 设置页「邮箱账单」───────────────────────────────┐
│  填 QQ 邮箱地址 + 授权码 → Keystore 加密落盘        │
└──────────────────────────────────────────────┘
                    ↓
┌─ 导入页「从邮箱一键收取」─────────────────────────┐
│ 1. IMAP 连接 imap.qq.com:993（SSL + ID 命令）      │
│ 2. 拉取最近 N 天邮件（默认 30 天）→ 本地过滤        │
│    支付宝：FROM service@mail.alipay.com            │
│            主题含「支付宝交易流水明细」              │
│    微信  ：FROM wechatpay@tencent.com              │
│            主题含「微信支付-账单流水文件」           │
│ 3. 取件                                            │
│    支付宝：下载 zip 附件（RFC2047 文件名解码）       │
│    微信  ：正文提取下载链接 → HTTPS GET 下载 zip     │
│ 4. 解密：穷举 6 位数字口令 → ZipCrypto 解密 → 解压   │
│ 5. 交给既有 ImportBillUseCase（externalId 去重 +    │
│    自动分类 + 单事务整批写入）                       │
│ 6. 汇总反馈「成功 N / 跳过 M（分原因）/ 失败 K」     │
└──────────────────────────────────────────────┘
```

### 3.2 分层与新增组件

| 层 | 组件 | 职责 |
|---|---|---|
| 数据层（Android 边界） | `MailAccountStore` | 邮箱地址 + 授权码读写；授权码经 Android Keystore（AES-GCM）加密，复用现有 `KeyManager` 模式 |
| 数据层 | `ImapMailClient` | JavaMail 封装：连接、ID 命令、**只读**打开 INBOX、按日期窗口拉取、取附件字节、取正文文本 |
| 数据层 | `MailBillMatcher` | 本地过滤规则（发件人白名单 + 主题关键词），**不依赖服务端 SEARCH** |
| 数据层 | `MailFilenameDecoder` | RFC2047 / RFC2231（`name*0`/`name*1`）文件名解码 |
| 数据层 | `WechatBillLinkExtractor` | 从纯文本/HTML 正文提取 `tenpay.wechatpay.cn` 下载链接 |
| 数据层 | `MailBillDownloader` | 微信链接 HTTPS GET 下载（跟随重定向、限流超时） |
| 业务层（纯 Kotlin，可 JVM 单测） | `ZipPasswordCracker` | 6 位数字口令穷举（ZipCrypto 校验字节快筛） |
| 业务层（纯 Kotlin） | `ZipCryptoDecryptor` | ZipCrypto 流解密 + 原生 `Inflater` 解压 |
| 业务层 | `FetchBillFromMailUseCase` | 编排「连邮箱 → 过滤 → 取件 → 落临时文件」，产出 `List<FetchedBill>` |
| 业务层 | `MailBillImportUseCase` | 编排「取件 → 破解 → 解密 → 解压 → 交给 `ImportBillUseCase`」并聚合结果 |
| UI 层 | `MailSettingsScreen` + `MailSettingsViewModel` | 邮箱配置（地址/授权码/连通性测试/清除凭据） |
| UI 层 | 导入页扩展 | 「从邮箱一键收取」入口 + 进度 + 结果（复用 F-12 结果页态） |

### 3.3 关键契约（草案，实现时以代码为准）

```kotlin
// 凭据存储
interface MailAccountStore {
    val email: Flow<String?>
    val isConfigured: Flow<Boolean>
    suspend fun save(email: String, authCode: String)
    suspend fun getAuthCode(): String?      // 解密读出；不落明文、不写日志
    suspend fun clear()
}

// 邮件取件结果
sealed interface MailBillPayload {
    data class Attachment(val fileName: String, val bytes: ByteArray) : MailBillPayload
    data class DownloadLink(val url: String, val suggestedName: String?) : MailBillPayload
}

data class MailBillCandidate(
    val platform: BillPlatform,   // ALIPAY / WECHAT
    val subject: String,
    val sentDateEpochMillis: Long,
    val payload: MailBillPayload,
)

// 口令穷举（纯 Kotlin，JVM 可测）
interface ZipPasswordCracker {
    /** @return 命中口令；未命中返回 null（调用方降级为手动输入） */
    fun crack(zipFile: File, digits: IntRange = 6..6): String?
}

// 解密解压
interface EncryptedZipExtractor {
    fun extract(zipFile: File, password: String, destDir: File): File
}
```

### 3.4 口令穷举设计（本方案的核心，也是唯一"非标准"之处）

**原理**：ZipCrypto（PKWARE 传统加密，`ZipCrypto`）在条目数据前写了 12 字节加密头，其**最后一个字节**是校验字节——通常为 CRC32 高字节，若使用了 data descriptor（flag bit 3）则可能为 DOS 时间高字节。据此可**先只解 12 字节头做快筛**，命中后再整包解密验 CRC32。

**两段式判定**：

1. **快筛（每个候选都做，极廉价）**：由候选口令派生密钥 → 解密 12 字节头 → 取第 12 字节比对。
   - **必须宽松**：接受「CRC32 高字节 **或** DOS 时间高字节」任一命中（两种约定都真实存在，实测两个支付宝 zip 的 `flags=0x0809` 即含 data descriptor 位）。
   - 通过率约 `2/256`。
2. **权威判定（仅对快筛命中者执行）**：整包解密 → `Inflater` 解压 → 对**解压后的内容**算 CRC32，与 zip 中记录的 CRC 比较。
   - ⚠️ **口径红线**：zip 里存的是**原始内容**的 CRC，**不是压缩数据**的 CRC。拿压缩数据算 CRC 去比对会把正确答案全部误杀（本次 spike 实际踩过这个坑，详见 §9.4）。

**性能优化**：
- 预计算前 3 位的密钥状态（`1000` 个状态），后 3 位循环 → 每候选只需 3 次密钥更新 + 12 次解密。
- 快筛命中率低，权威判定只跑约 `10^6 × 2/256 ≈ 7,800` 次，可再优化为「只解压前 256 字节做合法性判定」。

**降级策略**（不可假定永远 6 位数字）：
- **实现现状（如实）**：穷举失败 → 记录 `MailItemFailure.PasswordNotFound`，结果页按邮件提示「口令穷举失败」（`ZipPasswordCracker.crack` 返回 null，上层聚合为失败项）。**手动输入口令的 UI 未实现**（当前降级为结果页提示，待后续增强；复用同一 `EncryptedZipExtractor.extract` 路径的组件已具备，仅缺输入 UI）。
- 若将来平台改用 **AES 加密**（`method=99` / 带 `0x9901` 扩展字段）→ ZipCrypto 路径直接失效；此时必须走「手动输入口令 + 引入 AES 解密库」或明确告知不支持。**实现已按此探测加密类型**（`ZipArchiveInspector`：method 99 或 extra `0x9901` → `Unsupported`，不进入穷举）。

---

## 4. 安全与隐私

### 4.1 凭据保护

| 项 | 措施 |
|---|---|
| 授权码落盘 | Android Keystore（AES-256/GCM）加密后存私有目录；**与 SQLCipher master key 同级对待** |
| 日志 | 授权码、邮箱口令、账单内容**一律不得进日志**（含异常堆栈） |
| 界面 | 授权码输入框掩码显示；仅在用户主动点「显示」时明文 |
| 清除 | 设置页提供「清除邮箱凭据」，一键抹除密文与内存缓存 |

### 4.2 邮件处理边界（用户 2026-09-23 明确选择「只读」）

- IMAP **只读打开**（`Folder.READ_ONLY`）：**不删除、不标已读、不移动、不修改任何 flag**。
- 只处理命中白名单的账单邮件；**其余邮件不下载正文、不落盘**。
- 附件/下载产物只落 `cacheDir` 临时文件，导入结束（含失败路径）**`finally` 删除**。

### 4.3 网络行为

- 出网点**仅两个**：`imap.qq.com:993`（TLS）与 `tenpay.wechatpay.cn:443`（TLS）。禁止明文 HTTP（微信实测链接即 HTTPS）。
- 不接入任何统计/崩溃上报/广告 SDK。
- **不上传任何用户数据**；这是「本地优先」定位下的**单向拉取**。

### 4.4 需同步修订的既有边界（→ DEC-023）

- `01_prd.md §7 非功能需求` 现写「**所有功能离线可用，无网络依赖（本期无云端）**」——本功能是**首次引入网络与外部凭据**，该条款需**显式例外化**（限定为「仅邮箱账单拉取，单向、不上传」），否则文档自相矛盾。
- Android 清单新增 `INTERNET` 权限（当前清单**零权限**）。
- 隐私说明/合规文案需补充「网络访问 + 邮箱凭据」条目。

---

## 5. 依赖与构建变更

| 变更 | 内容 | 理由 |
|---|---|---|
| 新增依赖 | `com.sun.mail:android-mail:1.6.7` + `com.sun.mail:android-activation:1.6.7`（约 1 MB） | Android 上唯一成熟的 IMAP 客户端；用户已同意 |
| ProGuard/R8 | 保留 `javax.mail.**` / `com.sun.mail.**` / `javax.activation.**` keep 规则 | 反射与 `META-INF` 服务发现在 R8 下会被裁剪 |
| 权限 | `android.permission.INTERNET` | IMAP + HTTPS 下载 |
| **不新增** | ~~`net.lingala.zip4j`~~ | 实测加密为 **ZipCrypto（非 AES）**，Java 原生 `Inflater` + 自实现 ZipCrypto 解密即可，**零第三方解压依赖**（延续 DEC-016「零第三方解析库」口径） |

---

## 6. 性能设计

| 环节 | 目标 | 手段 |
|---|---|---|
| IMAP 连接 + 拉取 | ≤ 5 s（30 天窗口） | 只取 `ENVELOPE`/必要头（`FetchProfile`）做过滤，命中后才取正文与附件 |
| 口令穷举 | ≤ 10 s（中端机） | 两段式判定 + 前 3 位密钥状态预计算 + 多核并行（`Dispatchers.Default`） |
| 解密解压 | ≤ 1 s | 流式解密 + `Inflater`，不整包驻留多份 |
| 整体一键收取 | ≤ 20 s（2 个平台各 1 封） | 全程 `Dispatchers.IO`；UI 进度可见、可取消 |

**可取消性**：穷举与下载必须响应取消（协程 `ensureActive`），避免用户退页后仍在空跑。

---

## 7. 风险与未决项

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| R1 | 平台改用**非 6 位数字**口令 | 穷举失败 | 当前降级为结果页提示（`PasswordNotFound`，**手动输入口令 UI 未实现，待后续**）；穷举范围 `digits`/`charset` 已参数化可配置 |
| R2 | 平台改用 **AES** 加密 zip | ZipCrypto 路径失效 | 实现时先探测加密类型；非 ZipCrypto 明确报错，不盲目穷举 |
| R3 | 微信下载链接 **7 天过期** | 老邮件取不到件 | 过滤时跳过超期邮件并在结果中提示「链接已过期，请在微信重新申请」 |
| R4 | QQ 服务端行为变化（SEARCH/ID 命令） | 拉取失败 | 已按「不依赖 SEARCH」设计；ID 命令双保险（属性 + 显式调用） |
| R5 | JavaMail 1.6.7 版本较老、R8 兼容性 | 构建/运行异常 | 预置 keep 规则；`assembleRelease` 需实测验证 |
| R6 | 邮箱/链接在部分网络下不可达 | 功能整体不可用 | 明确错误提示（超时/认证失败/网络不可达分开报），不阻塞手动导入路径 |
| R7 | 邮件正文为 HTML 且链接被包裹/转义 | 链接提取失败 | 正则覆盖 `&amp;` 反转义 + `href` 与裸链双路提取；提取失败计入「取件失败」明细 |
| R8 | 穷举耗电/发热 | 用户体验 | 仅用户点击时执行、可取消、硬超时；不做后台穷举 |

**未决项（实现阶段需定）**：
- 拉取时间窗口默认值（暂定 30 天）与最大邮件数上限。
- 多封账单邮件的处理顺序与去重（同一账期重复申请会生成多封邮件；`externalId` 已能兜底入库去重，但下载会重复 → 需按主题+账期去重）。
- 微信链接是否需要特定 `User-Agent`（实测用 Chrome UA 成功，需确认是否必需）。

---

## 8. 与既有 F-12 的关系

**本功能是 F-12 的「取件自动化」前置段，不替换 F-12 任何逻辑**：

```
F-13：邮箱 → 取到 zip → 解密解压 → 得到 .csv/.xlsx 文件
                                          ↓（交给现有链路，零改动）
F-12：BillParserFactory.resolve → AlipayCsvParser / WechatXlsxParser
      → ImportBillUseCase（自动分类 + externalId 去重 + 单事务整批写入）
```

- 复用点：`ImportBillUseCase`、`BillParserFactory`、`BillCategoryMapping`、`TransactionRunner` 事务口径、F-12 结果页态。
- **不修改** F-12 的解析规则、字段映射、过滤规则、幂等口径。
- 唯一需要的衔接改动：给 `ImportBillUseCase` 增加一个**「以文件路径（而非 SAF uri）为入口」的重载**，因为邮件取件产物是 App 私有目录下的 `File`，不是 `content://` Uri。（替代方案：把文件包成 `FileProvider` 的 `content://` Uri 走原入口——**不采用**，成本更高且引入不必要的 provider 暴露面。）

---

## 9. 验证证据（spike 复现记录）

### 9.1 验证环境

- 本机 JDK 21 + JavaMail 1.6.2（`javax.mail`）做协议验证；真实 Android 端将用 `android-mail:1.6.7`。
- 真实 QQ 邮箱（授权码认证）+ 三封真实账单邮件 + 两个真实加密 zip。
- 验证工程位于工作区 `eg/spike/`（临时目录，已加入 `.gitignore`，不入版本库）。

### 9.2 验证项目与结论

| # | 验证项 | 结论 |
|---|---|---|
| 1 | QQ IMAP 登录（含 ID 命令） | ✅ 通过 |
| 2 | 服务端 SEARCH 可用性 | ❌ **不可用** → 改本地过滤 ✅ |
| 3 | 支付宝邮件附件取件 | ✅ 通过（含 RFC2047 文件名解码需求确认） |
| 4 | 微信邮件正文链接提取 | ✅ 通过（1 个签名参数，约 298 字符） |
| 5 | 微信链接实际下载 | ✅ `HTTP 200` / `application/zip` / 26318 B |
| 6 | 加密类型识别 | ✅ 两者均 `ZipCrypto + Deflate`（非 AES） |
| 7 | 6 位数字口令穷举 | ✅ 三个 zip 全部命中（1.0 / 1.0 / 1.6 s） |
| 8 | 解出内容可被 F-12 消费 | ✅ GBK CSV 与标准 XLSX |
| 9 | 已知明文攻击可行性 | ❌ 排除（Deflate 压缩流不可预测，见 §2.6） |

### 9.3 工具链正确性交叉验证（方法学要求）

穷举工具是**自研**的，其「否定结论」不可信，除非先用**已知答案**验证过。本次采用：

1. 用 Python 独立实现 ZipCrypto **加密**，造一个**已知口令**的 zip；
2. 用 Python 标准库 `zipfile`（**独立第三方实现**）验证「正确口令能解、错误口令被拒」→ 证明加密实现符合规范；
3. 再用自研穷举工具去破这个 zip → 必须命中已知口令。

### 9.4 本次踩到的两个真实坑（留档，避免重犯）

1. **校验字节约定不唯一**：最初要求解密头第 11、12 字节**同时**匹配「CRC 高字节 + 时间高字节」，而实测 zip 带 data descriptor（`flags=0x0809/0x080B`），该约定会**误杀正确口令**。→ 改为「任一命中即通过 + 整体 CRC 权威判定」。
2. **CRC 比对口径错误**：最初拿**解密后的压缩数据**算 CRC32 去比对 zip 记录的 CRC（那是**原始内容**的 CRC），导致**正确答案被全部误杀**，一度得出「口令不是 6 位数字」的**错误结论**。
   - 是 §9.3 的交叉验证暴露了该 bug（`123456` 的自造 zip 竟然破不出来）。
   - **教训：工具未通过已知答案验证前，不得采信其否定结论。**

---

## 10. 任务拆分指引

详见 `06_tasks.md` TASK-045~050：

| 任务 | 内容 |
|---|---|
| TASK-045 | 邮箱凭据存储（Keystore）+ 设置页「邮箱账单」UI + `mail_account` 路由 |
| TASK-046 | IMAP 客户端（JavaMail 依赖 + INTERNET 权限 + ID 命令 + 只读拉取）+ 本地过滤 |
| TASK-047 | 取件：支付宝附件下载（RFC2047 文件名解码）+ 微信链接提取与 HTTPS 下载 |
| TASK-048 | ZipCrypto 口令穷举 + 解密解压（纯 Kotlin，JVM 单测，**含已知答案自校验**） |
| TASK-049 | `MailBillImportUseCase` 编排 + `ImportBillUseCase` 文件入口重载 |
| TASK-050 | UI 接线（一键收取入口 / 进度 / 结果）+ 真机端到端验证 |

---

## 11. 文档回写清单

- [x] `01_prd.md §F-13`（需求）
- [x] `07_decisions.md` DEC-022（技术路线）、DEC-023（隐私/权限边界修订）
- [x] `06_tasks.md` TASK-045~050
- [x] `CHANGELOG.md`
- [x] `02_architecture.md §17`（F-13 架构段落，实现时随代码差分补齐）
- [x] `04_api.md §14`（契约细节，实现时随代码固化——已于 2026-09-23 随实现回写，含 §14.9 已知限制与待真机验证）
- [x] `05_ui.md §10`（设置页邮箱区块 + 导入页收取区块——已于 2026-09-23 随实现回写，含 P-16 页面与完整文案表）
