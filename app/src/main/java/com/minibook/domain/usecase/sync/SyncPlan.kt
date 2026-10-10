package com.minibook.domain.usecase.sync

/**
 * 同步**计划器**：纯函数，无 IO、无 Android 依赖（F-15 / DEC-033，`docs/14_cloud_sync.md §4.3~§4.7`）。
 *
 * 三模式、三路比较、LWW 冲突、墓碑差分全在这里判定；[com.minibook.domain.usecase.sync.SyncUseCase]
 * 只负责「取数据 → 调本函数 → 落库」，因此**预览与执行共用同一份判定**，不会出现
 * 「预览说 3 条、执行却动 5 条」。
 *
 * 五道保险在本层的落点：
 * 1. 快照在执行前写（UseCase 层，本层不管）；
 * 2. 删除只走墓碑：本地删除 **不**在此处表达为「删云端」，而是 [SyncPlan.tombstones]（上行墓碑）；
 *    云端墓碑才产生 [SyncPlan.localDeletes]；「仅下载」永不因**云端缺失**删本地（只认 `deleted=true`）；
 * 3. 冲突留档：任何一份会被覆盖/忽略的版本都进 [SyncPlan.conflicts]，绝不静默丢；
 * 4. 幂等：判据全部是「与 base 比」，重复跑同一份输入得到同一结果；
 * 5. dry-run：本函数即预览。
 */
object SyncPlanner {

    /**
     * 计算计划。
     *
     * @param mode 三种手动模式之一
     * @param local 本地 6 类业务行（同步视角投影）
     * @param bases `sync_state` 全部基线
     * @param remote 本次 pull 到的云端行（分页聚合后一次传入）
     * @param nowIso 构造墓碑信封用的时间戳（同一次同步内恒定，保证幂等可比）
     */
    fun plan(
        mode: SyncMode,
        local: List<SyncLocalRow>,
        bases: List<SyncBaseRow>,
        remote: List<SyncRemoteRow>,
        nowIso: String,
    ): SyncPlan {
        val localByKey = local.associateBy { it.key }
        val baseByKey = bases.associateBy { it.key }

        // ---- 上行：本地新增 / 本地修改（相对 base） ----
        val uploads =
            if (!mode.uploads) {
                emptyList()
            } else {
                local
                    .filter { row ->
                        val base = baseByKey[row.key]
                        base == null || base.baseUpdatedAt != row.updatedAt
                    }
                    .sortedBy { it.key }
                    .map {
                        SyncPushItem(
                            type = it.type,
                            id = it.id,
                            ledgerId = it.ledgerId,
                            updatedAt = it.updatedAt,
                            deleted = false,
                            payloadJson = it.payloadJson,
                        )
                    }
            }

        // ---- 上行：墓碑差分（DEC-035：基线有、本地已无 ⇒ 本地删过且未上行） ----
        // 这条路径天然覆盖「单条删除 / 批量删除 / 删账本 / 导入替换」等全部删除来源，
        // 无需在 5 处删除路径插写操作。
        val tombstones =
            if (!mode.uploads) {
                emptyList()
            } else {
                bases
                    .filter { it.key !in localByKey }
                    .sortedBy { it.key }
                    .map {
                        SyncPushItem(
                            type = it.type,
                            id = it.id,
                            ledgerId = it.ledgerId,
                            updatedAt = nowIso,
                            deleted = true,
                            payloadJson = null,
                        )
                    }
            }

        // ---- 下行：落地 / 冲突 / 云端墓碑 ----
        val downloads = mutableListOf<SyncRemoteRow>()
        val localDeletes = mutableListOf<SyncRemoteRow>()
        val conflicts = mutableListOf<SyncConflictRecord>()

        if (mode.downloads) {
            for (r in remote) {
                val localRow = localByKey[r.key]
                val base = baseByKey[r.key]

                if (r.deleted) {
                    // 云端墓碑：对端删过。本地若也改过（相对 base）→ 冲突，本地优先
                    // （push 时 `deleted=false` + 更新的 updated_at 会让服务端复位墓碑，
                    //  见 server/app/services/sync_service.py:101-102，故可收敛）。
                    when {
                        localRow == null -> Unit // 两端都没了，无需动作
                        base != null && localRow.updatedAt != base.baseUpdatedAt -> conflicts +=
                            conflict(
                                local = localRow,
                                remote = r,
                                outcome = OUTCOME_DELETE_VS_EDIT,
                                winner = ConflictWinner.LOCAL,
                            )
                        else -> localDeletes += r
                    }
                    continue
                }

                when {
                    // 首见（本机/本账号从未同步过该行）：直接落地，不看 base。
                    base == null -> downloads += r

                    // 本地已删、墓碑未上行：**不复活**（否则「仅下载」会把用户刚删的账目拉回来）；
                    // 云端在 base 之后也改过 → 留档，绝不静默丢。
                    localRow == null -> {
                        if (r.updatedAt != base.baseUpdatedAt) {
                            conflicts +=
                                conflict(
                                    local = null,
                                    remote = r,
                                    outcome = OUTCOME_DELETE_VS_EDIT,
                                    winner = ConflictWinner.LOCAL,
                                )
                        }
                    }

                    // 本地未改（== base）：快进到云端版本。
                    localRow.updatedAt == base.baseUpdatedAt -> downloads += r

                    // 本地改过、云端没改：本地为准，等下次 push。
                    r.updatedAt == base.baseUpdatedAt -> Unit

                    // 双方都改过：LWW。
                    r.updatedAt > localRow.updatedAt -> {
                        downloads += r
                        conflicts +=
                            conflict(localRow, r, OUTCOME_LWW_REMOTE, ConflictWinner.REMOTE)
                    }

                    else -> conflicts +=
                        conflict(localRow, r, OUTCOME_LWW_LOCAL, ConflictWinner.LOCAL)
                }
            }
        }

        return SyncPlan(
            mode = mode,
            uploads = uploads,
            downloads = downloads,
            conflicts = conflicts,
            localDeletes = localDeletes,
            tombstones = tombstones,
        )
    }

    /** 冲突留档 outcome 取值（写进 `sync_conflicts.resolution`，`resolvedAt` 仍为 NULL 待人工确认）。 */
    const val OUTCOME_LWW_REMOTE = "lww:remote"

    const val OUTCOME_LWW_LOCAL = "lww:local"

    const val OUTCOME_DELETE_VS_EDIT = "delete-vs-edit:local"

    private fun conflict(
        local: SyncLocalRow?,
        remote: SyncRemoteRow,
        outcome: String,
        winner: ConflictWinner,
    ): SyncConflictRecord = SyncConflictRecord(
        type = remote.type,
        id = remote.id,
        ledgerId = remote.ledgerId.ifEmpty { local?.ledgerId.orEmpty() },
        localJson = local?.payloadJson ?: DELETED_MARKER,
        remoteJson = remote.payloadJson ?: DELETED_MARKER,
        outcome = outcome,
        winner = winner,
    )

    /** 冲突一侧已被删除时的占位 JSON（列 NOT NULL，用带标记的空对象比空串更可读）。 */
    const val DELETED_MARKER = "{\"__deleted__\":true}"
}
