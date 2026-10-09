package com.minibook.domain.usecase.mail

import com.minibook.domain.model.MailImportStep
import com.minibook.domain.model.MailImportStepState
import com.minibook.domain.model.MailImportStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [MailImportStepTracker] 单测（纯 JVM，无 Android 依赖）。
 *
 * 核心回归：步骤状态**累积**——后续快照未提及的已完成步骤不得回退 PENDING
 * （真机实测缺陷：收取成功后 stepper 显示「○○○○✓」，与逐封明细全 ✅ 矛盾）。
 */
class MailImportStepTrackerTest {

    /** 按步骤取出列表中的状态，便于断言。 */
    private fun List<MailImportStepState>.of(step: MailImportStep): MailImportStepState =
        first { it.step == step }

    @Test
    fun `空累加器 apply 返回 5 步全 PENDING 且顺序按枚举序`() {
        val tracker = MailImportStepTracker()
        val steps = tracker.apply()

        assertEquals(5, steps.size)
        assertEquals(MailImportStep.entries.toList(), steps.map { it.step })
        steps.forEach { state ->
            assertEquals(MailImportStepStatus.PENDING, state.status)
            assertNull(state.summary)
        }
    }

    @Test
    fun `前三步 RUNNING 后其余步骤仍为 PENDING`() {
        val tracker = MailImportStepTracker()
        val steps = tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.RUNNING),
        )

        assertEquals(MailImportStepStatus.RUNNING, steps.of(MailImportStep.CONNECT).status)
        assertEquals(MailImportStepStatus.RUNNING, steps.of(MailImportStep.FETCH).status)
        assertEquals(MailImportStepStatus.RUNNING, steps.of(MailImportStep.FILTER).status)
        assertEquals(MailImportStepStatus.PENDING, steps.of(MailImportStep.PROCESS).status)
        assertEquals(MailImportStepStatus.PENDING, steps.of(MailImportStep.DONE).status)
    }

    @Test
    fun `前三步 DONE 后状态与 summary 保留`() {
        val tracker = MailImportStepTracker()
        tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.RUNNING),
        )
        val steps = tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.DONE, "已连接（IMAP/SSL）"),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.DONE, "窗口内共 12 封"),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.DONE, "命中 5 封（微信 3 / 支付宝 2）"),
        )

        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.CONNECT).status)
        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.FETCH).status)
        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.FILTER).status)
        assertEquals("已连接（IMAP/SSL）", steps.of(MailImportStep.CONNECT).summary)
        assertEquals("窗口内共 12 封", steps.of(MailImportStep.FETCH).summary)
        assertEquals("命中 5 封（微信 3 / 支付宝 2）", steps.of(MailImportStep.FILTER).summary)
    }

    @Test
    fun `PROCESS RUNNING 快照后前三步仍为 DONE 不回退`() {
        val tracker = MailImportStepTracker()
        tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.RUNNING),
        )
        tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.DONE, "已连接（IMAP/SSL）"),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.DONE, "窗口内共 12 封"),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.DONE, "命中 5 封（微信 3 / 支付宝 2）"),
        )
        // 关键回归：只传 PROCESS(RUNNING) 时，前三步不得被打回 PENDING。
        val steps = tracker.apply(
            MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.RUNNING),
        )

        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.CONNECT).status)
        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.FETCH).status)
        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.FILTER).status)
        assertEquals(MailImportStepStatus.RUNNING, steps.of(MailImportStep.PROCESS).status)
        assertEquals(MailImportStepStatus.PENDING, steps.of(MailImportStep.DONE).status)
        // 前三步 summary 也保留。
        assertEquals("已连接（IMAP/SSL）", steps.of(MailImportStep.CONNECT).summary)
        assertEquals("窗口内共 12 封", steps.of(MailImportStep.FETCH).summary)
        assertEquals("命中 5 封（微信 3 / 支付宝 2）", steps.of(MailImportStep.FILTER).summary)
    }

    @Test
    fun `PROCESS DONE 快照后前四步均为 DONE`() {
        val tracker = MailImportStepTracker()
        tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.DONE, "已连接（IMAP/SSL）"),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.DONE, "窗口内共 12 封"),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.DONE, "命中 5 封（微信 3 / 支付宝 2）"),
            MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.RUNNING),
        )
        val steps = tracker.apply(
            MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.DONE, "成功 5 封 / 失败 0 封"),
        )

        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.CONNECT).status)
        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.FETCH).status)
        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.FILTER).status)
        assertEquals(MailImportStepStatus.DONE, steps.of(MailImportStep.PROCESS).status)
        assertEquals("成功 5 封 / 失败 0 封", steps.of(MailImportStep.PROCESS).summary)
        assertEquals(MailImportStepStatus.PENDING, steps.of(MailImportStep.DONE).status)
    }

    @Test
    fun `DONE 快照后 5 步全 DONE —— 真机 ○○○○✓ 缺陷的回归用例`() {
        val tracker = MailImportStepTracker()
        // 复刻 processAccount 的完整快照序列。
        tracker.apply(MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.RUNNING))
        tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.RUNNING),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.RUNNING),
        )
        tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.DONE, "已连接（IMAP/SSL）"),
            MailImportStepState(MailImportStep.FETCH, MailImportStepStatus.DONE, "窗口内共 12 封"),
            MailImportStepState(MailImportStep.FILTER, MailImportStepStatus.DONE, "命中 5 封（微信 3 / 支付宝 2）"),
        )
        tracker.apply(MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.RUNNING))
        tracker.apply(MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.RUNNING))
        tracker.apply(
            MailImportStepState(MailImportStep.PROCESS, MailImportStepStatus.DONE, "成功 5 封 / 失败 0 封"),
        )
        // 最后一份快照只传 DONE(DONE) —— 修复前这里会把前四步全部打回 PENDING。
        val steps = tracker.apply(
            MailImportStepState(
                MailImportStep.DONE,
                MailImportStepStatus.DONE,
                "新导入 0 条 / 已存在 896 条",
            ),
        )

        MailImportStep.entries.forEach { step ->
            assertEquals("步骤 $step 应为 DONE", MailImportStepStatus.DONE, steps.of(step).status)
        }
        assertEquals("新导入 0 条 / 已存在 896 条", steps.of(MailImportStep.DONE).summary)
    }

    @Test
    fun `同一步骤重复 apply 取最新状态与 summary`() {
        val tracker = MailImportStepTracker()
        val first = tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.RUNNING),
        )
        assertNull(first.of(MailImportStep.CONNECT).summary)
        assertEquals(MailImportStepStatus.RUNNING, first.of(MailImportStep.CONNECT).status)

        val second = tracker.apply(
            MailImportStepState(MailImportStep.CONNECT, MailImportStepStatus.DONE, "已连接（IMAP/SSL）"),
        )
        assertEquals(MailImportStepStatus.DONE, second.of(MailImportStep.CONNECT).status)
        assertEquals("已连接（IMAP/SSL）", second.of(MailImportStep.CONNECT).summary)
    }
}
