package com.minibook

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.fragment.app.FragmentActivity
import com.minibook.ui.navigation.MiniBookNavHost
import com.minibook.ui.theme.MiniBookTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity + Compose 壳（ARCHITECTURE §12、TASK-018）。
 *
 * - `@AndroidEntryPoint`：Compose 壳的 Hilt 入口（Activity 级组件）。
 * - `onCreate` 先 `enableEdgeToEdge()`（TASK-019 落地）：让状态栏/导航栏透明并跟随
 *   主题 colorScheme 背景（暗色系统栏图标随暗色自动切换）。
 * - `setContent { MiniBookTheme { Surface { MiniBookNavHost() } } }`：主题与设计系统已由
 *   TASK-019 落地（[MiniBookTheme]，含暗色跟随系统，ARCHITECTURE §11），本类不承载业务逻辑。
 * - 锁门控：未解锁时导航图顶层只可达 `lock` 目标（真解密门，§9）；根级组合由
 *   [MiniBookNavHost]（TASK-021 填充）依 [com.minibook.data.local.security.AppLockController.isLocked]
 *   决定 LockScreen 或主入口。
 * - 父类用 [FragmentActivity]（TASK-022）：`androidx.fragment.fragment` 经 activity-compose
 *   传递依赖已在运行时 classpath，不改 toml/build.gradle；解锁门需在 FragmentActivity 内触发
 *   BiometricPrompt（[com.minibook.data.local.security.AppLockController.unlock] `useAuth` 路径）。
 *   [FragmentActivity] 继承 [androidx.activity.ComponentActivity]，`enableEdgeToEdge()`
 *   与 `setContent` 均不受影响。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiniBookTheme {
                Surface {
                    MiniBookNavHost()
                }
            }
        }
    }
}
