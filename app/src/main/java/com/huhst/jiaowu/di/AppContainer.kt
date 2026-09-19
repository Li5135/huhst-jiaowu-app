package com.huhst.jiaowu.di

import android.content.Context
import com.huhst.jiaowu.data.SessionManager
import com.huhst.jiaowu.data.local.JiaowuStore
import com.huhst.jiaowu.data.net.QzClient
import com.huhst.jiaowu.data.net.QzParser
import com.huhst.jiaowu.data.net.SharedCookieJar
import com.huhst.jiaowu.data.net.VpnAssetInterceptor
import com.huhst.jiaowu.data.net.WebVpn
import com.huhst.jiaowu.data.repo.JiaowuRepository
import com.huhst.jiaowu.security.CredentialVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 轻量依赖容器（手写，不引入 Hilt）。
 *
 * 本应用依赖关系很浅，手写容器省掉注解处理器与 KSP，
 * 构建更快、出错面更小。
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val store = JiaowuStore(context)
    val vault = CredentialVault()
    val cookieJar = SharedCookieJar(store, appScope)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val webVpn = WebVpn(httpClient)
    val parser = QzParser()
    val qzClient = QzClient(httpClient, webVpn)
    val assetInterceptor = VpnAssetInterceptor(httpClient)

    val session = SessionManager(store, vault, webVpn)
    val repository = JiaowuRepository(store, qzClient, parser)
}
