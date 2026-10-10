package com.minibook.data.remote

import com.minibook.domain.usecase.export.ExportAllUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * **debug 源集**里的测试专用 Hilt 入口点（不随 release 打包）。
 *
 * 为什么放在这里而不是 `androidTest`：Hilt 只为**编译变体内的** `@EntryPoint` 生成实现。
 * 声明在 androidTest 时，生产 `SingletonComponent` 并不实现它，运行期 `EntryPointAccessors`
 * 会抛 `ClassCastException: Cannot cast DaggerMiniBookApp_HiltComponents_SingletonC$SingletonCImpl
 * to ...EntryPoint`（2026-10-10 实测）。放到 `src/debug` 后：debug 变体的组件会实现它，
 * 而 androidTest 编译的正是 debug 变体，故可见；release 变体不含本文件。
 *
 * 用途：设备端端到端测试需要生产 `ExportAllUseCase`（`SyncUseCase` 构造依赖之一，
 * 用于同步前快照）；其余依赖测试自己装配（内存 Room + 真 Keystore + 真 OkHttp）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncDebugEntryPoint {
    fun exportAllUseCase(): ExportAllUseCase
}
