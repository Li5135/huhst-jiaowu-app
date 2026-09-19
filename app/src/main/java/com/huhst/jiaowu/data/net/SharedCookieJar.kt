package com.huhst.jiaowu.data.net

import android.util.Log
import android.webkit.CookieManager
import com.huhst.jiaowu.data.local.JiaowuStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Cookie 的完整快照。字段缺失会导致重启后会话失效，所以一个都不能省。 */
@Serializable
private data class CookieRecord(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    val persistent: Boolean,
    val expiresAt: Long,
)

/**
 * 在 WebView 与 OkHttp 之间共享 Cookie 的 [CookieJar]。
 *
 * 这是整个方案的关键接缝：登录由 WebView 完成（CAS 有验证码与前端加密 JS），
 * 成功后把 WebView 的 Cookie 灌进 OkHttp，之后所有取数都走原生 HTTP。
 *
 * ⚠️ 落盘必须是**无损**的。
 * 早先只存 `name=value; path=`，domain / secure / httpOnly / 过期时间全丢，
 * 结果 App 一重启原生请求就被弹回登录页——
 * 表面上「登录成功」，实际上会话已经废了。现在存完整快照。
 */
class SharedCookieJar(
    private val store: JiaowuStore,
    private val scope: CoroutineScope,
) : CookieJar {

    private val json = Json { ignoreUnknownKeys = true }
    private val memory = linkedMapOf<String, MutableMap<String, Cookie>>()

    @Volatile
    private var restored = false

    /** 从磁盘恢复。应在读取网络之前调用一次。 */
    fun restore() {
        if (restored) return
        restored = true
        val raw = runBlocking { runCatching { store.savedCookies() }.getOrDefault("") }
        if (raw.isBlank()) {
            Log.d(TAG, "restore: 磁盘上没有 Cookie")
            return
        }
        val records = runCatching {
            json.decodeFromString<List<CookieRecord>>(raw)
        }.getOrElse {
            Log.d(TAG, "restore 失败（可能是旧格式），按空处理: ${it.message}")
            return
        }
        records.forEach { r -> fromRecord(r)?.let { put(it) } }
        Log.d(TAG, "restore: 恢复 ${records.size} 条 -> ${names()}")
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { put(it) }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        restore()
        val now = System.currentTimeMillis()
        val matched = memory.values
            .flatMap { it.values }
            .filter { it.expiresAt > now && it.matches(url) }
        return matched
    }

    /** 把 WebView 中当前站点的 Cookie 同步进来（登录成功后调用）。必须主线程调用。 */
    fun syncFromWebView(urlString: String) {
        val manager = CookieManager.getInstance()
        val url = urlString.toHttpUrlOrNull() ?: return
        val root = "${url.scheme}://${url.host}:${url.port}/"

        var count = 0
        listOf(urlString, root).distinct().forEach { target ->
            val header = manager.getCookie(target) ?: return@forEach
            Log.d(
                TAG,
                "WebView Cookie 名称: " +
                    header.split(';').joinToString(", ") { it.trim().substringBefore('=') },
            )
            header.split(';').forEach { pair ->
                val trimmed = pair.trim()
                if (trimmed.isEmpty()) return@forEach
                // ⚠️ 必须显式补 path=/。
                // getCookie 只返回 name=value，不带 path；若直接用页面 URL 解析，
                // OkHttp 会按 URL 的**目录**推出一个很窄的 path
                // （如 /http/<码>/jsxsd/framework），导致请求别的路径时 Cookie 根本不发，
                // 会话看起来「登录成功」却处处失效。
                Cookie.parse(url, "$trimmed; path=/")?.let { put(it); count++ }
            }
        }
        persist()
        Log.d(TAG, "syncFromWebView: 同步 $count 条 -> ${names()}")
    }

    @Synchronized
    private fun put(cookie: Cookie) {
        memory.getOrPut(cookie.domain) { linkedMapOf() }[cookie.name] = cookie
    }

    private fun names(): String =
        memory.values.flatMap { it.values }
            .joinToString(", ") { "${it.name}@${it.domain}${it.path}" }

    private fun persist() {
        val snapshot = synchronized(this) {
            memory.values.flatMap { it.values }.map { c ->
                CookieRecord(
                    name = c.name,
                    value = c.value,
                    domain = c.domain,
                    path = c.path,
                    secure = c.secure,
                    httpOnly = c.httpOnly,
                    hostOnly = c.hostOnly,
                    persistent = c.persistent,
                    expiresAt = c.expiresAt,
                )
            }
        }
        val raw = json.encodeToString(snapshot)
        scope.launch(Dispatchers.IO) {
            runCatching { store.putCookies(raw) }
        }
    }

    /** 由完整快照还原 Cookie（通过标准 Set-Cookie 串交给 OkHttp 解析，避免依赖构造函数）。 */
    private fun fromRecord(r: CookieRecord): Cookie? = runCatching {
        val host = r.domain.removePrefix(".")
        val url = (if (r.secure) "https" else "http") + "://" + host + "/"
        val setCookie = buildString {
            append(r.name).append('=').append(r.value)
            append("; domain=").append(r.domain)
            append("; path=").append(if (r.path.isBlank()) "/" else r.path)
            if (r.persistent && r.expiresAt > 0 && r.expiresAt < Long.MAX_VALUE - 1) {
                val maxAge = ((r.expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                append("; max-age=").append(maxAge)
            }
            if (r.secure) append("; secure")
            if (r.httpOnly) append("; httponly")
        }
        Cookie.parse(url.toHttpUrlOrNull() ?: return null, setCookie)
    }.getOrNull()

    @Synchronized
    fun clear() {
        memory.clear()
        restored = true
        scope.launch(Dispatchers.IO) {
            runCatching { store.putCookies("") }
        }
    }

    private companion object {
        const val TAG = "JiaowuCookie"
    }
}
