# MiniBook Android — 发布说明

> 状态：已确认
> 最后更新：2026-10-21（真机换版安装与启动验证：真机由 TASK-084 的包 `2FD3869…` 原地覆盖为 TASK-087 的构建 `A6DD265B…`，含 F-16i / F-16k / F-16l；装前 / 装后数据逐字节一致、启动无崩溃 —— 见 `09_test_report.md` 顶部章节。上一轮 2026-10-13：TASK-084 docs/15 七条整改 + 即时需求；F-16i 解密门门控 Flow；F-16j 应用锁前置分诊；F-16k 首页账号入口收成标题旁小链接；F-16l 礼金/红包图标去重）
> 维护者：Coordinator

## 未发布：F-15 云端同步（本地↔云端，2026-10-10 功能完成，**尚未发版**）

第三个功能块，**尚未发版**（未构建签名 release 包；debug 包已装机验证——真机 2026-10-21 由 TASK-084 的包换成含 F-16i / F-16k / F-16l 的构建，仅做安装与启动验证、**未做发布验收** ⇒ 发布前请读完本节末尾清单）。

### 交付内容

| 编号 | 功能 | 说明 |
|---|---|---|
| F-15a | **Python API 服务**（新目录 `server/`） | FastAPI + SQLAlchemy 2 + Alembic + PyMySQL。`alembic upgrade head` 建表：`0001_init`（用户系统 + 同步表）+ `0002_typed_entities`（云端改 **6 张类型化业务表 + `sync_log`**，旧 JSON 记录自动拆列回填）。生产 = MySQL 8（InnoDB / `utf8mb4_unicode_ci` / `DATETIME(6)`），开发与单测 = SQLite（双 DSN 同套用例） |
| F-15b | **用户系统** | 用户名 + 口令（argon2id）→ JWT access（30 分钟）+ 刷新令牌轮换（旧令牌再用即拒）；**数据按 `user_id` 硬隔离**（越权读写有专项测试）；登录/注册限流；服务端日志**不记 payload** |
| F-15c | **手动三模式同步** | 设置 → 数据 → **云同步**：双向 / 仅上传 / 仅下载；推送前**预览确认**（四计数 + 危险动作二次确认）；冲突 = 三路 base 比较 + LWW，输家**留档**在 `sync_conflicts`（绝不静默丢）；删除走墓碑；整批校验失败**整批回滚**；游标**只在 pull 成功后**推进 |
| F-15d | **本地不丢的五道保险** | ① 同步前本地快照；② 云端按 payload 原样落地，不改本地主键/内容；③ 冲突留档；④ 幂等（同 `updated_at` = no-op 且不涨游标）；⑤ 网络/中断失败不动游标、不动本地 |
| F-15e | **云端可查账** | 业务字段落在**真实列**上（不再塞进 JSON）：`SELECT date, SUM(amount) FROM transactions GROUP BY date` 直接出账（真 MySQL 实测 88.88 元） |
| F-15f | **账号登录 + 自动云同步**（DEC-038，2026-10-12） | 设置首页新增一级「**账号**」行（未登录 / 已登录两态）直达账号与同步页；新增**自动同步**开关（默认开）：**打开 / 回到 App 时静默双向同步一次**（≥5 分钟节流）。**会删本地或有冲突的计划不自动执行**（留给手动确认），失败不打扰；手动三模式**全部保留**。登录失败改为**可见**：落盘后读回验证，Keystore 失败重试一次并给出可读原因（杜绝「服务端 200 但本地没登录、界面无提示」） |
| F-15g | **导入后当前账本自动归位**（TASK-072，2026-10-12） | 修掉一个**会让用户误判「导入失败」**的坑：导入会重建账本并重新生成 id，旧的「当前账本」指针随即悬空 ⇒ 首页显示 **¥0.00 / 暂无交易记录**（数据其实已全部落库）。现改为**事务成功后**把当前账本写回导入后的默认账本；回滚时指针不动。实测 `pm clear` 后重新导入手机备份，首页**直接**显示 `2026年10月结余 ¥-2,474.02` |
| F-15h | **「账号」页：登录 / 改资料 / 改口令 / 退出 / 注销**（DEC-039 / TASK-073，2026-10-12） | 设置首页「账号」点进去，二级页标题就是「**账号**」；页内给用户信息（显示名 / 用户名 / 注册时间）与四类操作：**改显示名**（留空即清除）、**改口令**（需原口令，成功后服务端撤销全部旧刷新令牌）、**退出登录**、**注销账号**（口令二次确认）。**注销与退出只删云端账号，本机数据一条不删**（界面写明）。云同步页只留「数据怎么同步」，未登录时给一行直达账号页 |
| F-15i | **服务地址不再暴露给用户**（DEC-039 / TASK-073，2026-10-12） | 服务器地址改为按构建类型内置（`BuildConfig.SYNC_BASE_URL`）：正式版界面**既无输入也无展示**；仅调试版保留输入框供开发临时改指向。内置地址缺失时明确提示「本安装包未内置服务器地址」，不静默失败 |
| F-15j | **修复「登录」报 NetworkOnMainThreadException**（2026-10-12） | `SyncApi.callRaw` 读响应体原本在 IO 上下文之外：小响应侥幸进缓冲区、大响应在调用方（主）线程真读 socket ⇒ 账号页点「登录」失败（自动同步没踩到，因为整段在 IO）。已把 `execute()` 与读 body 一起收进 `withContext(ioDispatcher)`，并在调试版打印异常栈便于定位 |
| F-15k | **修复「注册失败」提示不准**（2026-10-12） | 服务端要求口令 ≥ 8 位、用户名 3–32 位且限特定字符，但客户端原先不本地校验、服务端 422 又复用同步文案 ⇒ 6 位口令注册只显示「数据校验失败，未写入云端」。现在：本地前置校验 + 表单常驻规则提示 + 认证类 422 说清规则，不合规连请求都不发 |
| F-15l | **云同步入口去重**（TASK-074，2026-10-12） | 用户指出「账号页有云同步、数据页也有」：按「只保留一个」去掉账号页那一行，入口只留**设置 → 数据 → 云同步**；账号页专心做「账号自己」 |
| F-15m | **登录 / 注册各自独立成页**（TASK-074，2026-10-12） | 账号页里原先的内嵌表单拆成两个独立整页（路由 `settings-login` / `settings-register`）：应用图标 + 大标题 + 满宽主按钮 + **页面内错误文案**（不再是一闪而过的 toast）+ 互相切换的文字按钮；注册页带「确认口令」，注册成功即自动登录 |
| F-15n | **「注册时间」显示错误修复 + 资料扩充到 4 项**（TASK-074，2026-10-12） | 服务端 `created_at` 原先是**不带时区**的 UTC 串 ⇒ 客户端解析失败、把 UTC 原样显示（早 8 小时）。现在服务端统一输出带 `Z` 的 UTC、客户端再兜一层兜底解析；账号页新增 邮箱 / 个性签名 / 地区（迁移 `0004_user_profile_fields`）与只读的「上次登录」「账号 ID」，并新增「编辑资料」页（**只提交改动过的字段**，改一项不会清掉另一项） |

