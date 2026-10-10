# 测试报告

> 状态：F-14（短信账单导入）**JVM 单测已绿**（52 类 / 410 例 / 0 失败）；**真机 + 模拟器验收已完成（2026-10-10）**，并完成验收后口径定案（F1 修复保留；F2 角标方案按用户决策**撤销**）+ 边界短信复测 + **验收后 UI 改版（TASK-059 / DEC-029）** + **增量 / 全量两种扫描方式（TASK-060 / DEC-030）**——见下方最新章节
> 最后更新：2026-10-10
> 维护者：Tester

## 最新测试（F-14 增量 / 全量两种扫描方式 + 白名单空态，2026-10-10 13:2x）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-10（13:2x） |
| 授权依据 | 用户指令「插上了，真机测试把」+ 本轮反馈（两种扫描方式原话见 `13_sms_bill_import.md §16` / DEC-030） |
| 真机环境 | 小米 `某机型`，Android 16 / SDK 36（HyperOS），adb `A1B2C3D4`，1200×2608 |
| 执行方式 | 主代理单点串行执行（规则 3）：`install -r -g` + `pm grant` → 冷启 → `uiautomator dump` 定位 → `input tap` → `screencap` 截图 + 节点文本比对 |
| 详细记录 | `docs/13_sms_bill_import.md §16`（逐端证据与截图清单）、`docs/07_decisions.md` DEC-030 |

### 结果总览

| 指标 | 数值 |
|------|------|
| JVM 单测（本轮） | **52 类 / 410 例 / 0 失败 / 0 错误 / 0 跳过**（F-14 占 7 类 / **77** 例；本轮 `SmsImportPresenterTest` +2 例） |
| `:app:assembleDebug` | **绿**（`BUILD SUCCESSFUL in 53s`） |
| ① 增量扫描（真机） | **通过**：`扫描 0 条` —— 那条已看过的快递短信**完全不出现**（既非折叠也非灰显），正是用户第三次反馈的核心诉求（首轮证据截图里说明行还是旧文案 `增量扫描：只看 05-07 15:15 之后的短信`，该文案已在 13:4x 节点修正中替换，见下 ⑤） |
| ② 全量扫描（真机） | **通过**：`扫描 1 条 = 可导入 0 + 未识别 1 + 非账单 0 + 已存在/重复 0` + `全量扫描：列出全部历史短信` + `▸ 此前已看过 1 条（点击展开）` |
| ③ 入口卡两按钮（真机） | **通过**：短信卡 = `增量扫描`（主）+ `全量扫描`（次）+ 一行小字「增量＝只看上次扫描之后的新短信；全量＝全部历史（看过的折叠在下面）。」 |
| ④ 白名单空态（真机） | **通过**：`白名单为空：对某个发送方手动确认入账 3 次（期间无纠错）后自动加入；…`，且**不再出现「清空白名单」按钮**（用户提问「白名单在哪看，就只有清空」） |
| ⑤ 时间节点 = 扫描时刻（真机，节点修正后复验） | **通过**：进导入页 → L3 补扫把节点写为 `13:47`（墙钟）→ 增量 `本次覆盖 10-10 13:47 之后的新短信，起点已更新为 10-10 13:48` ✓；**再跑一次全量 → 节点不动**；随后增量 `本次覆盖 10-10 13:48 …，起点已更新为 10-10 13:49` ⇒ 覆盖起点 = 上次**扫描**时间（既非短信时间、也非全量时刻）✓ |
| ⑥ 增量「只读新到达的」（模拟器，新构建复跑） | **通过**（`emulator-5554`，App 保持打开）：增量 #1 → `扫描 0 条` + `本次覆盖 10-10 05:35 …，起点已更新为 10-10 05:51`（节点从旧值推进到本次扫描时刻）；注入短信 C → 增量 → `扫描 1 条 = 可导入 1` + `本次覆盖 10-10 05:51 …，起点已更新为 10-10 05:52` ⇒ **只读到新到达的 C，早先的 A/B 不重读**（严格 `date > 节点` 成立）✓ |
| ⑦ 增量首次无节点 | 修正前在模拟器上实测过旧行为（**每次增量都提示「首次使用已建立水位基线」**——空库立不起节点，正是本次修正要修的毛病）；修正后该分支文案由**单测**锁定（`增量扫描：首次使用，起点已设为本次扫描时间 …`），真机/模拟器上节点均已存在，未再触发 |
| 未覆盖（如实挂账） | 白名单**非空态**（新增的「白名单（N 个发送方…）」标题行 + 清空按钮）本轮未实测：真机白名单为空，模拟器 AVD 在重启后数据被重置（白名单亦空）。该分支改动 = 一行 `HintText` + 原 `forEach` 逐项开关不变 + 清空按钮移入 `else`，编译与空态路径均已验证；真实非空态待下次有发送方晋级后目视确认 |

