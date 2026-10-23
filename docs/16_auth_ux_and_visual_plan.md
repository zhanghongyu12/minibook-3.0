# MiniBook Android 重构版 - 登录/注册 UX 与视觉现代化 方案

> 状态：**待用户确认**（调研完成，实施未开始）
> 维护者：主代理（调研 + 方案）
> 最后更新：2026-10-12
> 说明：本文回答两件事——①「正经 App 的登录/注册页怎么设计」②「本 App 样式为什么显老、怎么变时尚」。所有结论附来源；来源抓不到的一律标「未验证」，不编造。实施编号见文末 §7（拟 TASK-075 起）。

---

## §0 调研方法与取证限制（先看这段，决定下文每条结论的可信度）

| 渠道 | 结果 |
|---|---|
| `web_search` | **本环境不可用**（HTTP 401 无效 key）——所有检索均改为直接抓取，无搜索结果兜底 |
| `m3.material.io` | **SPA**，裸 HTML 61647 字节、去标签后正文为空、无 `__NEXT_DATA__` ⇒ 正文读不到（Material 3 层结论改用 androidx Compose 源码 + Material 官方 TextField 文档，规范级表述退回 Material 2 归档页） |
| `developer.android.com` | 网络阻断（`fetch failed`）⇒ 改用官方镜像 **`developer.android.google.cn`**（同源文档，已验证可读） |
| Apple HIG | 现行 HIG（2024 改版后）为 JS-only；`/design/human-interface-guidelines/authentication` 返回 **404** ⇒ 只用 **2022 年前归档页**（web.archive.org），标注为「归档」 |
| 国内 App 登录页 | 淘宝（`appNameError` 非法请求）、抖音（SPA）、招商一网通（0 字符）**未取得原文**；微信客户端内首屏无官方可抓页面 |
| 应用商店审核规范 | 华为/小米/OPPO/vivo/应用宝均为 SPA 或需登录 ⇒ **未取得原文**，故不引用其条款 |

---

## §1 平台规范对照（「正经 App」的硬线）

### §1.1 必须做（硬规范）— 逐条对照我方现状

| # | 规范要求 | 出处 | 我方现状 | 动作 |
|---|---|---|---|---|
| 1 | 口令最小长度：NIST 单因子 SHALL ≥15；仅作 MFA 一环 SHALL ≥8；SHOULD 允许 ≥64。ASVS 5.0 §6.2.1：L1 ≥8、强烈建议 15 | NIST SP 800-63B-4、ASVS 5.0 V6 | 服务端 `MIN_PASSWORD_LENGTH = 8`（`server/app/schemas.py:16`） | 见 §4.1（决策项 A） |
| 2 | 禁止口令字符组合规则（必须含大小写/数字/符号 = 反模式） | NIST「SHALL NOT be imposed」、OWASP、ASVS 6.2.5 | 未强制组合 ✅ | 保持，并在文案里写明 |
| 3 | 口令黑名单：注册/改密须比对至少 top 3000 弱口令 | ASVS 6.2.4、NIST | **无黑名单** ❌ | 加（服务端，见 §4.1） |
| 4 | 必须允许粘贴 / 自动填充 / 密码管理器 | NIST「SHALL allow」、OWASP、WCAG 2.2 SC 3.3.8 | 未禁粘贴 ✅，但**无 `autofillHints`/`ContentType`** ❌ ⇒ 密码管理器识别不到 | 加（§4.2） |
| 5 | 必须提供「显示口令」选项（SHOULD） | NIST | 有，但做成按钮而非框内 icon ❌（§4.2） | 改为 `trailingIcon` |
| 6 | 口令字段默认掩码 | 归档 HIG、Material `password_toggle` | 默认掩码 ✅ | 保持 |
| 7 | 认证必须限流 | NIST「SHALL implement rate-limiting」 | 有（`server/app/ratelimit.py:27`「操作过于频繁，请稍后再试」），但**仅进程内** ⚠️ | 记为已知缺口 |
| 8 | 不得泄露账号是否存在（登录/找回/重置返回同一文案与状态码） | OWASP Authentication Responses | **已合规** ✅：`server/app/routers/auth.py:184-185` 把「用户不存在 / 已禁用 / 口令错」合并为一条 `ApiError("invalid_credentials", "用户名或口令不正确")` → 401 | 保持；登录页**不得**加「用户名是否存在」实时校验 |
| 9 | 触控目标 ≥48×48dp | Android a11y 官方、`minimumInteractiveComponentSize` | 基本满足（`AuthPrimaryButton` 52dp；`IconButton` 默认 48dp）✅ | 改造时守住 |
| 10 | 对比度：正文 4.5:1、大字 3:1（不可四舍五入） | WCAG 1.4.3 AA | M3 默认 token ✅；但**自定义主题色**用 `lerp` 生成（`ui/theme/Theme.kt:132-145`，`onPrimary = Color.White` 恒定）⇒ 浅色 seed 会白字压浅底 ❌ | 按亮度选 `onPrimary`（§4.4） |
| 11 | 错误必须是文本，不能只靠颜色 | WCAG 3.3.1 / 1.4.1 | 是文本 ✅（`AuthErrorMessage`） | 保持 |
| 12 | 错误须能被读屏播报 | Material `errorAccessibilityLiveRegion`；Compose `SemanticsProperties.Error` / `LiveRegion` | **未设** ❌ | 加语义（§4.2） |
| 13 | 登录/注册字段必须声明用途（`autofillHints` / `ContentType`） | Android autofill-optimize 官方 | **全仓库 0 命中** ❌ | 加（§4.2） |
| 14 | 不得静默截断口令 | OWASP | 未截断 ✅ | 保持 |

