# 测试报告

> 状态：已确认
> 最后更新：2026-08-21
> 维护者：Tester

## 最新测试

### 测试信息

| 项目 | 内容 |
|------|------|
| 测试日期 | 2026-08-20 |
| 测试人 | Tester（Tester） |
| 测试范围 | Stage 7 阶段测试——领域模型枚举往返（5 枚举 fromValue/非法与空返回 null/常量对齐 DATABASE §4/数量守卫）、数据映射 Entity↔Domain 往返（Transaction/Debt/Category/Ledger/Budget，含 syncStatus=CONFLICT 等边界）、util 工具（TimeFormatter、BackupJsonMapper：BackupV1 往返 + DEC-005/DEC-004 + 导入恒写 local）、DAO 净额口径（PRD F-02）与月结余 SQL（instrumented，未在设备执行）；对应 TASK-002/003/006/008/010/014/015/016 需测试覆盖点 |
| 代码版本 | 测试提交 `1d09ae1`（Tester，2026-08-20）；被测代码父提交 `b4aa338`（fix(security) 修复 DEC-002 双钥系统）；仓库 HEAD `ddd3ee8` |

### 测试结果

| 指标 | 数值 |
|------|------|
| 用例总数 | 58（单元 56 + 仪器测 2 套，编译通过未在设备执行） |
| 通过 | 56（单元测试全绿，`./gradlew :app:testDebugUnitTest`） |
| 失败 | 0 |
| 跳过 | 仪器测 2 套（NetByPartyDaoTest 7 例 + MonthlySummaryDaoTest 3 例，共 10 个 `@Test` 方法，未在设备执行） |
| 覆盖率 | N/A（未采集；instrumented 未执行，无覆盖数据） |

> 口径说明：
> - **单元测试 56 例 = Tester 4 文件 40 例**（EnumRoundTripTest 12 / MapperRoundTripTest 9 / TimeFormatterTest 6 / BackupJsonMapperTest 13）**+ Developer 存量单测 16 例**（MoneyFormatterTest 10 / ValidatorsTest 6），全绿。
> - **仪器测「2 套」**按 Stage 7 提交口径指 2 个 DAO 仪器测套件（NetByPartyDaoTest、MonthlySummaryDaoTest，共 10 个 `@Test` 方法，基于 Room in-memory DB），`compileDebugAndroidTestKotlin` 编译通过但未在设备/模拟器执行。
> - **TransactionRunnerAtomicityTest**（迁移事务原子性，3 个方法）因 `RoomTransactionRunner`/`RoomDataSourceImpl` 构造依赖 `DatabaseGate`（SQLCipher/KeyManager，androidTest 依赖集无 hilt-testing 无法手动构造）**降级为 TODO 骨架**（无实际断言），不计入有效用例。

### 失败用例详情

（无——单元测试 56 例全绿，无失败用例。）

需特别说明（属已知限制，非失败）：

1. **2 个 DAO 仪器测未在设备执行**：NetByPartyDaoTest（净额口径，7 例）与 MonthlySummaryDaoTest（月结余，3 例）已编写、编译通过，但未在设备/模拟器上运行，结果未经实测确认，待设备验证（见 `10_release_notes.md`「已知限制 / 待办」）。
2. **TransactionRunnerAtomicityTest 降级为 TODO 骨架**：迁移事务原子性（deleteById 三表分发 / runInTransaction 回滚 / deleteByLedger 四表批删）验证点被阻塞——androidTest 依赖集无 hilt-testing（禁改 toml），无法手动构造依赖 DatabaseGate（SQLCipher/KeyManager）的 `RoomTransactionRunner`/`RoomDataSourceImpl`；已用注释记录阻塞原因与后续计划，待 Stage 9 设备验证或引入测试替身后补全。

### 结论

- [x] 通过
- [ ] 不通过

结论说明：单元测试 56 例全绿，Tester 阶段测试通过；2 个 DAO 仪器测用例编译通过但未在设备执行、迁移事务原子性用例为 TODO 骨架，二者为已知限制，需设备验证后补测。

---

## 历史测试记录

### Stage 7（2026-08-20）— 首轮阶段测试

- 提交：`1d09ae1`（Tester，7 个测试文件，+939 行）
- 范围：领域模型枚举 / 数据映射往返 / util 工具 / DAO 净额与月结余（instrumented）
- 结果：单元 56 例全绿；仪器测 2 套（10 例）编译通过未执行；迁移事务原子性为 TODO 骨架
- 状态：**已确认**（含已知限制，待设备验证后补测 instrumented 项）
