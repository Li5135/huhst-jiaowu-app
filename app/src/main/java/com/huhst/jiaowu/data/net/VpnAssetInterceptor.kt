package com.huhst.jiaowu.data.net

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

/**
 * 兜底：把 WebView 里发往内网地址的静态资源请求代理到 WebVPN。
 *
 * **正常情况下本类什么都不做。** 实测深信服 WebVPN 会正确地把页面里的内网绝对地址
 * 改写成 `/http/<宿主码>/...` 再发起请求（抓到的实际请求确实是代理地址），
 * 所以绝大多数设备的资源加载不需要我们插手。
 *
 * 保留它的理由：WebVPN 的改写靠注入脚本在客户端完成，一旦该脚本在某个
 * WebView 版本上失效，资源请求就会直接打向 `10.1.1.x`（手机上不可达），
 * 页面会退化成无样式的裸表单。此时这里能在网络层兜住。
 *
 * 只处理「非主文档 + 私网地址」：页面导航与 Cookie 仍由 WebView 自己走，
 * 不干扰统一身份认证的跳转链。
 */
class VpnAssetInterceptor(private val client: OkHttpClient) {

    private val hostCodeRegex = Regex("""/(?:http|https)/([0-9a-fA-F]{16,})/""")

    fun intercept(
        request: WebResourceRequest,
        pageUrl: String?,
    ): WebResourceResponse? {
        if (request.isForMainFrame) return null
        if (pageUrl.isNullOrBlank()) return null

        val code = hostCodeRegex.find(pageUrl)?.groupValues?.get(1) ?: return null
        val origin = pageUrl.substringBefore("/http/").trimEnd('/')
        if (origin.isEmpty() || origin == pageUrl) return null

        val target = request.url?.toString() ?: return null
        val proxied = rewrite(target, origin, code) ?: return null

        return runCatching {
            val builder = Request.Builder().url(proxied)
            request.requestHeaders.forEach { (name, value) ->
                // Accept-Encoding 交给 OkHttp 自己管，它才会透明解压；
                // Host 由 OkHttp 依 URL 重新生成。
                if (name.startsWith(":")) return@forEach
                if (name.equals("Host", true)) return@forEach
                if (name.equals("Accept-Encoding", true)) return@forEach
                runCatching { builder.header(name, value) }
            }

            val response = client.newCall(builder.build()).execute()
            val body: InputStream = response.body?.byteStream() ?: return null

            val rawContentType = response.header("Content-Type") ?: guessMime(proxied)
            val mime = rawContentType.substringBefore(';').trim().ifBlank { guessMime(proxied) }
            val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                .find(rawContentType)?.groupValues?.get(1)?.trim()?.trim('"')
                ?: "utf-8"

            val headers = response.headers.toMultimap()
                .filterKeys { key ->
                    // OkHttp 已透明解压，必须去掉编码与长度头，否则 WebView 会二次解码
                    !key.equals("Content-Encoding", true) &&
                        !key.equals("Content-Length", true) &&
                        !key.equals("Transfer-Encoding", true)
                }
                .mapValues { (_, values) -> values.joinToString(", ") }

            WebResourceResponse(
                mime,
                charset,
                response.code,
                response.message.ifBlank { "OK" },
                headers,
                body,
            )
        }.getOrNull()
    }

    /** 把内网绝对地址改写为 WebVPN 网关地址；不需要改写则返回 null。 */
    private fun rewrite(url: String, origin: String, code: String): String? {
        if (url.startsWith(origin)) return null
        val match = Regex("""^https?://([^/:]+)(?::(\d+))?(/.*)?$""").find(url) ?: return null
        val host = match.groupValues[1]
        val port = match.groupValues[2].ifBlank { "80" }
        val path = match.groupValues[3].ifBlank { "/" }

        if (port != "80") return null
        if (!isPrivateHost(host)) return null

        return "$origin/http/$code$path"
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host.startsWith("10.")) return true
        if (host.startsWith("192.168.")) return true
        val parts = host.split('.')
        if (parts.size >= 2 && parts[0] == "172") {
            val second = parts[1].toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }

    private fun guessMime(url: String): String = when {
        url.endsWith(".css") -> "text/css"
        url.endsWith(".js") -> "application/javascript"
        url.endsWith(".png") -> "image/png"
        url.endsWith(".jpg") || url.endsWith(".jpeg") -> "image/jpeg"
        url.endsWith(".gif") -> "image/gif"
        url.endsWith(".svg") -> "image/svg+xml"
        url.endsWith(".woff2") -> "font/woff2"
        url.endsWith(".woff") -> "font/woff"
        url.endsWith(".ttf") -> "font/ttf"
        url.endsWith(".json") -> "application/json"
        else -> "text/plain"
    }
}