### 单测增量明细（+2 例，`SmsImportPresenterTest`）

| 用例 | 断言要点 |
|------|----------|
| `扫描方式说明行：全量与增量（时间节点 = 扫描时刻而非短信时间）` | 全量恒为 `全量扫描：列出全部历史短信`（与节点无关）；增量常规 = `增量扫描：本次覆盖 10-10 12:00 之后的新短信，起点已更新为 10-10 12:30`（本地时区格式化）；增量首次（`sinceMs=0, scannedAtMs=12:30`）= `增量扫描：首次使用，起点已设为本次扫描时间 10-10 12:30（历史短信请用「全量扫描」）`；拿不到扫描时刻时退回 `增量扫描：只看 10-10 12:00 之后的新短信`（不显示 1970 年） |
| `预览携带扫描方式与时间节点：计数闭合不受影响` | `SmsScanPreview` 默认 `FULL / 0L / 0L`；`copy(scanMode = INCREMENTAL, sinceMs = …, scannedAtMs = …)` 后 `assertClosed()` 仍成立、`scanTotal` 与 `unrecognized` 不变（扫描方式与时间节点只是元信息） |

## 最新测试（F-14 验收后 UI 改版：「已看过」标记 / 导入页三入口 / 设置分组汇总，2026-10-10 11:00–11:25）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-10（11:00–11:25） |
| 授权依据 | 用户三点要求（原话见 `13_sms_bill_import.md §15` / DEC-029）+ 既有真机测试授权 |
| 真机环境 | 小米 `某机型`，Android 16 / SDK 36（HyperOS），adb `A1B2C3D4`，1200×2608 |
| 模拟器环境 | AOSP `Pixel_7`，Android 15 / API 35，`emulator-5554`，1080×2400 |
| 执行方式 | 主代理单点串行执行（规则 3）：`install -r -g` + `pm grant` → `uiautomator dump` 定位 → `input tap` → `screencap` 截图 + 节点文本比对 |
| 详细记录 | `docs/13_sms_bill_import.md §15`（含逐端证据与截图清单）、`docs/07_decisions.md` DEC-029 |

### 结果总览

