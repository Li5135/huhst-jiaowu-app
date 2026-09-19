package com.huhst.jiaowu.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 深信服 WebVPN 的 URL 改写层。
 *
 * 实测结论（vpn.huhst.edu.cn:9900）：
 *  - 门户把内网地址加密进路径：`/http/<宿主码>/<内网路径>`
 *  - 例如内网 `10.1.1.76:80` 对应宿主码 `a1a70fcd6961260728`
 *  - 未登录状态下多次请求，宿主码保持一致（推测按 host:port 固定）
 *
 * 宿主码**不硬编码**：登录后抓门户首页 HTML 解析出来并缓存，
 * 学校换 IP 或改规则时 App 无需发版即可自愈。
 */
class WebVpn(private val client: OkHttpClient) {

    private val hostCodeRegex = Regex("""/(?:http|https)/([0-9a-fA-F]{16,})/""")

    /** 拼接被 WebVPN 代理后的 URL。 */
    fun buildUrl(vpnBase: String, hostCode: String, path: String): String {
        val base = vpnBase.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return "$base/http/$hostCode$p"
    }

    /** 门户首页；登录成功后可访问，用于发现宿主码。 */
    fun portalHome(vpnBase: String): String = "${vpnBase.trimEnd('/')}/"

    /** 门户登录入口（会 302 到统一身份认证 CAS）。 */
    fun portalLogin(vpnBase: String): String = "${vpnBase.trimEnd('/')}/login"

    /** 抽取页面中所有出现过的宿主码，保持出现顺序。 */
    fun hostCodes(html: String): List<String> =
        hostCodeRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()

    /**
     * 猜测「教务系统」对应的宿主码。
     *
     * 优先返回带 jsxsd 字样的链接里的宿主码；找不到则返回出现次数最多的那个。
     * 实测门户首页里所有 jsxsd 链接都指向同一个宿主码，所以第一条规则就够用，
     * 第二条只是门户改版后的兜底。
     */
    fun guessJiaowuHostCode(html: String): String? {
        val nearJsxsd = Regex("""/(?:http|https)/([0-9a-fA-F]{16,})/[^"'\s]*jsxsd""")
            .find(html)?.groupValues?.get(1)
        if (nearJsxsd != null) return nearJsxsd

        val codes = hostCodes(html)
        return codes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    /** 拉取门户首页 HTML（需已登录）。 */
    suspend fun fetchPortalHome(vpnBase: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(portalHome(vpnBase)).build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }
}