| F-15o | **修复「点注册新账号」App 闪退**（TASK-074，2026-10-12） | 模拟器实测抓到：新登录 / 注册页的应用图标用了 `painterResource(R.mipmap.ic_launcher)`，而该资源是**自适应图标 XML**（`mipmap-anydpi-v26/ic_launcher.xml`），Compose 只支持 VectorDrawable 与位图 ⇒ 抛 `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported ex. PNG, JPG, WEBP` 并当场结束 Activity。**编译绿、JVM 单测全绿都抓不到，只有真机 / 模拟器点得出来**。已改用矢量 `R.drawable.ic_launcher_foreground` + 圆角底衬。同类隐患：`SmsNotificationHelper` 的通知小图标仍是 `R.mipmap.ic_launcher`（既有代码，未改） |

| F-15p | **登录 / 注册页补齐平台规范交互**（TASK-075，2026-10-12） | 口令显示/隐藏改为输入框右侧眼睛图标（trailing）；用户名 / 口令 / 注册字段带 autofill 语义（`ContentType.Username/Password/NewUsername/NewPassword`）；键盘关闭首字母大写与自动纠错，`imeAction` 串联「下一步 → 完成」，最后一项回车即提交；表单错误落到字段级（`isError` + `supportingText`）并用 `LiveRegion` 供读屏播报；注册页「两次口令不一致」挂到确认口令字段；提交中禁用全部输入；返回键换矢量箭头（带 `contentDescription`） |

