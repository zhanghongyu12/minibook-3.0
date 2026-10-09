package com.minibook.domain.usecase.bill.zip

import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ZipPasswordCracker 穷举单测（复用 [ZipCryptoTest] 同文件内的 [ReferenceZipCrypto]/[buildZip] 夹具）。
 *
 * 覆盖：6 位数字口令命中（deflate / stored）；校验字节为 DOS 时间高字节时仍命中（宽松快筛）；
 * 口令长度不含正确口令 → 未命中返回 null；未加密 zip → null；进度回调被调用且尝试数有界。
 * 全部自造内存 zip，不依赖任何外部文件。
 */
class ZipPasswordCrackerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `穷举命中 6 位数字口令`() = runTest {
        val zipFile = tmp.newFile("crack.zip")
        zipFile.writeBytes(buildZip("bill.csv", "账单".toByteArray(), method = 8, password = "123456"))
        assertEquals("123456", ZipPasswordCracker().crack(zipFile))
    }

    @Test
    fun `穷举命中 - 校验字节为 DOS 时间高字节`() = runTest {
        val zipFile = tmp.newFile("crack-time.zip")
        zipFile.writeBytes(buildZip("bill.csv", "账单".toByteArray(), method = 8, password = "654321", checkByteFromTime = true))
        assertEquals("654321", ZipPasswordCracker().crack(zipFile))
    }

    @Test
    fun `穷举命中 - stored 方法`() = runTest {
        val zipFile = tmp.newFile("crack-stored.zip")
        zipFile.writeBytes(buildZip("bill.csv", "stored 内容".toByteArray(), method = 0, password = "888888"))
        assertEquals("888888", ZipPasswordCracker().crack(zipFile))
    }

    @Test
    fun `未命中返回 null - 口令长度不含正确口令`() = runTest {
        val zipFile = tmp.newFile("crack-miss.zip")
        zipFile.writeBytes(buildZip("bill.csv", "账单".toByteArray(), method = 8, password = "123456"))
        assertNull(ZipPasswordCracker().crack(zipFile, digits = 4..4))
    }

    @Test
    fun `未加密 zip 返回 null`() = runTest {
        val zipFile = tmp.newFile("crack-plain.zip")
        zipFile.writeBytes(buildZip("bill.csv", "data".toByteArray(), method = 8, password = null))
        assertNull(ZipPasswordCracker().crack(zipFile))
    }

    @Test
    fun `进度回调被调用且尝试数有界`() = runTest {
        val zipFile = tmp.newFile("crack-progress.zip")
        zipFile.writeBytes(buildZip("bill.csv", "账单".toByteArray(), method = 8, password = "999999"))
        val progresses = ConcurrentLinkedQueue<Long>()
        val password = ZipPasswordCracker().crack(zipFile, onProgress = { tried -> progresses.add(tried) })
        assertEquals("999999", password)
        assertTrue("进度回调应被调用", progresses.isNotEmpty())
        assertTrue("尝试数应为正", progresses.all { it > 0L })
        assertTrue("尝试数不应超过 10^6", (progresses.maxOrNull() ?: 0L) <= 1_000_000L)
    }
}