### §1.2 强烈建议（主流做法）

- **字段结构**用 label + supporting text，不靠 placeholder 单独承担（Material 解剖：label 两态 / leading / trailing / supporting / active indicator）。我方已是 label + supporting ✅。
- **错误位置**：字段内标记 + 字段下方文案；多处错误在表单顶部汇总。我方目前只有「按钮下方一条」⚠️ ⇒ 改字段级 `isError` + 表单级汇总。
- **校验时机**：错误出现在「用户交互过程中或之后」；无法判断进度时在**提交后**报。我方提交后报 ✅，符合。
- **按钮与输入框尺寸**：M3 按钮容器高 40dp（`ButtonSmallTokens.ContainerHeight = 40.0.dp`，`ButtonDefaults.MinWidth = 58.dp`，精度指针下 `MinHeight = 36.dp`）；`TextFieldDefaults.MinHeight = 56.dp`、`MinWidth = 280.dp`。我方按钮 52dp（高于规范，属风格选择，保留或收至 48/44，见 §6）。
- **口令可见性切换**用 trailing icon（Material `endIconMode="password_toggle"`，须给 `endIconContentDescription`）。
- **键盘与 IME**：口令字段 `KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)`（Compose `SecureTextField` 默认即如此）；表单用 `imeAction` 串联（`ImeAction.Next` = 用户想移到下一项）。
- **错误反馈不要只靠会消失的浮层**：Snackbar 单行、自动超时、0–1 个动作、不得承载核心用例唯一通路；Android Toast 官方口径「前台优先用 snackbar」⇒ **表单错误必须内联**，Snackbar 只做辅助。我方 `AccountViewModel` 里 `toast(...)` 承担了部分表单错误 ⚠️。
- **无障碍描述**：图标按钮给 `contentDescription` 表达用途（写 "Submit" 而非 "Submit button"），Text 本身不必再设。
- **认证方式收敛**（归档 HIG Accounts）：「presenting a single way to authenticate」「Initiate authentication only in response to user action」「Always identify the authentication method you offer」。我方单一用户名+口令路径 ✅，且不在页面加载时自动发起任何认证 ✅。
- **Onboarding**（归档 HIG）：不拿设置/注册墙挡路、给跳过路径、回访不自动再弹 ⇒ 我方**本地优先、登录只用于同步** 正是该原则的最佳实践 ✅（与国内合规 §2 相互印证）。
- **减少重复输入**（WCAG 2.2 SC 3.3.7 Redundant Entry）：同一流程内已输信息不得要求再输一次，除非「安全必需」⇒ 是**去掉「确认口令」框**的规范依据（§4.3 决策项 B）。
- **加载态**：Compose Material3 有 `LoadingIndicator`（2024 起）；提交中禁用按钮防重复提交。

### §1.3 明确的反模式 — 我方命中情况

| 反模式 | 我方是否命中 | 位置 |
|---|---|---|
| 只用会消失的 toast/snackbar 报表单错误 | ⚠️ 部分命中（资料页/账号操作走 `toast`） | `ui/settings/AccountViewModel.kt` |
| 用户没输完就报错 | ✅ 未命中（提交后才报） | — |
| 用红色单独表达错误 | ✅ 未命中（有文本） | — |
| 强制口令字符组合 | ✅ 未命中，且**不要**新增 | — |
| 禁止粘贴/自动填充/隐藏密码管理器 | ❌ **命中「未声明用途」**（不是主动禁止，但等价于密码管理器填不了） | `ui/settings/AuthComponents.kt` 全字段 |
| 静默截断口令 | ✅ 未命中 | — |
| 登录错误写成「该用户名不存在/密码错误/账号已锁定」 | ✅ 未命中（服务端已合并文案） | `server/app/routers/auth.py:184-185` |
| 硬性要求「确认口令」 | ❌ 命中（我方第二轮刚加的） | `ui/settings/RegisterScreen.kt:54-60` |
| 默认明文显示口令 | ✅ 未命中 | — |
| App 内给「开启生物识别」opt-in 设置 | 需核对设置页（本轮未深查） | `ui/settings/SettingsSecurityScreen.kt` |
| Snackbar 堆叠/多动作/持久 | ✅ 未命中 | — |
| 首次启动自动弹键盘 | ✅ 未命中（且改造后也不应加自动聚焦） | — |