| F-15q | **视觉现代化：主题 token + 自带矢量图标 + 面板分层**（TASK-078 / TASK-079，2026-10-12） | 配色换成青绿「清爽现代」基线并补齐 M3 分层表面族（`surfaceContainerLowest…Highest`，此前全仓库 0 命中）；主题色按背景亮度算 `on*` 色修正对比度；形状 6/12/16/20/28dp；新增等宽数字金额样式 `MoneyLarge/MoneyMedium`；新增 `MiniBookIcons`（18 个自带矢量图标，路径取自 Material Icons / Apache-2.0）替换底部导航 emoji、🔍、✏️/🗑、返回 ←、月切换 ‹›、FAB ＋、快捷入口图标；`surfaceVariant+alpha` 面板与显式 `surface` 卡片改 `surfaceContainerLow/High/Highest`（Chip / 进度轨道等语义用色保留）；语义色不随动态取色漂移，`dynamicColor=true` 仍默认 |

| F-15s | **隐私政策 / 用户协议 + 注册页协议勾选**（TASK-076 部分完成，2026-10-12） | 两份法务正文写成 `res/raw` 纯文本**单一副本**（隐私 11 节 / 协议 10 节），App 内新增「设置 → 关于 → 隐私政策 / 用户协议」两页（2 跳可达）；注册页新增**默认不勾选**的协议勾选框，未勾选时本地拦下并直说原因；登录页只放「登录即表示同意」+ 可点链接（不放勾选框）。**运营主体与联系方式按用户要求暂不声明**，列为上架前必补 |

| F-15r | **首页结余卡「较上月」环比**（TASK-081，2026-10-12） | 结余金额下方一行「较上月 +¥X / ¥-X / 与上月持平 / 上月无记录」：**只给可核对的绝对差额，不给百分比**（上月为 0 时百分比无定义）；上月收支全 0 视为无记录；涨跌符号 + 语义色双编码 |

| F-15w | **账本图标换成自带矢量图标集**（2026-10-13） | 按用户反馈「账本的图标也换一下」：新建 `ui/components/LedgerIcons.kt`（16 个稳定 key `book` … `car`，路径同取 Material Symbols Rounded，Iconify 内联自带矢量）；渲染统一走新增 `LedgerGlyph`（key / 历史 emoji → 矢量并按色 `tint` 着色，未知串 → 文本兜底，**绝不空白**）。账本编辑器「emoji 快捷行 + 自定义 Emoji 输入框」换成 **6 列图标网格**（选中项主题描边 + `primaryContainer` 底色）；新建账本默认预选 `book`，默认账本与导入回退图标由 `📒` 改为 `book`；锁屏 LOGO 同步换矢量。**不做数据迁移**：老 emoji 由 `LEGACY_EMOJI`（16 条）兜底映射回同批矢量。`LedgerIconsTest` 3 例在 JVM 侧逐个解析全部路径 |

