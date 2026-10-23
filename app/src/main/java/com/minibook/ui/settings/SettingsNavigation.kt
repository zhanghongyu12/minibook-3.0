package com.minibook.ui.settings

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 设置页跨层导航桥（2026-10-10 设置页改版）。
 *
 * 设置首页（[SettingsScreen]）位于 [com.minibook.ui.main.MainScreen] 内嵌 5 Tab 嵌套图，而四个
 * 二级设置页注册在**根图** [com.minibook.ui.navigation.MiniBookNavHost]——嵌套图不能直达根图
 * 目的地。与 [com.minibook.ui.home.HomeNavigation] 同口径：用 CompositionLocal 把根导航能力
 * 注入进来，设置页只读取使用，不再穿透 MainScreen 的参数列表（参数式回调改动面更大）。
 */
interface SettingsNavigation {
    /** 打开「外观」二级页（根图 `settings-appearance`）。 */
    fun openAppearance()

    /** 打开「安全」二级页（根图 `settings-security`）。 */
    fun openSecurity()

    /** 打开「数据」二级页（根图 `settings-data`）。 */
    fun openData()

    /** 打开「短信账单」二级页（根图 `settings-sms`）。 */
    fun openSms()

    /**
     * 打开「账号」页（根图 `settings-account`）。
     *
     * 2026-10-12 起设置首页「账号」行指向这里（标题与入口同名）；原先指向的
     * 「账号与同步」页已拆成「账号」页 + 「云同步」页（同步入口在「数据」里）。
     */
    fun openAccount()

    /** 打开「账号与同步」页（根图 `settings-cloud`；数据页的同步入口仍用它）。 */
    fun openCloud()

    /** 打开「隐私政策」页（根图 `settings-privacy-policy`；TASK-076）。 */
    fun openPrivacyPolicy()

    /** 打开「用户协议」页（根图 `settings-terms`；TASK-076）。 */
    fun openTerms()
}

/**
 * 设置页根导航桥的 CompositionLocal；由 [com.minibook.ui.navigation.MiniBookNavHost] 在根图内注入
 * 真实实现，缺省 no-op（预览/独立 compose 不崩）。读取方 = [SettingsScreen]。
 */
val LocalSettingsNavigation: ProvidableCompositionLocal<SettingsNavigation> =
    staticCompositionLocalOf {
        object : SettingsNavigation {
            override fun openAppearance() = Unit
            override fun openSecurity() = Unit
            override fun openData() = Unit
            override fun openSms() = Unit
            override fun openAccount() = Unit
            override fun openCloud() = Unit
            override fun openPrivacyPolicy() = Unit
            override fun openTerms() = Unit
        }
    }