| 指标 | 数值 |
|------|------|
| JVM 单测（本轮） | **52 类 / 408 例 / 0 失败 / 0 错误 / 0 跳过**（F-14 占 7 类 / **75** 例；本轮 `SmsImportPresenterTest` +2 例） |
| `:app:assembleDebug` | **绿**（`BUILD SUCCESSFUL in 2m 48s`） |
| ① 已看过标记（模拟器） | **通过**：第 1 次扫描「未识别 1」在主列表；第 2 次扫描 → 主列表清空 + `▸ 此前已看过 1 条（点击展开）`；计数两次完全一致（`扫描 17 条 = 可导入 0 + 未识别 1 + 非账单 3 + 已存在/重复 13`） |
| ① 已看过标记（真机） | **通过**：第 1 次扫描显「⚠ 未识别（发送方未登记模板）：您好,您京东到了一个件…」；第 2 次折叠为「此前已看过 1 条」（用户投诉场景复现并消除） |
| ② 导入页三入口（两端） | **通过**：模拟器/真机均为「选择导入方式 + 账单文件导入 / 邮箱账单自动导入 / 短信账单导入」三卡（标题 + 一行说明 + 按钮）+「▸ 如何导出账单？」折叠条；微信/支付宝指引卡不再常驻 |
| ② 返回语义（模拟器） | **通过**：结果态按系统返回键 → 回三入口页（`选择导入方式` 可见），再返回才退出本页 |
| ③ 设置分组汇总页（两端） | **通过**：5 行汇总（账本 / 外观：跟随系统 · 默认 / 安全：应用锁未开启 / 数据：备份导出·导入 + 邮箱凭据（真机「已配置 1 个」/+ 模拟器「未配置」）/ 短信账单：已开启 · 静默聚合）+ 页脚 `MiniBook v1.0.0` |
| ③ 四个二级页（模拟器） | **通过**：`settings-sms`（开关矩阵 + 白名单 + 权限）、`settings-appearance`（暗色模式 + 主题颜色）、`settings-security`（应用锁 + 锁定时长 + 数据加密）、`settings-data`（导出/导入/导入账单/邮箱凭据）均可达，顶栏「← + 标题」返回 |
| ③ 二级页（真机抽验） | **通过**：`settings-sms` 页含「本机短信库共 1 条（App 可见数量…）」（MIUI 受限视图口径，见 §14.4） |
| 未覆盖（如实挂账） | 折叠区**展开态**真机截图未单独采集（同一 presenter 纯函数 + 单测覆盖）；真机仅抽验一个二级页 |

### 单测增量明细（+2 例，`SmsImportPresenterTest`）

| 用例 | 断言要点 |
|------|----------|
| `已看过的未识别条目移出主列表、进折叠区` | `lines()` 不含 `seen=true` 条目、含 `seen=false` 条目；`seenLines()` 精确等于该条 ⚠ 行；`unrecognized.size == 2` 且 `scanTotal == 8`（**计数闭合不因折叠改变**） |
| `未识别行文案统一：主列表与折叠区同一格式` | `unrecognizedLine(item) == "⚠ 未识别（发送方未登记模板）：摘要：支出 77.00 元"`（折叠区不另造格式） |
| `待确认条数 = 未看过的未识别 + 疑似重复（已看过不计，2026-10-10 二次反馈）` | 全看过 → 归零（只保留疑似重复）；混合（1 已看过 + 1 新）→ 只报新条目；`null` → 0 —— 在**原用例内扩展断言**，用例总数不变（仍 52 类 / 408 例） |

## 最新测试（F-14 真机 + 模拟器验收，2026-10-10）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-10 |
| 授权依据 | 用户指令「真机连接上了，你可以测试一下，如果你能模拟发短信，那就非常棒了」 |
| 真机环境 | 小米 `某机型`，Android 16 / SDK 36（HyperOS），adb `A1B2C3D4`，1200×2608 |
| 模拟器环境 | AOSP `Pixel_7`，Android 15 / API 35（`sdk_gphone64_x86_64`）；`adb emu sms send` 注入真实 PDU → 真 `SMS_RECEIVED` 广播 |
| 执行方式 | 主代理单点执行（规则 3）：`adb install -r` + `uiautomator` 定位点击 + `logcat` + `run-as` 读 DataStore/DB |
| 详细记录 | `docs/13_sms_bill_import.md §14`（含缺陷根因、SQL 报错原文与 MIUI 证据） |

### 结果总览