| F-15x | **预算：总额可删除 / 清空 + 分类预算删除后立即刷新**（2026-10-13） | 按用户反馈「预算填写了不能删除或者清空」「分类预算删除后不立马刷新，返回再进来才会删除」：①总预算编辑弹层在**已设过总额**时给出「删除月度总预算」danger 入口（新增 `ClearTotalBudgetUseCase` + `BudgetsViewModel.clearTotalBudget`），强确认文案说明「已记录的交易与分类预算不受影响」，清空后回到空态引导；②**根因修复**——分类预算删除原先走裸 `writableDatabase().delete("budgets", …)`，绕过 Room 失效通知 ⇒ 预算进度 Flow 不重发、必须退出页面（`WhileSubscribed(5s)` 超时重连）才刷新（这也解释了为何「保存」会刷新而「删除」不会：保存走 DAO `upsert`）。改走新增的 `BudgetDao.deleteCategoryBudget`（`@Query` DAO 删除，写入与失效通知同一路径闭合），删完列表立即刷新 |

| F-15y | **账号页：显示名改称「昵称」+ 不再展示账号 ID**（2026-10-13） | 按用户反馈「显示名改一下，叫昵称」「账号ID不要展示」：账号页与编辑资料页的「显示名」全部改称「**昵称**」（登录页辅助说明同步写明「登录用的账号名（不是昵称）」，避免与昵称混淆）；已登录用户信息由 8 项收为 **7 项**（昵称 / 用户名 / 邮箱 / 个性签名 / 地区 / 注册时间 / 上次登录），**不再展示账号 ID**（`UiState.userId` 保留供内部使用） |

| F-16l | **礼金 / 红包图标去重**（2026-10-13） | 按用户反馈「分类中收入的礼金和红包的图标重复了」：收入「礼金」与「红包」此前共用同一张图形（内置 `icon = "redpacket"`，历史 🧧 也映射到它；`Gift` 与 `RedPacket` 两条矢量的路径都是「盒 + 蝴蝶结」，人眼难分）。修法：新增 `CategoryIcons.GIFT_MONEY = "giftmoney"`（Material Symbols `volunteer_activism` 手捧心矢量）；`resolve(raw, name)` 支持**按名覆盖**——名为「礼金」且旧解析得到 `RedPacket`/`Gift` 时改渲染 `GiftMoney`，**不动用户既有数据**；`CategoryGlyph` 新增 `name`，9 个调用点中持有 `Category` 对象的都传入（图标选择器刻意不传，保持「所见即所选」）；内置模板礼金 `icon` 改 `"giftmoney"`；`pickerKeys` 24 → **25**（红包矢量保留在可选列表末尾）；`CategoryIconsTest` +2 例 |
| F-16k | **首页账号入口收成「首页」标题旁的小链接**（2026-10-13） | 按用户反馈「首页账号设置不用那么大 在首页两个字后面加个"未登录""昵称"作为链接就可以了」：撤掉首页 `LazyColumn` 首项大卡 `HomeAccountRow`（36dp 圆底头像 + 标题 + 副行 + 行尾箭头/「登录」），改为 `HomeTopBar` 中「首页」标题**紧随其后的可点小字**（未登录显示「未登录」、已登录显示昵称，`labelLarge` + `primary`、`clickable(role = Button)` + `defaultMinSize(minHeight = 44.dp)`、行尾省略号），点击仍进账号设置页；`HomeDashboardContent` 随之不再需要 `accountName`/`onOpenAccount`。模拟器 dump：同一行 `首页 [63,179]` + `未登录 [203,194]` + `默认账本 [810,189]` |
| F-16j | **应用锁前置分诊（修复未录入生物识别时开启应用锁崩溃）**（2026-10-13） | 模拟器实测：设了系统 PIN、未录入指纹/人脸时点「应用锁」开关 ⇒ `InvalidAlgorithmParameterException: At least one biometric must be enrolled to create keys requiring user authentication for every use`（Android 13+ Keystore 对 auth-gated key 的硬要求）在主线程抛出致崩（PID 4040）。修法：`BiometricHelper.hasEnrolledBiometric()`（`BIOMETRIC_STRONG`/`WEAK`，**不含 `DEVICE_CREDENTIAL`**）+ `SettingsViewModel.setAppLockEnabled` 开启前分诊（发 `needBiometric`，不启用不降级）+ 安全页生物识别引导卡 + `AppLockController` 四处 cipher 创建改 `runCatching` 非致命回调。复测：不崩溃、显示引导卡、应用锁仍关闭、`logcat -b crash` 0 行 |
| F-16i | **解密门门控 Flow（修复重锁瞬间主线程崩溃）**（2026-10-13） | 真机 crash buffer 实录 `IllegalStateException: DatabaseGate 未解锁/未打开，无法提供 LedgerDao`：`AppLockController.lock()` **先关库后翻锁状态**，UI 生命周期恢复重启上游 Flow 时旧实现于调用点 `error(...)` ⇒ 主线程崩溃。修法：`DatabaseProvider.openState`（`DatabaseGate` 以 `MutableStateFlow` 覆写）+ 新增 `GatedFlow.kt`（`openState.flatMapLatest { if (it == null) emptyFlow() else block(it) }`，**不能 `filterNotNull()`**，否则重锁不取消旧库流）+ `RoomDataSourceImpl` 七个 `observe*` 与适配器 `observeAll` 全部门控；**一次性 `suspend` 查询保持硬失败**（不静默返空）；新增 `GatedFlowTest` 3 例 |
| F-16h | **内置商户词典**（docs/15 第 7 条，2026-10-13） | 按用户定案「联网搜一波大的当内置，学习留在 App 本地」：`res/raw/merchant_dictionary.json` **1315 条**（70,105 字节，只映射 15 个内置支出分类名）；`MerchantDictionary` 接口 + `BuiltInMerchantDictionary`（先相等后包含，复用归一化）+ `MerchantDictionaryStore`（`@Singleton` 双检加载，失败静默）+ Hilt 绑定；`ImportBillUseCase` 第 1.5 步在 `allowWeakRuleMatch` 路径查词典（**L2 不查**，防静默污染）；优先级 = 本地学习 > 内置词典 > 内置类型映射 > 其他 |