### §1.4 规范冲突与不确定处（诚实标注）

- 「显示口令」三边态度不同：NIST 是 SHOULD 提供；归档 HIG 只要求 secure text field；Material 明确写进规范 ⇒ **默认掩码 + 可切换**是三边都不违背的解。
- 口令长度三套数字：NIST 单因子 ≥15 / ASVS L1 ≥8 且建议 15 / OWASP 无 MFA <15 算弱。实务折中 = **≥8~12 + 弱口令黑名单**。
- 触控尺寸三套：Android 48dp / WCAG 2.5.8 AA 24px / WCAG 2.5.5 AAA 44px ⇒ Android 端以 **48dp** 为准。
- 「确认口令」没有被任何权威规范**明文禁止**；依据是 WCAG 3.3.7 的可推导结论 + 社区共识（本环境无搜索，**无首手共识出处**，已标注）。
- 「首次启动不自动弹键盘」无权威条文，属社区共识（未验证）。

---

## §2 国内形态与合规（中国市场专属结论）

### §2.1 主流形态（有原文支撑的部分）

| App | 登录方式 | 保留密码登录 | 注册形态 | 出处 |
|---|---|---|---|---|
| 美团 | 「手机动态码登录」+「账号登录」两个 tab | 是 | 独立入口；手机号已绑账号时弹「确认为本人 / 不是我的，去注册」 | 已读 passport.meituan.com/account/unitivelogin |
| 京东 | 「密码登录」+「短信登录」两个 tab；另有 APP 扫码 | 是 | 无独立注册页，短信登录即注册；客户端有「手机号码一键登录」 | 已读 passport.jd.com/new/login.aspx |
| 支付宝 | 「账密登录 / 扫码登录 / 验证码登录」 | 是 | 验证码登录即注册 | 已读 auth.alipay.com/login/index.htm |
| 12306 | 扫码 + 手机号短信验证码（须填证件号后 4 位，短信限频） | 是 | 手机核验后才可改邮箱/密码 | 已读 kyfw.12306.cn/otn/resources/login.html |
| 微信 | 网页版仅扫码；App 内手机号+验证码 | 网页版无密码框 | 未能验证 | 已读 wx.qq.com |
| 淘宝/抖音/招商一网通 | **未能验证**（反爬 / SPA） | — | — | — |
| 运营商一键登录 | 移动/天翼：一键登录 / 一键注册 / 本机号码校验 | — | 一键登录即注册 | 已读 www.cmpassport.com、id.dlife.cn |

**可据以决策的结论**：① 手机号+验证码是主路径；② **密码登录并未消失**，普遍作为并列 tab 保留；③ 「验证码登录即注册」已成默认。⇒ 我方「用户名 + 口令、注册即登录」的形态**不违背国内观感**，只是缺短信通道（见 §2.3）。一键登录占比数据（天翼自述京东 >80%）属**厂商商业自述**，仅作旁证。

### §2.2 合规硬要求（必须做，不是加分项）

| 要求 | 依据 | 含义 | 我方现状 |
|---|---|---|---|
| 首启以弹窗等明显方式提示阅读隐私政策；隐私政策不得难访问（进主界面后 >4 次点击即认定违规） | 《App违法违规收集使用个人信息行为认定方法》一.1–一.4（国信办秘字〔2019〕191号） | 首启弹窗 + 设置内 1–2 次点击可达 | **全缺** ❌（`docs/` grep `隐私政策|用户协议` **0 命中**；代码只有 `SyncPresenter.PRIVACY_NOTICE` 一句数据边界） |
| 不得默认勾选同意 | 同上 三.4；《个人信息保护法》第十四条 | 勾选框默认不勾，未勾不得提交 | **无勾选框** ❌ |
| 不同意后不得继续收集、不得频繁征求同意 | 同上 三.2 | 拒绝后不反复弹窗 | 不适用（无征求流程）❌需随首启弹窗一并设计 |
| 不得因不同意非必要信息而拒绝基本功能 | 《常见类型移动互联网应用程序必要个人信息范围规定》第四条、第五条（三十八）「实用工具类…无须个人信息即可使用基本功能服务」 | 记录类 App 应可无账号使用 | **已合规** ✅✅（本地优先，登录仅为同步）——这是**合规优势**，须保留并在文案中强化 |
| 必须提供有效注销；不得设不合理条件；**15 个工作日内**完成 | 认定方法 六.1–六.4 | App 内注销入口 + 后台真删 | **已合规** ✅（`注销账号` 立即删云端，快于 15 工作日） |
| 真实身份认证 | 《网络安全法》（2025 修正）**第二十六条**；《移动互联网应用程序信息服务管理规定》第六条；《互联网用户账号信息管理规定》第九条 | 仅「信息发布 / 即时通讯 / 社区」类硬性触发 | **不触发** ✅（纯记账+同步）；**一旦加社区/评论/分享即变硬门槛** |
| 提供撤回同意的途径 | 《个人信息保护法》第十五条；认定方法 三.8 | 设置内可撤回 | 随首启弹窗一并设计 ❌ |
| 处理前告知义务 | 《个人信息保护法》第十七条 | 隐私政策须列明处理者名称与联系方式、目的/方式/种类、保存期限、行权方式 | **全缺** ❌ |
| 注册须签订服务协议并公开账号管理规则 | 《互联网用户账号信息管理规定》第六条；《移动互联网应用程序信息服务管理规定》第十六条 | 注册页协议勾选 + 可点链接 | **全缺** ❌ |
| 上架审核重点 | 应用程序信息服务管理规定 第二十条；工信部信管函〔2020〕164号 三（四）10；APP 备案（工信部信管〔2023〕105号） | 首启流程、隐私政策、权限、注销是审核重点 | 需补齐 |

