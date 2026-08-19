package com.minibook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.minibook.ui.navigation.MiniBookNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity + Compose 壳（ARCHITECTURE §12、TASK-018）。
 *
 * - `@AndroidEntryPoint`：Compose 壳的 Hilt 入口（Activity 级组件）。
 * - `setContent { MaterialTheme { Surface { MiniBookNavHost() } } }`：当前用
 *   [MaterialTheme] 默认主题占位；TASK-019 主题与设计系统落地后替换为 `MiniBookTheme`
 *   （含暗色跟随系统，ARCHITECTURE §11），本类不承载业务逻辑。
 * - 锁门控：未解锁时导航图顶层只可达 `lock` 目标（真解密门，§9）；根级组合由
 *   [MiniBookNavHost]（TASK-021 填充）依 [com.minibook.data.local.security.AppLockController.isLocked]
 *   决定 LockScreen 或主入口。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // TASK-019 替换为 MiniBookTheme（ARCHITECTURE §11）。
            MaterialTheme {
                Surface {
                    MiniBookNavHost()
                }
            }
        }
    }
}
