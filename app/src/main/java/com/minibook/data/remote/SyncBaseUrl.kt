package com.minibook.data.remote

import com.minibook.BuildConfig

/**
 * 生效的服务地址解析（纯函数，可 JVM 单测）。
 *
 * 规则（2026-10-29 换服务器踩坑后确定，见 `docs/14_cloud_sync.md §20`）：
 * - **正式包**：一律用安装包内置地址（`BuildConfig.SYNC_BASE_URL`）。正式版界面不提供地址输入，
 *   用户无从修改；DataStore 里残留的旧地址只可能来自上一版安装包。若让它优先，
 *   换服务器后用户会**静默继续打旧域名**——本次真实故障就是如此：
 *   手机一直在向已下线的腾讯云发同步请求，阿里云一个请求都没收到，界面还显示「同步成功」。
 *   内置地址为空时才回退到已保存地址（兼容旧包）。
 * - **调试包**：已保存地址 > 内置地址（内网联调要能在界面上改地址，保持原行为）。
 */
object SyncBaseUrl {

    fun resolve(
        saved: String,
        debug: Boolean = BuildConfig.DEBUG,
        builtIn: String = BuildConfig.SYNC_BASE_URL,
    ): String {
        val savedTrimmed = saved.trim()
        val builtInTrimmed = builtIn.trim()
        return if (debug) {
            savedTrimmed.ifEmpty { builtInTrimmed }
        } else {
            builtInTrimmed.ifEmpty { savedTrimmed }
        }
    }
}