- **未验证，不编造**：华为/小米/OPPO/vivo/应用宝的审核规范原文本次均未取得（SPA/登录墙）。

### §2.3 没有短信通道的现实结论

- **能做**：首启隐私弹窗 + 隐私政策 1 次点击可达；注册页勾选（默认不勾、未勾不可提交）；游客/本地模式（我方已具备）；App 内注销（已有）；忘记密码/切换账号/退出登录（行业习惯，后两者已有）。
- **做不了**：手机号+验证码、运营商一键登录（需运营商网关与聚合 SDK）；**账号找回**只能靠邮箱或管理员人工（见我方现状：服务端只有「用户名+口令」，无邮件/短信通道）。
- **上架障碍**：隐私政策与注销入口缺失属**明文列举的违规情形**，必须补；未接短信在我们既定「无社区/无分享」范围内**不构成硬门槛**。

### §2.4 页码元素与文案（国内原文可引部分）

- 勾选文案原文（美团）：「我已阅读并同意 《美团用户协议》 《隐私政策》」；未勾提交原文：「请先阅读并勾选用户协议」。
- 注册入口原文（美团）：「还没有账号？ 免费注册」。
- 切换账号原文（wx.qq.com）：「切换帐号」。
- **未抓到原文、不编造**：「登录即代表同意《用户协议》和《隐私政策》」、「验证码已发送至 138****8888」、「60 秒后可重新获取」、不同意协议的二次确认弹窗——需以真机自测截图为准。

---

## §3 我方差异总表（现状 → 目标 → 代价）

> 视觉部分（§6 起）待补。下表仅认证/合规向。

| # | 现状（文件:行） | 目标 | 代价 |
|---|---|---|---|
| A | `ui/settings/AuthComponents.kt` 全字段无 `autofillHints`/`ContentType` | 用户名→`ContentType.Username`、口令→`Password`；注册页→`NewUsername`/`NewPassword` | 小（1 个组件加参数） |
| B | 无 `imeAction`/`KeyboardActions` | 用户名 `Next`→口令 `Next`→（注册）确认/提交 `Done`；口令字段关纠错与首字母大写 | 小 |
| C | 「显示口令」是按钮下方的 `TextButton`（`LoginScreen.kt`/`RegisterScreen.kt`） | 移入字段 `trailingIcon`（图标 + `contentDescription`） | 小 |
| D | 错误只有按钮下方一条，无字段级标记、读屏不播报 | `isError` + 字段 `supportingText` 错误文案 + `SemanticsProperties.Error` / `liveRegion`；提交后顶部汇总 | 中 |
| E | 提交中字段未禁用（`AuthField` 无 `enabled`） | busy 时 `enabled=false`（视觉与无障碍同时禁用） | 小 |
| F | 注册页「确认口令」框 | 去掉，靠「显示口令」切换兜错（依据 WCAG 3.3.7） | 小（决策项 B） |
| G | 服务端无弱口令黑名单 | 注册/改密比对 top 3000（内置小表或哈希集） | 中（服务端 + 测试） |
| H | 口令下限 8（`schemas.py:16`） | 维持 8 或提到 12（NIST 严格口径 15） | 小（决策项 A） |
| I | 无隐私政策/用户协议/首启弹窗 | 新增隐私政策页 + 首启同意弹窗 + 设置内 1 次点击可达 + 撤回同意入口 | **大**（新产品内容 + 页面） |
| J | 注册页无协议勾选 | 勾选框（默认不勾）+《用户协议》《隐私政策》链接 + 未勾提示 | 小 |
| K | 自定义主题色 `onPrimary` 恒为白，浅色 seed 对比度不足 | 按 seed 亮度选 `onPrimary`（深/浅二选一） | 小 |
| L | 表单错误部分走 `toast`（`AccountViewModel`） | 内联错误优先，Snackbar 辅助 | 小 |
| M | 「账号」页底部只有一句数据边界 | 该句保留；另加隐私政策/用户协议入口与协议版本 | 小 |

---

## §4 决策项（需要用户拍板）

