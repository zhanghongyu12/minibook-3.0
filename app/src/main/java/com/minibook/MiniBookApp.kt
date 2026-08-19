package com.minibook

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.minibook.data.local.security.AppLockController
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * MiniBook 应用入口（ARCHITECTURE §13 / §7.5 / §9、TASK-018）。
 *
 * - `@HiltAndroidApp`：Hilt 根组件（DEC-001，Hilt 2.58）。装配全部 SingletonComponent 绑定，
 *   验证全 DI 图可注入。
 * - 安全模块**懒加载**：[appLockController] 以 [Lazy] 注入，首帧不构造，不触碰 DB /
 *   DataStore（冷启动 ≤2s，§13）；首个 Activity 进入前台时才取实例（构造本身亦不触碰 DB，
 *   [DatabaseGate] 未 [DatabaseGate.open] 前无可用连接，见 §7.1）。
 * - **不直接调 BootstrapUseCase**：首启引导（默认账本 + 内置分类种子）需 DB 已解锁
 *   （§7.3/§8.4），而解锁经 [AppLockController]（BiometricPrompt 认证后开库），故
 *   BootstrapUseCase 由 UI 解锁流程触发（TASK-022 锁屏解锁成功后调用），Application 不调用。
 * - 锁生命周期观察者：目标方案为 `ProcessLifecycleOwner`（`lifecycle-process`，
 *   `DefaultLifecycleObserver.onStop→onAppBackground` / `onStart→onAppForeground`，§7.5/§9）；
 *   因 `lifecycle-process` 未在依赖集（[libs.versions.toml] 无声明，且 TASK-018 权限边界
 *   禁止改已 commit 构建文件），退而使用 [Application.ActivityLifecycleCallbacks]——这正是
 *   `ProcessLifecycleOwner` 内部的底层机制，语义等价：应用全部 Activity 停止（无可见
 *   Activity）→ [AppLockController.onAppBackground]；首个 Activity 启动（回到前台）→
 *   [AppLockController.onAppForeground]。
 */
@HiltAndroidApp
class MiniBookApp : Application() {

    /**
     * 应用锁控制器（懒注入：首帧不构造，避免任何 DB/DataStore 触碰，§13）。
     * 生命周期回调首次触发（应用进入前台）时才经 [Lazy.get] 构造。
     */
    @Inject
    lateinit var appLockController: Lazy<AppLockController>

    /** 已 STARTED 且未 STOPPED 的 Activity 数；归零即应用进入后台（主线程访问，无需同步）。 */
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        registerLockLifecycleObserver()
    }

    /**
     * 注册应用级锁生命周期观察者（§7.5/§9）。
     *
     * 以 [Application.ActivityLifecycleCallbacks] 追踪前台/后台（等价于
     * `ProcessLifecycleOwner` 的 ON_START/ON_STOP）：最后一个 Activity STOPPED → 后台 →
     * [AppLockController.onAppBackground]（启动闲置重锁计时）；首个 Activity STARTED → 前台 →
     * [AppLockController.onAppForeground]（取消计时，§9「回前台未超阈→恢复使用」）。
     */
    private fun registerLockLifecycleObserver() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {

            override fun onActivityStarted(activity: Activity) {
                if (startedActivityCount == 0) {
                    // 回前台（含冷启动首帧）：取消闲置重锁计时。
                    appLockController.get().onAppForeground()
                }
                startedActivityCount++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount == 0) {
                    // 无可见 Activity → 应用进入后台：启动闲置重锁计时（§7.5）。
                    appLockController.get().onAppBackground()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
