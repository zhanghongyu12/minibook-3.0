package com.minibook.ui.settings

import com.minibook.data.remote.SyncAccountState
import com.minibook.domain.usecase.sync.CloudStatusLine
import com.minibook.domain.usecase.sync.ConflictWinner
import com.minibook.domain.usecase.sync.SyncConflictRecord
import com.minibook.domain.usecase.sync.SyncMode
import com.minibook.domain.usecase.sync.SyncPlan
import com.minibook.domain.usecase.sync.SyncRunResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 云同步界面的**纯文案/纯判定层**（F-15 / TASK-067）。
 *
 * 抽出来的理由：这些字符串是用户**唯一的知情渠道**——「上传几条、下载几条、删几条、冲突几条」
 * 写错了比崩溃更糟（用户会以为已经同步好了）。所以它们全部是纯函数，由 `SyncPresenterTest`
 * 逐条断言，Composable 只负责摆位置。
 */
object SyncPresenter {

    /** 服务器地址输入框的提示（含「内网 http 仅调试版」这一 DEC-034 口径）。 */
    const val URL_HINT = "例：192.168.1.5:8000；不带协议时调试版补 http://，正式版补 https://"

    /** 上云数据边界（DEC-034）——必须显示给用户看，不能只写在文档里。 */
    const val PRIVACY_NOTICE =
        "仅同步账目数据（账本 / 分类 / 交易 / 债务 / 预算 / 分类规则），" +
            "不含短信原文与发送方白名单、邮箱凭据、应用锁与外观偏好。"

    /** 同步前会自动留快照的提示（「本地不丢」五道保险之一，必须让用户知道去哪找）。 */
    const val SNAPSHOT_NOTICE = "每次同步前会自动在本地留一份备份（最多保留 3 份），可在数据页导出。"

    fun modeSubtitle(mode: SyncMode): String = when (mode) {
        SyncMode.BOTH -> "先上传本地改动，再拉取云端改动"
        SyncMode.UPLOAD_ONLY -> "只把本地改动推到云端，不下载任何数据"
        SyncMode.DOWNLOAD_ONLY -> "只把云端数据拉到本地；云端已删除的条目会同步删除本地对应项"
    }

    /** 预览主句：四个数字**逐项**给出，不做任何合并省略。 */
    fun previewText(plan: SyncPlan): String =
        "上传 ${plan.uploads.size} 条改动 + ${plan.tombstones.size} 条删除 · " +
            "下载 ${plan.downloadCount} 条 · 删除本地 ${plan.deleteCount} 条 · 冲突 ${plan.conflictCount} 条"

    /** 预览明细（按模式裁剪：仅上传不谈下载，仅下载不谈上传）。 */
    fun previewDetails(mode: SyncMode, plan: SyncPlan): List<String> = buildList {
        if (mode.uploads) {
            add("上行 ${plan.uploads.size} 条新增/修改")
            add("上行 ${plan.tombstones.size} 条删除（墓碑，云端也会删）")
        }
        if (mode.downloads) {
            add("下行 ${plan.downloadCount} 条新增/修改")
            add("下行 ${plan.deleteCount} 条云端删除")
            if (plan.deleteCount > 0) {
                add("⚠️ 云端删除会同步删除本地对应条目（这是你在其他设备上的删除操作）")
            }
        }
        if (plan.conflictCount > 0) {
            add("⚠️ ${plan.conflictCount} 条冲突：按修改时间较新的一方保留，输的一方自动留档可查")
        }
    }

    /** 触发二次确认的条件：会删数据或存在冲突（DEC-033 第 7 条⑤）。 */
    fun needsDangerConfirm(plan: SyncPlan): Boolean =
        plan.deleteCount > 0 || plan.conflictCount > 0

    fun confirmTitle(mode: SyncMode): String = "确认${mode.label}"

    fun confirmMessage(mode: SyncMode, plan: SyncPlan): String =
        if (needsDangerConfirm(plan)) {
            previewText(plan) + "\n\n本次会修改或删除本地数据（同步前已自动留备份）。确认继续？"
        } else {
            previewText(plan) + "\n\n确认按以上内容执行${mode.label}？"
        }

