package com.huhst.jiaowu.data

import android.util.Log
import com.huhst.jiaowu.data.local.JiaowuStore
import com.huhst.jiaowu.data.model.AppSettings
import com.huhst.jiaowu.data.model.SavedCredentials
import com.huhst.jiaowu.data.net.WebVpn
import com.huhst.jiaowu.security.CredentialVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** 会话状态。 */
enum class SessionState {
    /** 尚未判定（冷启动中） */
    Unknown,
    /** 未登录 */
    LoggedOut,
    /** 已登录 */
    LoggedIn,
}

/**
 * 会话与凭证管理。
 *
 * 登录由 WebView 完成（学校页面有前端加密 JS、跳转链与 WebVPN 注入脚本，
 * 原生复刻既脆弱又无必要）。这里只负责：
 *  - 两套账号密码的加密读写（Keystore，密钥不出安全区）
 *  - 登录态标记
 *  - 退出登录时彻底清理
 *  - 登录后从门户首页发现 WebVPN 宿主码并缓存
 */
class SessionManager(
    private val store: JiaowuStore,
    private val vault: CredentialVault,
    private val webVpn: WebVpn,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var cachedCredentials: SavedCredentials? = null

    /** 冷启动时判定登录态并解出已保存的凭证。 */
    suspend fun bootstrap() {
        // ⚠️ 必须先解凭证、再切状态。
        // 反过来的话，界面会在凭证尚未解密时就渲染登录页，
        // 登录页读到的 prefill() 是空的 —— 自动登录永远不会触发。
        loadCredentials()
        val marker = store.sessionMarker()
        _state.value = if (marker) SessionState.LoggedIn else SessionState.LoggedOut
        Log.d(TAG, "bootstrap: marker=$marker 已存凭证=${cachedCredentials != null}")
    }

    private suspend fun loadCredentials() {
        if (cachedCredentials != null) return
        val blob = store.savedCredentialBlob()
        if (blob == null) {
            Log.d(TAG, "loadCredentials: 没有已保存的凭证")
            return
        }
        val plain = vault.decrypt(blob)
        if (plain == null) {
            Log.d(TAG, "loadCredentials: 解密失败（密钥可能已被清除）")
            return
        }
        cachedCredentials = runCatching {
            json.decodeFromString<SavedCredentials>(plain)
        }.getOrElse {
            Log.d(TAG, "loadCredentials: 解析失败 ${it.message}")
            null
        }
        Log.d(
            TAG,
            "loadCredentials: sso=${cachedCredentials?.ssoUser} " +
                "有sso密码=${!cachedCredentials?.ssoPassword.isNullOrEmpty()} " +
                "有教务密码=${!cachedCredentials?.jwPassword.isNullOrEmpty()}",
        )
    }

    /** 供登录页预填。 */
    fun savedCredentials(): SavedCredentials? = cachedCredentials

    /**
     * 保存两套凭证。remember=false 时只存学号。
     *
     * ⚠️ 采用**合并**而非覆盖：登录分两步，第一步成功后保存时第二步的密码天然还是空的，
     * 直接覆盖会把上次存好的教务密码清掉（实测踩过：学号与统一身份认证密码还在，
     * 教务密码被抹成空，用户被迫反复重敲）。空值不覆盖已有值。
     */
    suspend fun saveCredentials(credentials: SavedCredentials) {
        val existing = cachedCredentials
        val merged = SavedCredentials(
            ssoUser = credentials.ssoUser.ifBlank { existing?.ssoUser.orEmpty() },
            ssoPassword = credentials.ssoPassword.ifBlank { existing?.ssoPassword.orEmpty() },
            jwUser = credentials.jwUser.ifBlank { existing?.jwUser.orEmpty() },
            jwPassword = credentials.jwPassword.ifBlank { existing?.jwPassword.orEmpty() },
            remember = credentials.remember,
        )
        val toStore = if (merged.remember) merged else merged.withoutPasswords()
        cachedCredentials = toStore
        val blob = vault.encrypt(json.encodeToString(toStore)) ?: return
        store.putCredentialBlob(blob)
        Log.d(
            TAG,
            "saveCredentials: sso=${toStore.ssoUser} 有sso密码=${toStore.ssoPassword.isNotEmpty()} " +
                "有教务密码=${toStore.jwPassword.isNotEmpty()} remember=${toStore.remember}",
        )
    }

    /** 登录成功（两步都完成）后调用。 */
    suspend fun onLoggedIn(portalHomeHtml: String?, settings: AppSettings) {
        _state.value = SessionState.LoggedIn
        store.setSessionMarker(true)
        if (portalHomeHtml != null) {
            val code = webVpn.guessJiaowuHostCode(portalHomeHtml)
            if (code != null && code != settings.hostCode) {
                store.updateSettings { it.copy(hostCode = code) }
            }
        }
    }

    /** 主动登出：清数据、清凭证、清会话标记。 */
    suspend fun logout() {
        store.wipeSession()
        vault.clear()
        cachedCredentials = null
        _state.value = SessionState.LoggedOut
    }

    /** 仅标记会话失效（例如被踢下线），保留已记住的账号密码。 */
    suspend fun invalidate() {
        store.setSessionMarker(false)
        store.clearData()
        _state.value = SessionState.LoggedOut
    }

    suspend fun currentSettings(): AppSettings = store.settings.first()

    private companion object {
        const val TAG = "JiaowuSession"
    }
}