| F-16g | **日期区间联动约束**（docs/15 第 5 条，2026-10-13） | `OptionalDateField` 与交易页 `DateFilterChip` 新增 `minDateMillis` / `maxDateMillis`，经 M3 `SelectableDates` 把不可选日期在选择器内**置灰**（选不出「止 < 起」的反向区间），而不是选完再报错；交易筛选与搜索条件两处调用点互相传递起 / 止 |

| F-16f | **退款记为「收入」+ 单独「退款」分类**（docs/15 第 6 条，2026-10-13） | 根因：「退款」原本在 `SmsBillParser` 的支出关键词里 ⇒ 退款短信被记成支出。改为入收入关键词并**优先**判收入，`platformCategory = "退款"`；`BillCategoryMapping` 补短信侧「退款」收入分类。按用户拍板不做支付/退款配对、不红冲、不动数据模型 |

| F-16e | **贷款 / 授信营销短信过滤**（docs/15 第 4 条，2026-10-13） | 用户给的两条真实银行原文（各 398000 元授信）原被**静默**记成一收一支。修复：方向判定前加三层预筛（回复指令特征 / 授信语义无强交易锚点 / 账单准入硬门槛），「利息」「消费」降级为需交易锚点语境；带「尾号+扣款」的真实交易即使带「退订回T」也不误杀（专门用例守住）；已入账脏数据按用户决定**不清理** |

| F-16d | **导入前主动申请短信权限**（docs/15 第 3 条，2026-10-13） | `ImportScreen` 新增 `smsPermissions()`（`READ_SMS`+`RECEIVE_SMS`，Android 13+ 加 `POST_NOTIFICATIONS`）与权限 launcher；两个导入入口未授权时先申请，授权后按**同一 mode** 继续扫；引导按钮改「授予权限并继续」+ 系统设置兜底文案 |

