package com.huhst.jiaowu.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.huhst.jiaowu.data.SessionState
import com.huhst.jiaowu.data.model.AppData
import com.huhst.jiaowu.data.model.AppSettings
import com.huhst.jiaowu.data.model.NetChannel
import com.huhst.jiaowu.data.net.LoginKind
import com.huhst.jiaowu.data.repo.SyncOutcome
import com.huhst.jiaowu.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 同步的界面状态。 */
sealed interface SyncUi {
    data object Idle : SyncUi
    data object Running : SyncUi
    data class Done(val message: String) : SyncUi
    data class Error(val message: String) : SyncUi
}

/**
 * 应用级 ViewModel：会话、设置、数据与同步。
 *
 * 只有一个，避免为三个页面各造一套状态机；页面各自从中取需要的部分。
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val sessionState: StateFlow<SessionState> = container.session.state

    /** 供登录页 WebView 代理内网静态资源。 */
    val assetInterceptor get() = container.assetInterceptor

    val data: StateFlow<AppData> = container.repository.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    val settings: StateFlow<AppSettings> = container.store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _sync = MutableStateFlow<SyncUi>(SyncUi.Idle)
    val sync: StateFlow<SyncUi> = _sync.asStateFlow()

    init {
        viewModelScope.launch {
            container.cookieJar.restore()
            container.session.bootstrap()

            // 冷启动校验：标记说「已登录」不代表会话真的还有效
            // （Cookie 可能过期、或曾被不完整地持久化）。
            // 校验失败就退回登录页——那里已经能凭已记住的凭证自动重登。
            if (container.session.state.value == SessionState.LoggedIn) {
                val settings = container.store.currentSettings()
                val probe = runCatching {
                    container.qzClient.fetch(settings, com.huhst.jiaowu.data.net.QzPaths.HOME)
                }.getOrNull()
                if (probe is com.huhst.jiaowu.data.net.FetchResult.SessionExpired) {
                    android.util.Log.d("JiaowuSync", "冷启动校验失败（${probe.kind}），退回登录页")
                    container.session.invalidate()
                } else {
                    android.util.Log.d("JiaowuSync", "冷启动校验通过")
                }
            }
        }
    }

    fun syncNow() {
        if (_sync.value is SyncUi.Running) return
        viewModelScope.launch {
            _sync.value = SyncUi.Running

            val outcome = runCatching { container.repository.sync(settings.value) }
                .getOrElse { SyncOutcome.Failure(it.message ?: "同步失败") }
            _sync.value = when (outcome) {
                is SyncOutcome.Success -> SyncUi.Done(
                    buildString {
                        append("已更新：")
                        append("课表 ${outcome.sessions} 条")
                        if (outcome.grades > 0) append("、成绩 ${outcome.grades} 条")
                        if (outcome.progresses > 0) append("、教学进度 ${outcome.progresses} 门")
                        if (!outcome.profileFilled) append("（未取到个人信息）")
                    },
                )

                is SyncOutcome.SessionExpired -> {
                    android.util.Log.d(
                        "JiaowuSync",
                        "会话失效，层级=${outcome.kind}",
                    )
                    container.session.invalidate()
                    SyncUi.Error(
                        if (outcome.kind == LoginKind.Vpn) {
                            "统一身份认证已失效，请重新登录"
                        } else {
                            "教务系统会话已失效，请重新登录"
                        },
                    )
                }

                is SyncOutcome.Failure -> SyncUi.Error(outcome.reason)
            }
        }
    }

    fun dismissSyncMessage() {
        _sync.value = SyncUi.Idle
    }

    fun setChannel(channel: NetChannel) {
        viewModelScope.launch { container.store.updateSettings { it.copy(channel = channel) } }
    }

    /** 课表第一列切周日／周一。 */
    fun setWeekStartsSunday(startsSunday: Boolean) {
        viewModelScope.launch {
            container.store.updateSettings { it.copy(weekStartsSunday = startsSunday) }
        }
    }

    /**
     * 从相册选中的图片设为背景。
     *
     * 图片会**拷贝到应用私有目录**再使用，而不是直接存相册 URI ——
     * 相册返回的 URI 只在本次进程内有效，重启后就读不到了。
     */
    fun importBackground(uri: android.net.Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val context = container.appContext
                    val dir = java.io.File(context.filesDir, "background")
                    dir.mkdirs()
                    // 只保留一张，避免反复换图把空间吃满
                    dir.listFiles()?.forEach { it.delete() }

                    val target = java.io.File(dir, "bg_${System.currentTimeMillis()}.jpg")
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching null
                    input.use { source ->
                        target.outputStream().use { sink -> source.copyTo(sink) }
                    }
                    target.takeIf { it.length() > 0 }?.absolutePath
                }.getOrNull()
            }

            if (path != null) {
                container.store.updateSettings { it.copy(scheduleBackground = path) }
                _sync.value = SyncUi.Done("背景已更新")
            } else {
                _sync.value = SyncUi.Error("这张图片读取失败，换一张试试")
            }
        }
    }

    /** 恢复默认背景。 */
    fun clearBackground() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(container.appContext.filesDir, "background").deleteRecursively()
                }
            }
            container.store.updateSettings { it.copy(scheduleBackground = "") }
            _sync.value = SyncUi.Done("已恢复默认背景")
        }
    }

    fun setCurrentTerm(termId: String) {
        viewModelScope.launch { container.repository.setCurrentTerm(termId) }
    }

    fun clearCache() {
        viewModelScope.launch {
            runCatching { container.repository.clearCache() }
            _sync.value = SyncUi.Done("本地缓存已清除")
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { container.cookieJar.clear() }
            container.session.logout()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}
