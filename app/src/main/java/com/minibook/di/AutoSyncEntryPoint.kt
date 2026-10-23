package com.minibook.di

import com.minibook.domain.usecase.sync.AutoSyncManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 自动同步的**按需取用**入口（DEC-038）。
 *
 * 为什么需要它：`AutoSyncManager` 依赖 `SyncUseCase` ⇒ `SyncStateDao`，而数据库只经
 * `DatabaseGate`（真解密门）暴露——在解锁前构造它就是启动崩溃（实测：
 * `IllegalStateException: DatabaseGate 未解锁/未打开，无法提供 SyncStateDao`）。
 * 因此不能在 `MainActivity` 里字段注入，必须等到**解锁后**再取。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoSyncEntryPoint {
    fun autoSyncManager(): AutoSyncManager
}