| F-16c | **导入来源清单（勾选要导入的发送方）**（docs/15 第 2 条，2026-10-13） | 用户「多家银行只导入指定的一家」，但 106 端口复用、发送方无法预枚举 ⇒ **从扫描结果反推**：`SmsWhitelistEntry.importEnabled` + `SmsSenderSummary` + `SmsScanPreview.senders`（codec 5 段、旧 4 段自动兼容默认允许）；`SmsSenderNamer` 猜机构名（猜不出显示号码，绝不硬写）；`scan()` 跳过未勾选来源并计入「非账单」（不删任何已入账交易），只登记出过账单的来源；导入页「导入来源」摘要 + 弹框逐源勾选（**默认全选**防漏）；设置 → 短信白名单每源两行开关（自动记账 / 允许导入） |

| F-16b | **分类规则归一化核心词**（docs/15 第 1 条，2026-10-13） | 新建 `CategoryRuleNormalizer`（剥括号 → 全角转半角/去空白/小写 → 长词在前的后缀表【刻意不剥裸「店/市/区/县/中心/广场」】→ 去首尾标点；强度 `STRONG` = 原串包含 / 归一化相等 / 核心词 ≥3 字被包含，`WEAK` = 公共前缀 ≥2 字）；学习侧改存**核心词**（旧规则不迁移、原串包含仍 STRONG 不退化）；`ImportBillUseCase` 按 STRONG → 核心词更长 → 更新时间新排序；**L2 只认 STRONG**，WEAK 仅限用户看得见的预览 / 待确认路径。算法与应用口径落在 `docs/19_category_rule_normalization.md` |

| F-16a | **页面顶部留白修复 + 首页账号信息行**（用户即时需求，2026-10-13） | 根因：`MainScreen` 的 `Scaffold` 无 `topBar` 却默认吃 `systemBars` inset，与内层各页 `Scaffold` 叠加成约两倍状态栏高度 ⇒ `contentWindowInsets = WindowInsets(0,0,0,0)`；首页 `LazyColumn` 首个 item 新增 `HomeAccountRow`：未登录显示「未登录 + 登录」链接、已登录显示昵称 +「账号设置 ›」，整行可点跳账号页（`HomeNavigation.openAccount()` → `settings-account`）。昵称来自 `SyncAuthStore` 新增的 `KEY_NICKNAME` / `updateNickname()` / `displayName`（`GET /me` 与保存资料成功后回写缓存），`HomeViewModel.accountDisplay` 暴露给首页 |

| F-15v | **内置分类图标换成自带矢量图标集**（TASK-082，2026-10-12） | 24 条内置分类的 `icon` 从 emoji 换成 `CategoryIcons` 的稳定 key（`"food"` … `"cash"`，路径取自 Material Symbols Rounded，Iconify 抓取后内联为自带矢量）；渲染统一走新增 `CategoryGlyph`：key → 矢量并按分类色 `tint` 着色，未知串 / 历史 emoji → 文本兜底（`LEGACY_EMOJI` 把 24 个旧 emoji 映射回同批矢量，**不做数据迁移**）。9 处渲染点全部改走它（分类格 / 交易列表行 / 记账下拉 / 交易详情 / 预算卡 / 预算编辑 / 搜索筛选）；分类编辑器「手输 Emoji」换成 **6×4 图标网格**。`CategoryIconsTest` 3 例在 JVM 侧逐个解析全部路径兜底（F-15o 教训：编译绿也可能运行时崩） |

| F-15u | **未登录态收为登录按钮 + 设置页「账号」提第一行**（TASK-082，2026-10-12） | 按用户反馈撤销上一版资料头卡：未登录的账号页只留满宽「**登录**」主按钮 + 「还没有账号？注册新账号」文字链（`GuestLoginButtons`）；设置页「账号」行移到**第一位**（在「账本」之前），因为它是身份 / 跨设备入口最可能的行动点 |