| 指标 | 数值 |
|------|------|
| JVM 单测（复跑，口径定案后） | **52 类 / 406 例 / 0 失败 / 0 错误 / 0 跳过**（F-14 占 7 类 / 73 例） |
| `:app:assembleDebug` | **绿** |
| 真机 L0 全量干跑（真实数据） | **通过**：`扫描 1 条 = 可导入 0 + 未识别 1 + 非账单 0 + 已存在/重复 0`，零写入 |
| 真机权限流 | **通过**：重装后经 `pm grant --user 0` 授权，**不重启**即进入扫描（缺陷 B 修复验证） |
| 模拟器 L0 干跑 | **通过**：`扫描 4 = 可导入 3 + 未识别 0 + 非账单 1 + 已存在/重复 0`（3 条账单 + 1 条验证码） |
| 模拟器余额链审计 | **通过**：故意注入缺口（应 755.91，实发 700.00）→「余额链疑似缺口 1 处」 |
| 模拟器确认入库 | **通过**：新增 3 笔、跳过 0 条；首页见记录与「短信」来源徽标；金额自洽（支出 325.00 = 100+50+100+30+20+25，收入 200.00，结余 −125.00） |
| 模拟器幂等重扫 | **通过**：`已存在/重复 3、可导入 0`，逐条给出跳过原因 |
| 非账单零提醒 | **通过**：验证码短信（App 进程已杀）→ `自动 0，待确认 0`，无通知 |
| L1 后台待确认 | **通过**：`自动 0，待确认 1`，不写库、不崩溃 |
| **L2 后台自动记账** | **通过**：3 次确认晋级 + 开「自动记账」+ 杀进程注入 → **`自动 1，待确认 0`**，首页出现 ¥25.00 记录 |

### 本轮发现并修复的 5 个缺陷（均为「JVM 绿 / 真机静默失效」）

| # | 缺陷 | 发现手段 | 修复 |
|---|------|---------|------|
| A | `SmsBillReader.selection()` 生成 `1..6.first` 非法 SQL | 真机 logcat：`SQLiteException: near ".6": syntax error` | 改拼 `SmsMessageType` 常量 |
| B | `ImportScreen` 权限取组合期快照 → 引导页卡死 | 真机重装后授权复检 | 点击时 + `onResume` 当场复查 |
| C | 接收器漏判「自动记账」总闸（未开启也可能写库） | 代码核对 + 模拟器复测 | 统一口径 `SmsSettings.autoImportUsable` |
| D | 银行模板兜底渠道 `OTHER` → **L2 永不触发** | 接收器诊断日志：`原因=渠道（other）会被账单文件覆盖` | `SmsTemplate.defaultChannel`；`bank.generic` → `BANK_DIRECT`；补回归用例 |
| E | 广播拉起的新进程未开库 → 后台链路静默失效（异常被吞） | 模拟器 logcat：`IllegalStateException` | 后台静默开库（DEC-002）+ DB 未就绪降级「待确认」+ 结果/原因日志 |

### 新的能力边界（如实登记）

**MIUI/HyperOS 受限视图**：非默认短信应用的 `content://sms` 查询被 ROM 重写为 `sms_restricted`，过滤 `sp_type≠0` 的服务/通知类会话。实测 App 可见 **1 条** / shell 可见 **9 条** → **oracle 对齐（shell 计数 = App 计数）在本 ROM 上不成立**，不得据此判定漏读。详见 `13_sms_bill_import.md §14.4`。

### 验收后口径定案：三层兜底（F1 修复保留 / F2 角标撤销）+ 边界短信复测（2026-10-10）

默认通知方式是**静默聚合**（不弹通知）。实测发现该配置下「未白名单发送方的银行短信」在 App 内**没有任何提示**：数据没丢（L0 能重新识别，已实测），但发现环节断了。先按「补扫 + 水位不越界 + 角标」补修并验证通过，随后**真机角标常驻去不掉**（缺陷 F3：未识别条目无销账出口），用户据此拍板**撤销角标**，改为三层兜底（根因、证据与复盘见 `13_sms_bill_import.md §14.6`，口径见 DEC-028 修订）：

