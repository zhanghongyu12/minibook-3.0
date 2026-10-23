package com.minibook

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动冒烟（DEC-038 回归）：`MainActivity` 必须能**真正启动**——这一条刚刚失守过。
 *
 * 背景：自动同步起初把 `AutoSyncManager` 直接注入 `MainActivity`，而它依赖 `SyncUseCase` ⇒
 * `SyncStateDao`，数据库只经 `DatabaseGate`（真解密门）暴露 ⇒ **解锁前构造**直接抛
 * `IllegalStateException: DatabaseGate 未解锁/未打开，无法提供 SyncStateDao`，App 一启动就崩。
 * 当时 20 例设备测**全绿**（它们都不启动 Activity），直到人工启动才发现 —— 本用例补的就是这个缺口：
 * 真 Application（runner 是 `AndroidJUnitRunner`，不是 HiltTestApplication）+ 真 Hilt 图 + 真 Activity。
 */
@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {

    @Test
    fun app_launches_withoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(
                    "MainActivity 启动即结束（多半是 onCreate/注入阶段抛异常）",
                    activity.isFinishing,
                )
            }
        }
    }
}
