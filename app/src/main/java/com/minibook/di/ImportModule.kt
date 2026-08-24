package com.minibook.di

import android.content.Context
import com.minibook.data.local.bill.BillParserFactory
import com.minibook.data.local.bill.DefaultBillParserFactory
import com.minibook.data.local.bill.SafBillFilePreparer
import com.minibook.domain.usecase.bill.BillFilePreparer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 账单导入（F-12）装配（ARCHITECTURE §16.2 目录归属，TASK-043）。
 *
 * 仅新增两个「域接口 → 数据层实现」绑定：
 * - [BillFilePreparer] → [SafBillFilePreparer]（Android Context/ContentResolver 边界）；
 * - [BillParserFactory] → [DefaultBillParserFactory]（无状态，解析器的工厂）。
 *
 * 解析器 [com.minibook.data.local.bill.AlipayCsvParser] /
 * [com.minibook.data.local.bill.WechatXlsxParser] 为无状态 object，由工厂直接实例化，
 * 不单独立 @Provides。ImportBillUseCase 依赖的 TransactionRunner/CategoryDataSource/
 * TransactionDataSource/IoDispatcher 均已由既有模块装配。
 */
@Module
@InstallIn(SingletonComponent::class)
object ImportModule {

    @Provides
    @Singleton
    fun provideBillFilePreparer(@ApplicationContext context: Context): BillFilePreparer =
        SafBillFilePreparer(context)

    @Provides
    @Singleton
    fun provideBillParserFactory(): BillParserFactory = DefaultBillParserFactory
}