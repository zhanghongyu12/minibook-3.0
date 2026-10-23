package com.minibook.data.local.datasource

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-16i 门控 Flow 语义测试（2026-10-13 真机崩溃修复）。
 *
 * 背景：真机上重锁瞬间 `AppLockController.lock()` **先关库、后**翻 `isLocked`
 * （`AppLockController.kt:72-73`：`databaseGate.close()` → `_isLocked.value = true`），
 * 而 UI 生命周期恢复会让 `collectAsStateWithLifecycle` 立刻重启上游；旧实现在**调用点**
 * `ledgerDao() ?: error(...)` 同步抛 `IllegalStateException: DatabaseGate 未解锁/未打开…`
 * 到 `Dispatchers.Main.immediate` 上，直接把进程打崩（真机 crash buffer 有全栈）。
 *
 * 这里锁定三条语义，防回归：
 * 1. 门关闭（null）**不发射任何值**；
 * 2. 解锁后发射「当前库」的值；
 * 3. 重锁取消上游，再解锁切到**新库**（绝不继续用已关闭实例）。
 *
 * 收集用 [UnconfinedTestDispatcher]（挂起流测试的标准做法）：发射立刻内联恢复收集者，
 * 无需 `advanceUntilIdle` 的空转，断言即所见。
 */
class GatedFlowTest {

    @Test
    fun `门关闭时不发射任何值`() = runTest {
        val gate = MutableStateFlow<String?>(null)
        val collected = mutableListOf<Int>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            gate.gatedFlowBy { db -> flowOf(db.length) }.collect { collected += it }
        }

        assertEquals(emptyList<Int>(), collected)

        job.cancel()
    }

    @Test
    fun `解锁后发射当前库的值`() = runTest {
        val gate = MutableStateFlow<String?>(null)
        val collected = mutableListOf<Int>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            gate.gatedFlowBy { db -> flowOf(db.length) }.collect { collected += it }
        }

        assertEquals(emptyList<Int>(), collected)

        gate.value = "abcd"
        assertEquals(listOf(4), collected)

        job.cancel()
    }

    @Test
    fun `重锁取消上游 再解锁切到新库`() = runTest {
        val gate = MutableStateFlow<String?>(null)
        var upstreamActive = false
        val collected = mutableListOf<Int>()
        val flow = gate.gatedFlowBy { db ->
            flow {
                upstreamActive = true
                try {
                    emit(db.length)
                    awaitCancellation()
                } finally {
                    upstreamActive = false
                }
            }
        }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { collected += it }
        }

        assertEquals(emptyList<Int>(), collected)

        gate.value = "abc"
        assertTrue("解锁后应启动上游", upstreamActive)
        assertEquals(listOf(3), collected)

        gate.value = null
        assertFalse("重锁后上游应被取消", upstreamActive)
        assertEquals("重锁本身不发射", listOf(3), collected)

        gate.value = "abcdef"
        assertTrue("再解锁应重启上游", upstreamActive)
        assertEquals("再解锁应切到新库", listOf(3, 6), collected)

        job.cancel()
    }
}
