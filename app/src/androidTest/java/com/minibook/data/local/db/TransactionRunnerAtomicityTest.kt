// 需 Android 设备/模拟器运行：./gradlew :app:connectedAndroidTest（暂不执行，待 Stage 9 设备验证）
package com.minibook.data.local.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 迁移事务原子性 instrumented 测试（TASK-009 / TASK-015，ARCHITECTURE §7.5）。
 *
 * 目标验证点（本期被阻塞，见类头 TODO(debt)）：
 * 1. `RoomDataSourceImpl.deleteById` 三表分发（transactions/debts/categories）整体原子：
 *    中途失败应回滚，不留悬空引用（TASK-015 迁移回滚）。
 * 2. `RoomTransactionRunner.runInTransaction`：block 内抛异常 → 整体回滚、异常上抛。
 *
 * TODO(debt)：**阻塞原因**——`RoomTransactionRunner` 与 `RoomDataSourceImpl` 均构造注入
 * `DatabaseGate`（SQLCipher 加密库门，`di/DatabaseModule` 装配），其构造依赖
 * `KeyManager`（AndroidKeyStore 生物识别/系统凭据）与真实（非内存）加密库文件；
 * 项目 androidTest 依赖集无 hilt-testing（禁改 toml），无法在无设备锁/无生物识别环境
 * 手动构造 DatabaseGate，强行构造会在 `open()` 时抛 SQLiteException（master key 缺失）。
 * 待 Stage 9 设备验证（或引入测试替身）后补全，见 docs/06_tasks.md TASK-009/015。
 *
 * 不依赖 Repository/TransactionRunner 的净额/月结余 SQL 口径已由
 * [NetByPartyDaoTest]/[MonthlySummaryDaoTest] 用 in-memory DB 覆盖。
 */
@RunWith(AndroidJUnit4::class)
class TransactionRunnerAtomicityTest {

    @Test
    fun `deleteById 三表分发原子性`() {
        // TODO(debt): 阻塞——RoomDataSourceImpl 依赖 DatabaseGate(SQLCipher/KeyManager)，
        // 无 hilt-testing 无法手动构造。计划：
        //   1. 构造三表各一条同 id 数据；
        //   2. 事务内 deleteById(id)；
        //   3. 断言三表该 id 均不存在；
        //   4. 事务内 block 中途抛异常 → 断言三表数据仍存在（回滚）。
    }

    @Test
    fun `runInTransaction 内抛异常整体回滚`() {
        // TODO(debt): 阻塞同上——RoomTransactionRunner 依赖 DatabaseGate。
        // 计划：runInTransaction { insert; throw RuntimeException } → 断言 insert 已回滚。
    }

    @Test
    fun `deleteByLedger 四表批删原子性`() {
        // TODO(debt): 阻塞同上。计划：四表各插 1 行同 ledgerId，事务内 deleteByLedger，
        // 断言四表该 ledgerId 均清空。
    }
}