| # | 项 | 结果 |
|---|----|------|
| F1 | 补扫触发点由「打开导入页」改为「打开 App（首页 VM 初始化）」 | **通过（保留）**：真机 `logcat -s MiniBookSms` 每次开 App 都出 `L3 补扫：扫描 1，自动 0，待确认 1，跳过=-` |
| F2a/b/c | 水位不越过待确认 + `SmsPendingTracker` + 首页角标 | **曾实现并验证通过**（模拟器：杀进程后开 App 角标自愈 2 条、L0 写入 2 笔后角标消失、首版 `onError` 配色不可读已改白字）→ **2026-10-10 用户决策撤销**，跟踪器（含 7 例单测）、角标与销账调用全部删除，水位回到「读到即推进」 |
| F3 | 角标常驻（未识别条目无销账出口） | **由决策消解**：不做角标、不做已读未读 ⇒ 该缺陷不再存在；「不遗漏」改由 L0 手动导入兜底 |
| — | 撤销后复验（真机 + 模拟器） | **通过**：两端首页均无任何「待确认」节点；补扫日志照常；真机账本数字未受影响 |
| B1/B2 | 通知类（106 端口）与「银行端口但无金额」 | **通过**：均判**非账单**，零提醒、不写入（不猜金额） |
| B3 | 发送方未登记模板 | **通过**：判**未识别**，清单页显示发送方/时间/预览/原因「发送方未登记模板」+ 重新扫描/手工记一笔 |
| B4 | **跨源去重**（手工 ￥77.00 vs 未入库的 ￥77.00 短信） | **通过**：复扫由「可导入」转「已存在/重复」（`可导入 1→0`、`重复 12→13`）；该行在惰性列表可视区外，未截到逐行文案 |

### 仍未验证（不得声称已验证）

1. 应用锁开启态 L2 门控（需系统锁屏 + 生物识别/PIN）；
2. 真实银行账单短信端到端（本机 App 可见短信中无银行账单，需另一台手机发送）；
3. MIUI 对 95xxx 短号的 `sp_type` 归类；
4. 通知「逐条提醒」模式（默认「静默聚合」已实测：无通知）；
5. L3 增量补扫场景 —— **已构造并验证**：开 App 即触发（真机日志 `L3 补扫：扫描 1…`）；「角标重启自愈」属已撤销方案，不再适用；
6. L4 分享导入未实现；
7. 双卡（`sub_id`）与长时间跨度（跨月/跨年）扫描（模拟器单卡、`emu sms send` 无法指定 `sub_id`/短信时间）。

### 结论

- [x] 通过（**JVM 单测 + 构建 + 真机 L0 + 模拟器 L0/L1/L2 端到端**）
- [ ] 不通过（无）

结论说明：F-14 在两端完成可执行验收；**6 个实测缺陷已修复/定案**（真机 5 个修复复测 + 验收后 F1 修复保留；F2 角标方案与 F3 常驻缺陷按用户 2026-10-10 决策**撤销**，改为三层兜底 = ①到达即记账 → ②打开 App 补扫 → ③手动导入），并复测 4 类边界短信（通知类 / 无金额银行短信 / 未登记模板 / 跨源判重）；未覆盖项见上（应用锁态、真实银行样本、逐条通知、双卡与长跨度、L4）。

## JVM 单测（F-14 短信账单导入，2026-10-09）

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-10-09 |
| 测试范围 | F-14 短信账单导入：解析器（模板锚点/金额转分/方向判定/日期跨年/幂等键）、跨源匹配规则（唯一判重/多候选/同源排除/双向）、余额链审计、白名单四条件与晋级降级、计数闭合不变量、白名单编解码、导入页 presenter 展示口径。对应 TASK-051~057；先读 `docs/13_sms_bill_import.md` + `docs/05_ui.md §11`。 |
| 被测代码 | 工作区未提交改动（F-14 全量实现，2026-10-09） |
| 构建环境 | JDK21（`D:\soft\jdk\jdk21`）；`:app:assembleDebug` BUILD SUCCESSFUL（app-debug.apk 35.95 MB） |
| 执行命令 | `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest` |