    /** 计划为空时的提示（不弹确认框，直接告诉用户「没必要」。） */
    fun emptyPlanMessage(mode: SyncMode): String = when (mode) {
        SyncMode.BOTH -> "本地与云端已经一致，无需同步。"
        SyncMode.UPLOAD_ONLY -> "本地没有需要上传的改动。"
        SyncMode.DOWNLOAD_ONLY -> "云端没有需要下载的改动。"
    }

    /** 结果卡主文案。 */
    fun resultText(result: SyncRunResult): String {
        if (!result.ok) {
            return buildString {
                append("同步失败：").append(result.error)
                if (result.retryable) append("（网络问题，可直接重试）")
                append("\n游标未推进，下次同步不会漏数据。")
            }
        }
        return "同步完成：上传 ${result.uploaded} 条 · 云端删除 ${result.tombstoned} 条 · " +
            "下载 ${result.downloaded} 条 · 删除本地 ${result.deletedLocal} 条 · 冲突 ${result.conflicts} 条"
    }

    /** 快照文件名的展示形态（只给文件名，不暴露完整路径）。 */
    fun snapshotLabel(path: String?): String? =
        path?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

    /** 账号状态行（未登录 / 已登录 + 上次同步 + 冲突数）。 */
    fun statusText(state: SyncAccountState): String {
        if (!state.isLoggedIn) return "未登录（本地数据不受影响，可继续离线记账）"
        return buildString {
            append("已登录：").append(state.username)
            if (state.conflictCount > 0) {
                append(" · 待处理冲突 ").append(state.conflictCount).append(" 条")
            }
            if (state.lastSyncAt.isNotEmpty()) {
                append(" · 上次同步 ").append(humanTime(state.lastSyncAt))
                if (state.lastSyncMode.isNotEmpty()) append("（").append(state.lastSyncMode).append("）")
            } else {
                append(" · 尚未同步过")
            }
        }
    }

    /** [SyncUseCase.statusLine] 结果的状态行（与 [statusText] 同口径，供结果卡复用）。 */
    fun statusText(line: CloudStatusLine): String {
        if (!line.loggedIn) return "未登录（本地数据不受影响，可继续离线记账）"
        return buildString {
            append("已登录：").append(line.username)
            if (line.conflictCount > 0) append(" · 待处理冲突 ").append(line.conflictCount).append(" 条")
            if (line.lastSyncAt.isNotEmpty()) {
                append(" · 上次同步 ").append(humanTime(line.lastSyncAt))
                if (line.lastSyncMode.isNotEmpty()) append("（").append(line.lastSyncMode).append("）")
            } else {
                append(" · 尚未同步过")
            }
        }
    }

    /** 冲突留档的一行说明（类型 + 短 id + 判定结果）。 */
    fun conflictText(conflict: SyncConflictRecord): String =
        "${conflict.type.label} ${shortId(conflict.id)} · ${outcomeText(conflict.outcome)}"

    /** 判定结果的用户口径：说清「谁赢了、输家在哪」，不含糊。 */
    fun outcomeText(outcome: String): String = when (outcome) {
        "lww:local" -> "本地版本较新，已保留本地（云端旧版留档）"
        "lww:remote" -> "云端版本较新，已采用云端（本地旧版留档）"
        "delete-vs-edit:local" -> "你删过它、云端改过它：已按你的删除处理（云端版本留档）"
        else -> outcome
    }

    fun winnerLabel(winner: ConflictWinner): String = when (winner) {
        ConflictWinner.LOCAL -> "本地"
        ConflictWinner.REMOTE -> "云端"
    }

    /** ISO 时间 → 本地「MM-dd HH:mm」；解析不了就原样返回（宁可难看，不可显示错误时间）。 */
    fun humanTime(iso: String): String {
        if (iso.isEmpty()) return "—"
        return try {
            FORMATTER.format(Instant.parse(iso))
        } catch (e: Exception) {
            iso
        }
    }

    /** 只留首段 8 字符，避免整屏 UUID 噪音。 */
    fun shortId(id: String): String = if (id.length <= 8) id else id.take(8)

    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
}
