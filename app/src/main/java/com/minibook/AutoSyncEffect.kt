package com.minibook

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.minibook.di.AutoSyncEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 自动云同步（DEC-038）触发点：**打开 / 回到 App 时**静默跑一次双向同步。
 *
 * 两个触发点合起来覆盖「打开」与「回到前台」：
 * 1. [AutoSyncEffect]：解锁后的主界面组合时跑一次 —— 覆盖冷启动解锁后的那一次，以及应用内
 *    上锁→解锁后的那一次（`key(isLocked)` 会让它重新组合）；
 * 2. `MainActivity.onStart()` → [runAutoSyncOnForeground] —— 覆盖从后台切回、且已解锁的情形。
 *
 * 为什么是「按需解析 + 吞掉异常」而不是注入：见 [AutoSyncEntryPoint] 的注释（解锁前取用会崩）。
 * 「还没解锁」不是错误，只是「这次不同步」；同步失败同样不打扰用户，语义与手动同步一致
 * （不推进游标，下次幂等续上）。
 */
@Composable
internal fun AutoSyncEffect() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { runAutoSyncOnForeground(context) }
}

/** 解析并跑一轮自动同步；任何异常都视为「这次不同步」。 */
internal suspend fun runAutoSyncOnForeground(context: Context) {
    runCatching {
        EntryPointAccessors.fromApplication(context.applicationContext, AutoSyncEntryPoint::class.java)
            .autoSyncManager()
            .runOnForeground()
    }
}