### 结果总览（证据来自 `app/build/test-results/testDebugUnitTest/*.xml`）

| 指标 | 数值 |
|------|------|
| JVM 单测 | **52 类 / 406 例 / 0 失败 / 0 错误 / 0 跳过**（基线 45 类 / 333 例 → 本次 +7 类 / +73 例；含 2026-10-10 补的渠道兜底回归 1 例） |
| 本次失败用例 | 首跑 394 例中 3 例失败，**全部为测试自身缺陷**（见下），修正后全绿 |
| `:app:assembleDebug` | **绿**（含新增 3 项权限与 `SmsReceivedReceiver` 的清单合并） |
| androidTest | 本阶段未新增；既有 instrumented 用例仍未在设备执行（延续挂账） |
| 真机 / 端到端 | ✅ **已执行（2026-10-10）**，见上方「最新测试」章节与 `13_sms_bill_import.md §14` |

### F-14 单测明细（7 类 / 73 例）

| 文件 | 例数 | 覆盖点 |
|------|:---:|------|
| `SmsBillParserTest` | 22 | 银行消费/入账抽全字段；金额边界（千分位/最小分/三位小数非法）；收支同现取靠前；无方向词→`Unrecognized`（不猜）；金额/日期缺失→`Unrecognized`；验证码/营销→`NotBill`；未登记发送方→`Unrecognized`；支付宝/财付通渠道抑制；**银行模板兜底渠道=银行直连且允许自动记账（2026-10-10 实测缺陷 D 回归）**；幂等键稳定性（同文稳定/金额不同不同/零宽与空白不影响）；跨年回退；摘要脱敏 |
| `SmsWhitelistPolicyTest` | 13 | 四条件逐条否定 + 全满足→自动；3 次确认晋级；已达阈值但用户关闭→不重开；纠错降级（关闭+清零）；错误留痕 |
| `SmsImportPresenterTest` | 11 | 干跑 headline 与计数闭合等式一致；结果 headline 四类计数；逐条结果行四类标记；超 100 行截断；5 步状态机（扫描中/预览/失败/完成）；余额链文案不含余额数值；待确认计数 |
| `SmsImportSummaryTest` | 8 | 两条闭合不变量（合法/各自违反抛异常）；空库；渠道抑制口径 |
| `BalanceChainAuditorTest` | 7 | 自洽链条无缺口；断裂检出缺口与时间区间；收入方向；不同卡尾号不互比；样本不足跳过；`headline` 汇总 |
| `SmsMatchRulesTest` | 7 | 无候选→新；唯一跨源→判重（依据含金额与来源）；两条→疑似重复；同来源不参与；**双向**（账单文件入口反查短信行）；金额格式化；来源文案 |
| `SmsWhitelistStoreCodecTest` | 5 | 编解码往返（含/不含错误时间戳）；畸形行忽略；按发送方升序；计数非法回退 0 |

### 失败用例详情（首跑 3 例，均为测试自身缺陷，已修正）

1. `EnumRoundTripTest.枚举条目数量与规格一致`：既有守卫断言 `TransactionSource.entries.size == 3`，F-14 新增 `SMS_IMPORT` 后应为 4 → 同步断言与 value 断言。
2. `SmsImportSummaryTest.解析计数不闭合 → 抛异常`：用例给的数据其实**两条不变量都成立**（`10 = 6+3+1`、`6 = 6+0+0+0`）→ 改为真正漂移的组合（`parsed 5 ≠ imported 4`）。
3. `SmsBillParserTest.幂等键：空白与零宽字符不影响哈希`：断言写错语义——零宽字符是**删除**而非「转为空格」，全角空格 U+3000 才转空格 → 拆成三条规则分别断言。

### 已知遗留 / 未验证项（**不得声称已验证**）