| ~~F-15t~~ | ~~**账号页「未登录」改为资料头卡**（TASK-082，2026-10-12）~~ | **已被用户否决**（「用户未登录时候不要展示一个大框，做成一个让用户登录的按钮即可」），实际交付见 F-15u |

### 构建与运行

- 服务端：见 `server/README.md`（`.venv` → 装依赖 → `alembic upgrade head` → `uvicorn app.main:app --host 0.0.0.0 --port 8000`）
- 客户端：Gradle 命令与 v1.1.0 相同；服务地址在「设置 → 数据 → 云同步」填写（**仅 debug 包允许明文 HTTP**，release 强制 HTTPS）
- 测试口径（**2026-10-13 实跑**）：服务端 `pytest` 双 DSN 各 **73 例 / 0 失败**（新增 12 例：昵称 / 改口令 / 注销 7 例 + 时间口径与资料字段 5 例）；
  Android `:app:testDebugUnitTest` **571 例 / 0 失败 / 1 跳过**（本轮 TASK-087 / F-16k+F-16l 新增 `CategoryIconsTest` 2 例【礼金与红包解析结果不同、名为「礼金」的旧分类换 `GiftMoney` 且其它分类不受影响】；TASK-085/F-16i 新增 `GatedFlowTest` 3 例；TASK-084 新增 39 例：分类规则归一化 `CategoryRuleNormalizerTest` 13 例 + 短信解析 `SmsBillParserTest` 10 例【含用户两条真实贷款营销原文、退款记收入、真实交易带「退订回T」不误杀】+ 导入来源与内置词典 16 例【`SmsWhitelistStoreCodecTest`、`SmsSenderNamerTest`、`SmsSenderSummaryBuilderTest`、`MerchantDictionaryTest`、词典文件校验、`SmsBillImportUseCase` 来源过滤】；上一轮新增 6 例：`LedgerIconsTest` 3 例【16 个矢量路径逐个可解析、key 唯一、旧 emoji 兜底齐全】+ `ClearTotalBudgetUseCaseTest` 1 例 + `ClearCategoryBudgetUseCaseTest` 2 例；上轮 521 例：`CategoryIconsTest` 3 例【24 个矢量路径逐个可解析、key 唯一、旧 emoji 兜底齐全】+ 518 例：Hero 卡环比 8 例【`NetBalanceTrendTest` 5 + `HomeViewModelTrendTest` 3】+ 协议勾选文案 1 例 + 法务文本守卫 `LegalTextTest` 3 例 + 账号页纯逻辑 6 例 + 账号表单校验 4 例 + 时间格式化与资料校验 5 例 + `MiniBookIconsTest` 2 例；唯一跳过项为 `SyncApiE2ETest`（需指向真实服务端，按既有条件跳过））；
  模拟器（Pixel_7）`:app:connectedDebugAndroidTest` **21 例 / 0 失败 / 0 跳过**（含 `MigrationV3To4Test`、设备端 A→云→B 端到端、
  真 Keystore 会话落盘，以及**新增的启动冒烟 `MainActivityStartupTest`**——此前 20 例全绿却漏掉「App 启动即崩」，已补上）

### 发布前必须完成（**未完成，不得当成已交付**）

- [ ] **真机 `A1B2C3D4` 验收**：2026-10-10 复查时**真机未连接**，该项无法执行。真机装的是 debug 签名版，安装/覆盖须按 v1.1.0 的签名边界处理（先「备份导出」再动）
- [ ] **部署与加固**：HTTPS（Let's Encrypt）+ Nginx 反代 + 数据库不裸露公网 + 备份加密；release 包只允许连 HTTPS
- [ ] 签名 release 包构建与冒烟（本轮只跑过 debug 包）

### 已知限制

