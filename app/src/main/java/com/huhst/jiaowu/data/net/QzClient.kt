package com.huhst.jiaowu.data.net

import android.util.Log
import com.huhst.jiaowu.data.model.AppSettings
import com.huhst.jiaowu.data.model.NetChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** 会话失效发生在哪一层。 */
enum class LoginKind {
    /** WebVPN / 统一身份认证失效，需要从第一步重新登录 */
    Vpn,

    /** 只有教务系统的会话失效，理论上重做第二步即可 */
    Jw,
}

/** 取数结果。 */
sealed interface FetchResult {
    data class Ok(val body: String, val url: String) : FetchResult

    /** 会话失效（拿到的是登录页而不是数据页）。 */
    data class SessionExpired(val kind: LoginKind) : FetchResult

    data class Failure(val reason: String) : FetchResult
}

/**
 * 教务系统取数客户端。
 *
 * 双通道：
 *  - 校园网内直连 `http://10.1.1.149/jsxsd/...`（最快，也不占学校 VPN 并发）
 *  - 校外经 WebVPN `http://vpn.huhst.edu.cn:9900/http/<宿主码>/jsxsd/...`
 *
 * AUTO 模式下按「先直连、后 VPN」的顺序试，任一成功即返回。
 */
class QzClient(
    private val client: OkHttpClient,
    private val webVpn: WebVpn,
) {

    suspend fun fetch(
        settings: AppSettings,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): FetchResult = withContext(Dispatchers.IO) {
        val urls = buildCandidates(settings, path)
        if (urls.isEmpty()) {
            return@withContext FetchResult.Failure("尚未获得教务系统访问地址，请先登录")
        }

        var last: FetchResult = FetchResult.Failure("未知错误")
        for (raw in urls) {
            val httpUrl = raw.toHttpUrlOrNull()
                ?: run {
                    last = FetchResult.Failure("地址非法：$raw")
                    continue
                }
            val builder = httpUrl.newBuilder()
            query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            val target = builder.build()

            val result = runCatching {
                val request = Request.Builder()
                    .url(target)
                    .header("User-Agent", MOBILE_UA)
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val kind = detectLoginPage(body, target.toString())
                    Log.d(
                        TAG,
                        "GET ${target.encodedPath} -> ${resp.code} len=${body.length} " +
                            "login=${kind ?: "-"} head=${snippet(body)}",
                    )
                    when {
                        kind == LoginKind.Vpn -> FetchResult.SessionExpired(LoginKind.Vpn)
                        kind == LoginKind.Jw -> FetchResult.SessionExpired(LoginKind.Jw)
                        resp.isSuccessful -> FetchResult.Ok(body, target.toString())
                        else -> FetchResult.Failure("HTTP ${resp.code}")
                    }
                }
            }.getOrElse { e ->
                FetchResult.Failure(e.message ?: e.javaClass.simpleName)
            }

            when (result) {
                is FetchResult.Ok -> return@withContext result
                is FetchResult.SessionExpired -> {
                    // 直连拿到登录页时，可能只是这条通道不通，继续试下一条
                    last = result
                }

                is FetchResult.Failure -> last = result
            }
        }
        last
    }

    /** 探测校园网直连是否可用。 */
    suspend fun probeCampus(campusBase: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(campusBase.trimEnd('/') + QzPaths.ROOT + "/")
                .header("User-Agent", MOBILE_UA)
                .build()
            client.newCall(request).execute().use { it.code in 200..499 }
        }.getOrDefault(false)
    }

    /** 生成候选 URL，按尝试顺序。 */
    private fun buildCandidates(settings: AppSettings, path: String): List<String> {
        val p = if (path.startsWith("/")) path else "/$path"
        val campus = settings.campusBase.trimEnd('/') + p
        val vpn = settings.hostCode
            .takeIf { it.isNotBlank() }
            ?.let { webVpn.buildUrl(settings.vpnBase, it, p) }

        return when (settings.channel) {
            NetChannel.CAMPUS -> listOfNotNull(campus)
            NetChannel.VPN -> listOfNotNull(vpn)
            NetChannel.AUTO -> listOfNotNull(campus, vpn)
        }
    }

    /**
     * 判断响应体是不是登录页，并区分是哪一层。
     *
     * ⚠️ 这里必须**保守**：误判的代价是把用户整个踢出去。
     * 之前用「正文里出现『统一身份认证』」来判断，结果正常的课表页
     * （页脚链接、WebVPN 注入脚本里都可能带这几个字）被误判成登录页。
     * 所以现在只认**登录表单的特征元素**，不认零散的文字。
     */
    private fun detectLoginPage(body: String, url: String): LoginKind? {
        if (body.isBlank()) return null
        val trimmed = body.trimStart()

        // 强智未登录时直接返回的 JSON 信封
        if (trimmed.startsWith("{") && trimmed.contains("请先登录系统")) return LoginKind.Jw

        val head = body.take(30000)

        // 正方统一身份认证（CAS）：靠它特有的密码框 id 判定。
        // 注意 execution 是 name 不是 id —— 之前写成 id="execution" 导致 CAS 页没被认出来。
        if (head.contains("id=\"ppassword\"") ||
            (head.contains("name=\"execution\"") && head.contains("id=\"username\"")) ||
            head.contains("id=\"fm1\"")
        ) {
            return LoginKind.Vpn
        }

        // 强智教务系统自己的登录页
        if (head.contains("id=\"userPassword\"") || head.contains("id=\"loginForm\"")) {
            return LoginKind.Jw
        }

        // 被真正重定向到 CAS 才算 VPN 失效
        if (url.contains("/cas/login")) return LoginKind.Vpn

        return null
    }

    private fun snippet(body: String): String =
        body.replace(Regex("\\s+"), " ").take(100)

    private companion object {
        const val TAG = "JiaowuNet"
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
