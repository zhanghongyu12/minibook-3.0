package com.minibook

import android.app.Application

/**
 * MiniBook 应用入口壳。
 * TASK-001 仅注册空 Application（不触碰 DB）；@HiltAndroidApp 与锁生命周期初始化见 TASK-018。
 */
class MiniBookApp : Application()