- 云端 `sync_log` **只增不删**（无归档/裁剪）：正确裁剪需要按设备记录「最慢游标」，否则会让落后设备的游标错位、漏拉或重复——本轮刻意不做，登记为二期。
- 并发插入**同一条尚不存在**的实体时，后到者可能命中主键冲突并收到一次 `server_error`：重试即幂等通过（个人手动同步场景概率极低，二期改 upsert）。
- 登录限流是单进程内存实现，多实例部署需换 Redis。
- Android Keystore 的失效/换机恢复路径只能用真机验证（JVM 侧用假实现替代）。
- **模拟器无外网时**首次生成 Keystore 密钥可能触发系统「远程密钥供给」（RKP）失败：客户端已改为**重试一次并给出可读原因**（不再静默失败）；真机有网，不受影响，但仍待真机复核。
- **自动同步只在 App 前台触发**（`MainActivity.onStart`）：关掉 App 不会后台同步（本轮刻意不引入 WorkManager 定时任务，理由见 DEC-038）。

## v1.1.0（F-14 短信账单导入 + 信息架构改版，2026-10-10）

第二个正式版本，**首个签名 release**（签名 key：`keystore/minibook-release.jks`，
口令在 `keystore.properties`；两者均已 gitignore，不入库）。

### 新增功能

| 编号 | 功能 | 说明 |
|---|---|---|
| F-14 | **短信账单导入** | 三层兜底：① 到达即记账（`SMS_RECEIVED` → L1 解析 / L2 白名单自动记账，四条件 + 应用锁门控）→ ② 打开 App 静默补扫（L3，兜广播丢失）→ ③ 手动导入（L0，全量干跑 + 确认写入，最终兜底）。手动页提供**两种扫描方式**：**增量**（只读上次扫描节点之后的新短信；**节点 = 本次扫描的墙钟时间**，读完推进 ⇒ 看过的不再出现）/ **全量**（全部历史，不动节点 ⇒ 历史永远可重见，含「▸ 此前已看过 N 条」折叠区）。未识别条目只折叠不删除；白名单按发送方逐条开关。**不申请默认短信应用角色、不加额外权限、不弹逐步提醒**（DEC-024 / 026 / 027 / 028 修订 / 029 / 030）。 |
| F-13 | 邮件账单自动导入 | 每种账单只取最新一封；IMAP 凭据加密存储（`12_mail_bill_import.md`）。 |
| 改版 | 导入页三入口 + 设置分组汇总 / 二级页 | 导入页 = 账单文件 / 邮箱账单 / 短信账单三个入口卡，逐层返回；设置页 = 一张 5 行汇总卡（账本 / 外观 / 安全 / 数据 / 短信账单）+ 4 个二级页（DEC-029 / TASK-059）。 |

### 构建与产物

- `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`（debug 签名）
- `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`（**已签名**；`keystore.properties` 缺失时退回 `app-release-unsigned.apk`）
- `./gradlew :app:testDebugUnitTest` → JVM 单元测试 **52 类 / 410 例 / 0 失败 / 0 跳过**
- `versionName 1.1.0` / `versionCode 2` / `applicationId com.minibook`

### 安装与签名边界（重要）

- 正式签名 key（SHA-256 `E137B7…`）与 debug key（`D44930…`）**不是同一把**。真机 `A1B2C3D4` 上长期安装的是
  **debug 签名**版本，release 包**不能直接覆盖安装**（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），必须卸载
  ⇒ 会清掉本机账本数据。要迁移到正式签名版：先在「设置 → 数据 → 备份导出」导出备份，卸载后安装 release 包，
  再「导入备份」。
- 本版真机（`A1B2C3D4`）安装的是 **debug 包**（版本号同为 `1.1.0`），账本数据不受影响；GitHub Release 附件为
  **签名 release 包**。

### 已知限制

- release **未开启 R8**（`isMinifyEnabled = false`，与上一版一致）：避免影响已验证行为；如需开启须重跑真机验收。
- MIUI/HyperOS 对非默认短信应用暴露 `sms_restricted` 视图（App 可见条数少于 shell）——ROM 级边界，见 DEC-027。
- instrumented 测试（`connectedAndroidTest`）仍未纳入常规执行（3 个既有用例）。

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
