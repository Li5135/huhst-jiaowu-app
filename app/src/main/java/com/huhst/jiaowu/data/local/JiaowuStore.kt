package com.huhst.jiaowu.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.huhst.jiaowu.data.model.AppData
import com.huhst.jiaowu.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jiaowu")

/**
 * 本机持久化。
 *
 * 选用 DataStore + kotlinx.serialization（而非 Room）：本应用的数据量很小
 * （一个学期的课表与教学进度），序列化整块读写完全够用，且省掉注解处理器，
 * 构建更简单可靠。业务数据、设置、加密凭证、Cookie 四类分开存放。
 */
class JiaowuStore(context: Context) {

    private val dataStore = context.dataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val kData = stringPreferencesKey("app_data")
    private val kSettings = stringPreferencesKey("app_settings")
    private val kCredential = stringPreferencesKey("credential_blob")
    private val kCookies = stringPreferencesKey("cookies")
    private val kSession = stringPreferencesKey("session_marker")

    // ---------- 业务数据 ----------

    val data: Flow<AppData> = dataStore.data.map { prefs ->
        prefs[kData]?.let { raw ->
            runCatching { json.decodeFromString<AppData>(raw) }.getOrNull()
        } ?: AppData()
    }

    suspend fun currentData(): AppData = data.first()

    suspend fun updateData(transform: (AppData) -> AppData) {
        dataStore.edit { prefs ->
            val current = prefs[kData]?.let { raw ->
                runCatching { json.decodeFromString<AppData>(raw) }.getOrNull()
            } ?: AppData()
            prefs[kData] = json.encodeToString(transform(current))
        }
    }

    suspend fun clearData() {
        dataStore.edit { it.remove(kData) }
    }

    // ---------- 设置 ----------

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        prefs[kSettings]?.let { raw ->
            runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
        } ?: AppSettings()
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[kSettings]?.let { raw ->
                runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
            } ?: AppSettings()
            prefs[kSettings] = json.encodeToString(transform(current))
        }
    }

    // ---------- 凭证（密文） ----------

    suspend fun savedCredentialBlob(): String? = dataStore.data.first()[kCredential]

    suspend fun putCredentialBlob(blob: String?) {
        dataStore.edit { prefs ->
            if (blob == null) prefs.remove(kCredential) else prefs[kCredential] = blob
        }
    }

    // ---------- Cookie ----------

    suspend fun savedCookies(): String = dataStore.data.first()[kCookies].orEmpty()

    suspend fun putCookies(raw: String) {
        dataStore.edit { it[kCookies] = raw }
    }

    // ---------- 会话标记 ----------

    suspend fun sessionMarker(): Boolean = dataStore.data.first()[kSession] == "1"

    suspend fun setSessionMarker(on: Boolean) {
        dataStore.edit { prefs ->
            if (on) prefs[kSession] = "1" else prefs.remove(kSession)
        }
    }

    /** 退出登录：清空一切与本机账号相关的数据。 */
    suspend fun wipeSession() {
        dataStore.edit { prefs ->
            prefs.remove(kData)
            prefs.remove(kCredential)
            prefs.remove(kCookies)
            prefs.remove(kSession)
        }
    }
}
