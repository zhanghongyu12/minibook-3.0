package com.minibook.domain.usecase.mail

import com.minibook.domain.model.MailImportStep
import com.minibook.domain.model.MailImportStepState
import com.minibook.domain.model.MailImportStepStatus

/**
 * 「一键收取」步骤状态累加器（F-13）：把「每份快照重置全部步骤」改为「**状态累积**」。
 *
 * 某个步骤一旦被 [apply] 发出过状态，后续快照里未提及它时**保持上次状态**（不回退
 * PENDING）；只有从未出现过的步骤才是 PENDING。顺序始终按
 * [MailImportStep.entries]（CONNECT→FETCH→FILTER→PROCESS→DONE）。
 *
 * 修复背景：此前每份快照把未列出的步骤一律补 PENDING，导致收取成功后 stepper 显示
 * 「○○○○✓」——已完成的 CONNECT/FETCH/FILTER/PROCESS 被打回待执行，与逐封明细
 * 全 ✅ 自相矛盾。累加后同一份快照序列会正确显示「✓✓✓✓✓」。
 *
 * 每个账号各用一份新实例（在 `MailBillImportUseCase.processAccount` 内创建，多账号时
 * 每个账号的 stepper 从头 PENDING，沿用既有口径）。
 *
 * 纯 Kotlin、无 Android 依赖，可 JVM 单测（见 `MailImportStepTrackerTest`）。
 */
internal class MailImportStepTracker {

    private val byStep = LinkedHashMap<MailImportStep, MailImportStepState>()

    /**
     * 记录本份快照列出的步骤状态（同一步骤重复 apply 取**最新**状态与 summary），
     * 返回按 [MailImportStep.entries] 排序的完整 5 步列表：
     * 已出现过的步骤保持/更新为其最近状态，从未出现过的步骤为 PENDING。
     */
    fun apply(vararg active: MailImportStepState): List<MailImportStepState> {
        active.forEach { byStep[it.step] = it }
        return MailImportStep.entries.map { step ->
            byStep[step] ?: MailImportStepState(step, MailImportStepStatus.PENDING)
        }
    }
}