| 编号 | 决策 | 我的建议 | 理由 |
|---|---|---|---|
| A | 注册口令下限：保持 8 / 提到 12 / 提到 15 | ~~**保持 8 + 加黑名单**~~ → **用户 2026-10-12 拍板：不加黑名单，维持简单校验（DEC-043）** | 记账云端账号是同步用途的实用工具；15 位会明显劝退，ASVS L1 只要求 8，黑名单才是真正有效的防线（该项**已知偏离**，DEC-043 已记账） |
| B | 注册页「确认口令」框去留 | ~~**去掉**~~ → **用户 2026-10-12 拍板：保留**（「不记错」优先于 WCAG 3.3.7） | WCAG 3.3.7 明确把重复输入列为需避免；配合「显示口令」切换即可防打错，少一屏负担 |
| C | 隐私政策与用户协议由谁写 | **我起草中文初稿，你审阅** → **已按用户 2026-10-12 拍板执行：只起草，运营主体与联系方式暂不声明**（正文里明写「上线前草稿」，`docs/17` §5 列为上架前必补） | 合规要求「处理者名称与联系方式、目的/方式/种类、保存期限、行权方式」；我没有你的公司主体与联系方式，需要你补 |
| D | 首启隐私弹窗的形态 | **首启一次弹窗（同意/暂不使用云端功能）** —— **用户未表态，本轮不实施**（只做 2 跳可达 + 注册时同意，缺口记在 `docs/17` §5.2） | 「本地记账不需要同意也能用」是我们的合规优势，弹窗不能挡路；不同意时仅关闭云同步入口 |
| E | 图标方案（§6.5） | **自带 vector drawable + `MiniBookIcons`** | 仓库明写「禁改依赖」（`ui/settings/LedgerEditorSheet.kt:43`），且零依赖即可 tint；不动 `libs.versions.toml` |
| F | 是否升 material3 1.5.0-beta01 拿 expressive 组件 | **不升**，留在 1.4.0 稳定版 + 自定义 token | expressive API 全在 preview（1.4.0-beta01 已把 `ExperimentalMaterial3ExpressiveApi` 全部移除），升级会引入构建风险与大面积回归，收益可由 §6.4 的 1–7/11 覆盖 |
| G | 首页 Hero 卡改造幅度（§6.4 #12） | **做**：大号结余 + 环比差额 + 收入/支出明细 + 月份切换图标化，不加迷你趋势图 —— **已完成（TASK-081，2026-10-12）** | 趋势图已有报表页；首页加第二张图会撞反例 1（信息密度） |

---

## §5 拟实施编号

- **TASK-075**：登录/注册页原生 UX 补齐（A–F、L：autofill、IME 链、trailing 图标、字段级错误 + 读屏语义、busy 禁用、去确认口令）
- **TASK-076**：隐私合规基础设施（I–J、M：隐私政策页、用户协议页、首启同意、注册勾选、撤回同意入口）
- **TASK-077**：口令策略收紧（G–H：弱口令黑名单 + 口径文案 + 服务端测试）
- **TASK-078**：视觉 token 层现代化（§6.4 的 1/2/3/4/5/6/7/11/14：新配色 + `surfaceContainer*` 角色 + 形状/排版/动效 token + 对比度修正 K）
- **TASK-079**：组件与图标层（§6.4 的 8/12/15 + §6.5：`MiniBookIcons` + 自带矢量、`NavigationBar` pill、Hero 卡、删分割线）
- **TASK-080**：逐页收口（§6.5 的三类逐页动刀 + 全 App 截屏回归 + 暗色模式回归）
- **DEC-041**：视觉现代化口径（留在 material3 1.4.0、自定义 token、图标零依赖、语义色不随动态取色）
- **DEC-042**：认证 UX 与合规口径（含 §4 决策 A–D 的最终选择；已落地部分见 `07_decisions.md`）

**实施状态（2026-10-12 第五轮更新）**：TASK-075 / TASK-078 / TASK-079 / TASK-080 / **TASK-081**（首页 Hero 卡环比）/ **TASK-082**（未登录态收为登录按钮 + 设置页「账号」提第一行 + 内置分类换自带矢量图标集）**已完成**（见 `06_tasks.md` 与 `09_test_report.md` 顶部章节）；**TASK-076 部分完成**（法务文本 + 应用内页面 + 注册勾选 + 守卫单测已完成，**首启弹窗与运营主体待补**）；**TASK-077 已关闭**（用户拍板不加弱口令黑名单，见 DEC-043）。**验收方式**：每个 TASK 落地后跑 `:app:testDebugUnitTest` 并 `assembleDebug`；视觉类改动必须 **模拟器逐页截屏回归**（浅色 + 暗色各一遍），因为纯 token 改动的破坏性只有渲染后才看得见（参照 F-15o：编译绿、单测全绿仍会运行时崩）；真机安装仍**每次先向用户申请**。

---

## §6 视觉现代化（用户已选：清爽现代 · 浅底 + 大圆角卡片 + 彩色小图标 · 全部页面）

### §6.1 现状取证（为什么显老）

