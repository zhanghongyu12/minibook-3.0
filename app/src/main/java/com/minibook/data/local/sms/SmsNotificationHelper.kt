package com.minibook.data.local.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.minibook.R
import com.minibook.domain.model.SmsNotifyMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 短信账单通知（F-14 设计 §11.3；DEC-025 阶梯③「降噪」）。
 *
 * 降噪原则：
 * - **只有命中账单模板的短信才可能提醒**（验证码/营销/快递零通知——由解析层 `NotBill` 前置挡掉）；
 * - 默认 [SmsNotifyMode.SILENT]：不发通知，只在 App 内累计「待确认」；
 * - 通知文案**永不含短信原文、卡号全文、余额**（DEC-026），只有条数与结果。
 */
@Singleton
class SmsNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val CHANNEL_PENDING = "sms_pending"
        const val CHANNEL_AUTO = "sms_auto"

        /** 「待确认」聚合通知 id（同一 id 覆盖更新，避免刷屏）。 */
        private const val NOTIFICATION_ID_PENDING = 7401

        /** 「自动记账成功」通知 id。 */
        private const val NOTIFICATION_ID_AUTO = 7402
    }

    /** 建通道（幂等；App 启动与设置页开启时都调）。 */
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PENDING,
                "短信账单待确认",
                NotificationManager.IMPORTANCE_LOW, // 低优先级：无声音、无常驻横幅
            ).apply { description = "识别到可能的账单短信但未自动入账时，在此汇总" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AUTO,
                "短信自动记账",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "白名单发送方的账单短信已自动入账" },
        )
    }

    /**
     * 「待确认 N 条」聚合通知。
     *
     * [SmsNotifyMode.SILENT] 时**不发通知**（默认路径）；[SmsNotifyMode.EACH] 仍聚合为一条
     * （逐条提醒指每次触发都刷新这条聚合，不堆 N 条通知）。
     */
    fun notifyPending(count: Int, mode: SmsNotifyMode) {
        if (count <= 0) return
        if (mode == SmsNotifyMode.SILENT) return
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_PENDING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("有 $count 条短信待确认")
            .setContentText("打开 MiniBook 查看并确认入账")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PENDING, notification)
        }
    }

    /**
     * 「已自动记账」通知（仅白名单命中时；`EACH`/`DAILY` 均发，`SILENT` 不发）。
     *
     * @param description 自建摘要（如「短信消费·尾号3170·网银在线」），**不是短信原文**
     */
    fun notifyAutoImported(description: String, mode: SmsNotifyMode) {
        if (mode == SmsNotifyMode.SILENT) return
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_AUTO)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("已自动记账 1 笔")
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_AUTO, notification)
        }
    }

    /** 清除「待确认」聚合通知（用户进入待确认清单后调用）。 */
    fun clearPending() {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PENDING) }
    }

    /** 通知可用性：Android 13+ 需 POST_NOTIFICATIONS，未授予则静默跳过（不打扰、不报错）。 */
    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS",
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
