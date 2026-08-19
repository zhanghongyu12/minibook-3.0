package com.minibook.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 注入 IO 调度器（ARCHITECTURE §5.2：文件 I/O、JSON（反）序列化、Keystore 加解密）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** 注入 Default 调度器（ARCHITECTURE §5.2：CPU 型组合/压缩等聚合）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** 注入 Main 调度器（ARCHITECTURE §5.2：UI 收集、BiometricPrompt 回调）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** 应用级协程作用域限定符（ARCHITECTURE §5.1/§5.2；闲置重锁计时宿主）。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * 协程调度器与应用级作用域装配（ARCHITECTURE §5.1/§5.2、TASK-018）。
 *
 * 所有切线程必须经本模块限定符注入（[@IoDispatcher]/[@DefaultDispatcher]/[@MainDispatcher]），
 * 便于测试替换（ARCHITECTURE §5.1 DispatcherModule 行）。`@ApplicationScope` 由 TASK-017
 * 暂驻 [SecurityModule]，TASK-018 收口迁至本模块，与三个 Dispatcher 限定符并列；消费方
 * [com.minibook.data.local.security.AppLockController]（闲置重锁计时宿主）经
 * [SecurityModule.provideAppLockController] 注入该作用域。
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    /** 文件 I/O / JSON 序列化 / Keystore 加解密调度器（§5.2）。 */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** 默认（CPU 型）调度器（§5.2：组合/压缩多个源）。 */
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** 主线程调度器（§5.2：UI 收集 / BiometricPrompt 回调）。 */
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * 应用级协程作用域（SupervisorJob + Main，§5.2）。
     * 由 [SecurityModule] 迁移而来（TASK-018 收口）；AppLockController 闲置重锁计时宿主，
     * 父作业为 [SupervisorJob]，子协程失败不影响应用级作用域。
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