| 根因 | 证据 |
|---|---|
| 主题色是 2021 年 M3 官方示例紫 | `app/src/main/java/com/minibook/ui/theme/Color.kt:16-32`：`primary = Color(0xFF6750A4)`、`surface = Color(0xFFFEF7FF)`、`onSurface = Color(0xFF1D1B20)`——一眼「没设计过」。API 31+ 有壁纸动态取色，31 以下与「恢复默认主题色」都落到这套 |
| 没有 M3 的分层表面色，全靠 `surfaceVariant + alpha` 凑 | 全仓库 `surfaceContainer*` **0 命中**；`surfaceVariant.copy(alpha = 0.4~0.5f)` 至少 8 处：`ui/budgets/BudgetsScreen.kt:272`、`ui/billimport/ImportScreen.kt:319`、`ui/billimport/ImportComponents.kt:64`、`ui/home/HomeScreen.kt:554`、`ui/components/States.kt:69`、`:118`、`:175`、`ui/reports/components/ChartEmptyState.kt:53` |
| 卡片平铺、无层级 | 显式 `containerColor = MaterialTheme.colorScheme.surface`：`ui/reports/ReportsScreen.kt:430`、`:466`、`ui/reports/components/ReportSummaryCard.kt:40`；`ui/components/Cards.kt:34-37` 净额卡用默认 `Card` + `shapes.large`，内部靠 `HorizontalDivider`（`Cards.kt:52`）分隔 |
| 图标是 emoji，最显眼的老 | `ui/main/MainScreen.kt:40-46` 底部导航 `🏠 📋 💰 🏷️ ⚙️`；`ui/settings/LedgerManageScreen.kt:256-258` `✏️`/`🗑`；月份切换是 `‹`/`›` 文字（`ui/home/HomeScreen.kt:443-456`）；其余十几处是手绘 `ImageVector`（`ui/components/States.kt:44`、`ui/home/HomeScreen.kt:613`），注释写明「material-icons 缺失（TASK-020 偏差）」「禁改依赖」（`ui/settings/LedgerEditorSheet.kt:43`） |
| 形状/字体是出厂设置 | `ui/theme/Theme.kt:53-58`：`small 8 / medium 12 / large 16 / extraLarge 28`；`ui/theme/Type.kt:12`：`Typography()` 完全未覆盖（仅 `TabularFigures` 一处 tnum ✅） |

### §6.2 方向结论（2025–2026 主流）

- **材质**：**无阴影 + tonal surface 分层**是当前主流（Material 3 Expressive 明确「工具栏无阴影、靠色面分层」；Apple 侧为半透明玻璃材质，但玻璃只用于控件/导航层，不用于内容层——HIG 细则**未验证**，页面需 JS）。⇒ 我们走「浅底 + tonal 卡片 + 大圆角」，**不引入半透明玻璃**（记账内容层会牺牲对比度）。
- **形状**：大圆角 + shape morph（按钮组/FAB 会变形旋转）；**直角卡片已过时**。
- **动效**：spring 弹性替代线性 easing；**动效预算花在状态切换**（保存成功、月份切换），不做装饰性逐项弹入。
- **图标**：Material 1.4.0 起 `material.icons` 库不再推荐，改用 **Material Symbols**（可变字重）。
- **组件形态**：官方**已弃用 bottom app bar** → docked toolbar / floating toolbar。
- 可信度：Google 自述做了 46 项研究、18,000+ 参与者，称用户偏好 expressive 设计（厂商口径）。

### §6.3 版本现实（决定性结论）

**Material 3 Expressive 的 API 不在 1.4.0 稳定版里。** 1.4.0-beta01（2025-07-30）发布说明原文：*"All public APIs tagged with ExperimentalMaterial3ExpressiveApi or ExperimentalMaterial3ComponentOverrideApi have been removed, please switch to 1.5.0-alpha to continue enjoying these features."*

| 项 | 状态 |
|---|---|
| `material3` 稳定版 | **1.4.0（2025-09-24）**；1.3 线止于 1.3.2（2025-04-09） |
| 当前预览 | **1.5.0-beta01（2026-10-07）** |
| Compose BOM | 最新列出 **2026.09.00**（其 material3 取值为 **1.4.0**）；**本仓库 `gradle/libs.versions.toml:7` 为 `composeBom = "2025.09.01"`** ⇒ material3 亦为 1.4.0 |
| 1.4.0 稳定版**确实带** | `MotionScheme` 定义动效、`HorizontalCenteredHeroCarousel`、`VerticalDragHandle`、**`SecureTextField`/`OutlinedSecureTextField`**、`TextAutoSize`、`material.icons` 弃用 |
| 仅 1.5.0+（**experimental/beta**） | `MaterialExpressiveTheme` + `expressiveLightColorScheme()`、`MotionScheme.expressive()`/`MaterialTheme.motionScheme`、`MaterialShapes`（35 个 `RoundedPolygon`）、`Morph.toPath(...)`、`ButtonGroup`、`ToggleButton`、FAB Menu、`FloatingToolbar`、`WavyProgressIndicator`、`LoadingIndicator`（stable 提升被 revert） |

