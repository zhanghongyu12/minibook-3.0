package com.minibook.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 生效服务地址的优先级（2026-10-29 换服务器踩坑后新增）。
 *
 * 真实故障：手机上 DataStore 存着上一版安装包的腾讯云地址，正式包又让它优先于内置地址，
 * 于是换到阿里云后同步请求仍发往已下线的腾讯云——界面显示「同步成功」，
 * 而服务端 `sync_log` 里一条记录都没有。故正式包必须以内置地址为准。
 */
class SyncBaseUrlTest {

    private val oldServer = "http://old-sync.example.com:38080"
    private val newServer = "http://new-sync.example.com:38080"

    @Test
    fun `正式包：内置地址优先，忽略 DataStore 里的旧地址`() {
        assertEquals(
            newServer,
            SyncBaseUrl.resolve(saved = oldServer, debug = false, builtIn = newServer),
        )
    }

    @Test
    fun `正式包：内置地址为空时回退到已保存地址（兼容旧包）`() {
        assertEquals(
            oldServer,
            SyncBaseUrl.resolve(saved = oldServer, debug = false, builtIn = ""),
        )
    }

    @Test
    fun `调试包：已保存地址优先（内网联调要能改地址）`() {
        assertEquals(
            "http://192.168.1.5:8000",
            SyncBaseUrl.resolve(saved = "http://192.168.1.5:8000", debug = true, builtIn = newServer),
        )
    }

    @Test
    fun `调试包：未保存时用内置地址`() {
        assertEquals(newServer, SyncBaseUrl.resolve(saved = "", debug = true, builtIn = newServer))
    }

    @Test
    fun `两端都空时返回空串（由调用方给出可读提示）`() {
        assertEquals("", SyncBaseUrl.resolve(saved = "", debug = false, builtIn = ""))
        assertEquals("", SyncBaseUrl.resolve(saved = "  ", debug = true, builtIn = "  "))
    }
}