1. **无真实短信账单样本**：以上全部为**合成 fixture**；解析准确率（真实银行的格式漂移）未经真实样本回归。
2. **真机端到端**：✅ 已于 2026-10-10 执行（见本文件顶部章节）；其中 `SmsNotificationHelper`「逐条提醒」模式与「应用锁开启」态仍未覆盖。
3. **L3 补扫（`catchUp`）无单测**：依赖 `SmsBillReader`（Android `ContentResolver`）与 `ImportBillUseCase`（多依赖），JVM 侧无替身；口径（首次只立基线、开关门控、水位推进）为**代码级保证**。
4. **UI 交互测试未覆盖**：待确认清单页、设置区块、权限引导为 Compose 交互，延续项目「UI 可选」口径。
5. 解析按天匹配（时间戳丢弃）→ 跨天错位漏判为**已知盲区**，二期以 `occurredAt` + ±10 分钟窗口解决。

### 结论

- [x] 通过（**限 JVM 单测与构建**）
- [ ] 不通过（无）

结论说明：F-14 JVM 单测 73 例 + 全量 406 例全绿、`:app:assembleDebug` 绿；**真机与模拟器端到端验收已于 2026-10-10 完成**（见本文件顶部章节），仍未覆盖项已在章内如实登记。

---

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

### 真机验证结果（2026-08-24 补验，编排者执行）

> 前置：`adb install -r app\build\outputs\apk\debug\app-debug.apk`（覆盖安装保数据）→ **Success**。设备 MEIZU 21（`461QYFDN225D9`）连接时断时续（authorized↔unauthorized 反复），关键步骤均完成。

1. **真机迁移保数据** ✅：`am start` 启动，进程存活，`logcat` 搜 `Room|SQLiteException|IllegalState|Migration|FATAL|AndroidRuntime` 无命中——迁移成功、无整库重建；列表可见既有数据（含更早导入的微信账单数据与手动记账数据）。
2. **入口 + 导入页 Idle 态** ✅：uiautomator dump 确认首页三格 tile 含「导入账单」📥；导入页顶栏「导入账单」+ 引导说明 + 微信/支付宝两 `PlatformGuideCard`（含导出路径与 `.xlsx`/`.csv` 提示）+「去选择文件」按钮，文案与 05_ui §9 精确一致。
3. **支付宝端到端导入** ✅：push `alipay.csv` 到 `/sdcard/Download/` → SAF（DocumentsUI）选文件 → 首页最近交易出现支付宝数据：`支付宝` SourceBadge、自动分类（交通🚗/餐饮🍜）、counterparty（某市交通卡公司/某火锅店/某电商平台）、description（乘车/美团收银/库迪订单）、金额分正常。
4. **幂等（去重）** ✅：重导同一支付宝账单 → 结果页「本次无新增交易」「跳过 234 笔」：**已存在 202 / 不计收支 20 / 交易关闭 8 / 解析失败 4**（202+20+8+4=234 全对上），externalId 去重生效、无重复入账。
5. **过滤** ✅：不计收支 20、交易关闭 8 正确跳过并计数（与解析器口径一致）。

> **与本清单原预估的差异（待查）**：实测支付宝为「成功 202 / 跳过 32（不计收支 20 + 交易关闭 8 + **解析失败 4**）」，非原预估「206 / 28」。差额在 **4 笔解析失败**——设备锁屏未能展开明细核对原因（疑似金额非法/时间缺失/单号缺失等数据边界）。明日展开「解析失败 4 笔（点击展开）」核对是否属正常过滤还是需修代码。
> **微信 XLSX**：设备上已有更早导入的微信数据（美团/滴滴出行/婷姐/礼金自动分类等，SourceBadge 正常），且 `WechatXlsxParserTest` 单测已覆盖日期序列/金额字面量/中性行过滤；本轮未单独重导微信账单，明日可对 `wechat.xlsx` 补一次端到端。

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