**决策（建议）**：**留在 1.4.0 稳定版**，靠自定义 token（颜色/形状/排版/间距/动效 spec）拿到绝大部分观感提升；**不为观感升到 1.5.0-beta01**（preview API 仍在改：Slider `onValueChange` 变必填、`activeRangeStart` 改名 `startValue` 等，会引入构建风险与回归面）。若将来要 expressive 组件，单独立项评估。

### §6.4 高回报改动清单（按「全站生效优先」排序）

| # | 改什么 | 预期观感 | 代价 | 我方落点 |
|---|---|---|---|---|
| 1 | shapes：卡片 16dp、弹层 28dp、FAB 16dp | 立刻脱离「默认 M3」 | 小 | `ui/theme/Theme.kt:53-58` |
| 2 | `surfaceVariant` → **`surfaceContainerLow/High`**，去描边 | 层级用色面表达，不再脏 | 小 | §6.1 的 8 处 alpha 面板 |
| 3 | 删列表分割线，改 8–12dp 间距分组 | 现代、通透 | 小 | `ui/components/Cards.kt:52` 等 |
| 4 | 金额：`headlineMedium` + `SemiBold` + `tnum` | 数字不跳动、有主次 | 小 | `ui/components/Cards.kt:45-49`（现 `displaySmall`）、`ui/components/ListItems.kt` |
| 5 | 金额统一右对齐、固定小数位 | 扫读速度显著提升 | 小 | `MoneyText` 调用点 |
| 6 | 收/支语义色成对使用，不做大色块 | 记账核心可读性 | 小 | `ui/theme/Color.kt:64-75` 已有 ✅ |
| 7 | `NavigationBar` 去阴影、选中态 tonal pill | 符合 2025 观感 | 小 | `ui/main/MainScreen.kt` |
| 8 | FAB → `LargeFloatingActionButton` | 视觉重心 + 少误触 | 小/中 | 首页/记账页 |
| 9 | `TopAppBar` → `MediumFlexibleTopAppBar`（滚动折叠） | 「高级感」的舒展感 | 中 | 各根页 |
| 10 | 主题切 expressive（**需 1.5.0-beta01**）或自建同构 colorScheme | 一次性拿到 expressive 配色 | 小（有 preview 风险） | **不采纳**，见 §6.3 |
| 11 | 统一 spring 动效 spec（1.4.0 无 `motionScheme`，自建） | 弹性 = 最重要的「高级感」来源 | 小 | 新增 motion token |
| 12 | 首页 Hero 卡：大号月度结余 + 环比变化 + 迷你趋势 | 首页不再是「列表堆」 | 中 | `ui/home/HomeScreen.kt:391-459` |
| 13 | 动态取色（已有）作主、品牌色 fallback | 感知「新」 | 小 | `ui/theme/Theme.kt:104-105` 已有 ✅ |
| 14 | 暗色用 `surfaceContainerLow` 而非纯黑，金额对比度单独校验 | 避免暗色翻车 | 小 | `ui/theme/Color.kt:34-50` |
| 15 | 图标换 Material Symbols / 自带矢量 | 图标从「系统默认」变「设计过」 | 中 | 见 §6.5 决策 E |

**全站一次性生效的是 1/2/3/4/5/6/7/11**（token 级）；**观感增量最大的是 12/15**。

### §6.5 图标方案（决策 E）与「全部页面」范围

**约束**：仓库明确「**禁改依赖**」（`ui/settings/LedgerEditorSheet.kt:43`，并有多处「material-icons 缺失（TASK-020 偏差）」注释）⇒ 不引入 `material-icons-extended`，也不引入 Material Symbols 字体资源。

| 方案 | 评价 |
|---|---|
| **A. 自带 vector drawable XML + `MiniBookIcons` 对象**（仓库先例：`app/src/main/res/drawable/ic_launcher_foreground.xml`） | ✅ **推荐**：零依赖变更、可 tint、与现有手绘 `ImageVector` 方案同源；`ui/components/Navigation.kt:52` 的 `BottomNavItem` 本来就吃 `ImageVector` |
| B. 升 Material Symbols 字体 | ❌ 需字体资源与加载路径，改动面大于收益 |
| C. 保留 emoji | ❌ 不可 tint、各机型字形不一，正是用户说的「老」 |

**「全部页面」的实际范围**：token 级改动（§6.4 的 1/2/3/4/5/6/7/11/14）自动覆盖约 30 个界面；需要**逐页动刀**的只有三类：
1. 硬编码了旧惯用法的 **11 处**（8 处 alpha 面板 + 3 处 `containerColor = surface`）；
2. emoji/文字图标的 **3 处**（`ui/main/MainScreen.kt:40-46`、`ui/settings/LedgerManageScreen.kt:256-258`、`ui/home/HomeScreen.kt:443-456`）；
3. 首页 Hero 卡（`ui/home/HomeScreen.kt:391-459`）与金额排版（`ui/components/Cards.kt`、`ui/components/ListItems.kt`）。

