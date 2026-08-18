package com.minibook.di

import com.minibook.data.datasource.TransactionRunner
import com.minibook.data.local.datasource.RoomTransactionRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 事务原语装配（TASK-009 事务闭合方案）。
 *
 * 独立模块：不污染 6 业务 DataSource 接口与 [DataSourceModule]。
 * [TransactionRunner] 为数据层事务原语，Room 实现 [RoomTransactionRunner] 构造注入
 * [com.minibook.data.local.security.DatabaseGate]；未来接云端（远程事务/补偿）仅需
 * 替换本模块 `@Binds` 一处，Repository 层零改动。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
