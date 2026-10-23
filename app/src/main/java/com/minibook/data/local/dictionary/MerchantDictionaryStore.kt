package com.minibook.data.local.dictionary

import android.content.Context
import com.minibook.R
import com.minibook.di.IoDispatcher
import com.minibook.domain.usecase.category.BuiltInMerchantDictionary
import com.minibook.domain.usecase.category.MerchantDictionary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 内置商户词典装载器（docs/15 第 7 条，2026-10-13）。
 *
 * 词典随 APK 打包在 `res/raw/merchant_dictionary.json`（**离线可用、不上传、不需要账号**）；
 * 本类只负责把它装进 [BuiltInMerchantDictionary]（进程内注册表），之后查询是纯内存匹配。
 *
 * 设计取舍：
 * - **懒装载 + 幂等**：首次真正要建议时（导入预览路径）才读一次资源，不拖慢冷启动；
 * - **失败静默**：读不到/解析坏 → 空词典继续跑（词典只是建议，导入主流程不受影响）；
 * - `@Volatile loaded` + 双检：并发导入时最多重复装载一次，无锁竞争。
 */
@Singleton
class MerchantDictionaryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MerchantDictionary {

    @Volatile
    private var loaded = false

    override suspend fun ensureLoaded() {
        if (loaded) return
        withContext(ioDispatcher) {
            if (loaded) return@withContext
            val text = runCatching {
                context.resources.openRawResource(R.raw.merchant_dictionary)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }.getOrNull()
            if (text != null) {
                BuiltInMerchantDictionary.install(MerchantDictionaryJson.parse(text))
            }
            // 读失败也标记已装载：不在每次导入时反复尝试读坏资源。
            loaded = true
        }
    }

    override fun suggest(counterparty: String): String? = BuiltInMerchantDictionary.suggest(counterparty)
}