### §6.6 记账场景专属反例（改造时必须守住的线）

1. **大圆角 + 大留白压垮信息密度**：记账核心是「快速扫一屏流水」，卡片 28dp 圆角 + 行 20dp 留白会让一屏从 12 条降到 6 条 ⇒ **圆角给卡片、行内间距回 4/8dp 网格**。
2. **对比度不足**：浅灰正文 + 极浅灰底在户外/低端屏不可读，**读错一位数字就是真金白银**（WCAG 1.4.3：正文 4.5:1、大字 3:1，不可四舍五入）。
3. **红绿编码**：A 股语境「红涨绿跌」与西方「红支出绿收入」相反，且红绿色盲不可辨 ⇒ 必须**颜色 + 符号/文字双编码**（我方 `MoneyText` 已带 +/− 与语义色，需保持）。
4. **动效过度**：记账流程只用状态切换动效，不做逐项弹入。
5. **金额不对齐/小数位不一**：`TabularFigures` 必须覆盖所有金额，小数位统一（我方 `MoneyFormatter` 已统一，需回归确认）。
6. **暗色直接反色**：纯黑底 + 高饱和品牌色会眩光，夜间记账尤其伤 ⇒ 用 tonal surface 层级，金额色单独校验。
7. **半透明玻璃质感**：内容层会牺牲对比度 ⇒ 不采纳（见 §6.2）。
8. **动态取色全盘接管品牌色**：收入/支出/预警是**语义色，不能随壁纸漂移** ⇒ 动态取色只作用中性面与强调色，语义色固定（我方 `LocalSemanticColors` 已如此 ✅）。

## §7 来源清单（可点开核对）

- NIST SP 800-63B-4：https://pages.nist.gov/800-63-4/sp800-63b.html
- OWASP Authentication Cheat Sheet：https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- OWASP ASVS 5.0 V6：https://raw.githubusercontent.com/OWASP/ASVS/master/5.0/en/0x15-V6-Authentication.md
- WCAG 2.2：对比度 https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html · 错误标识 https://www.w3.org/WAI/WCAG22/Understanding/error-identification.html · 仅颜色 https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html · 冗余输入 https://www.w3.org/WAI/WCAG22/Understanding/redundant-entry.html · 可访问认证 https://www.w3.org/WAI/WCAG22/Understanding/accessible-authentication-minimum.html
- Android 无障碍（触控 48dp、contentDescription）：https://developer.android.google.cn/guide/topics/ui/accessibility/apps?hl=en
- Android autofill 优化（autofillHints）：https://developer.android.google.cn/guide/topics/text/autofill-optimize?hl=en
- Compose 显示/隐藏口令官方 Quick Guide：https://developer.android.google.cn/develop/ui/compose/quick-guides/content/show-hide-password?hl=en
- Compose 文本输入与 IME：https://developer.android.google.cn/develop/ui/compose/text/user-input?hl=en
- Credential Manager：https://developer.android.google.cn/identity/sign-in/credential-manager?hl=en
- Material TextField 文档（password_toggle / helperText / errorLiveRegion）：https://raw.githubusercontent.com/material-components/material-components-android/master/docs/components/TextField.md
- Material 2 归档：错误模式 https://material.io/archive/guidelines/patterns/errors.html · Snackbar/Toast https://material.io/archive/guidelines/components/snackbars-toasts.html
- 归档 iOS HIG：Text Fields https://web.archive.org/web/20220301000000/https://developer.apple.com/design/human-interface-guidelines/ios/controls/text-fields/ · Accounts …/ios/user-interaction/accounts/ · Onboarding …/ios/app-architecture/onboarding/
- 国内合规：认定方法 https://www.cac.gov.cn/2019-12/27/c_1578986455686625.htm · 必要个人信息范围规定 https://www.cac.gov.cn/2021-03/22/c_1617990997054277.htm · 应用程序信息服务管理规定 https://www.cac.gov.cn/2022-06/14/c_1656821626455324.htm · 网络安全法（2025 修正）https://www.cac.gov.cn/2025-12/29/c_1768735112911946.htm · 账号信息管理规定 https://www.gov.cn/zhengce/zhengceku/2022-06/28/content_5698179.htm · 弹窗推送规定 https://www.cac.gov.cn/2022-09/08/c_1664260384702890.htm · APP 备案 https://www.gov.cn/zhengce/zhengceku/202308/content_6897341.htm · 164号文 https://www.gov.cn/zhengce/zhengceku/2020-08/02/content_5531975.htm · 个保法 http://www.npc.gov.cn/npc/c2/c30834/202108/t20210820_313088.html
- 国内登录页原文：美团 https://passport.meituan.com/account/unitivelogin · 京东 https://passport.jd.com/new/login.aspx · 支付宝 https://auth.alipay.com/login/index.htm · 12306 https://kyfw.12306.cn/otn/resources/login.html · 微信网页版 https://wx.qq.com · 移动认证 https://www.cmpassport.com · 天翼账号 https://id.dlife.cn
