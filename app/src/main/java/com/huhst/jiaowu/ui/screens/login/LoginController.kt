package com.huhst.jiaowu.ui.screens.login

import android.util.Log
import android.webkit.WebView
import com.huhst.jiaowu.data.model.AppSettings
import com.huhst.jiaowu.data.model.SavedCredentials
import com.huhst.jiaowu.data.net.QzPaths
import com.huhst.jiaowu.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 登录流程所处的阶段。 */
enum class LoginPhase {
    /** 第 1 步：填写统一身份认证 */
    SsoForm,

    /** 第 1 步：提交中 */
    SsoWorking,

    /** 第 2 步：填写教务系统密码 */
    JwForm,

    /** 第 2 步：提交中 */
    JwWorking,

    /** 自动化失败，把真实网页露出来让用户手动完成 */
    Manual,

    /** 两步都完成 */
    Done,
}

/** 登录界面需要观察的状态。 */
data class LoginUiState(
    val phase: LoginPhase = LoginPhase.SsoForm,
    val error: String? = null,
    val hint: String = "",
    /** 是否把 WebView 露到前景（手动兜底时） */
    val webVisible: Boolean = false,
)

/**
 * 教务系统页面的实际状态。
 *
 * ⚠️ 必须靠**内容**判断，不能看 URL：强智在未登录时会把登录页
 * 直接渲染在目标地址上（服务端渲染，不做 302），所以
 * `/jsxsd/framework/xsMain.htmlx` 这个地址既可能是主页、也可能是登录页。
 * 实测踩过这个坑——只看 URL 会误判为「已登录」，把第二步整个跳过。
 */
@Serializable
private data class JwProbe(
    val needLogin: Boolean = true,
    /** 页面上是否真的有密码框——用来区分「真登录页」与「会话无效的 JSON 拒绝页」。 */
    val hasPassword: Boolean = false,
    val title: String = "",
    val textLen: Int = 0,
    val head: String = "",
    val contentType: String = "",
    val frames: Int = 0,
    val htmlHead: String = "",
)

/**
 * 两步登录的协调器。
 *
 * ## 为什么要用一个「不可见 WebView」来提交
 *
 * 学校登录页的密码会被页面自己的 JS 加密后才提交（CAS 的 `#ppassword` 上有
 * `CreateRatePasswdReq`，强智版另有 `encoded` 字段），而且登录后还有 WebVPN 的
 * 注入脚本参与改写跳转链。原生复刻这套逻辑既脆弱又随学校升级而失效。
 *
 * 所以这里的分工是：**界面是原生的，提交交给页面自己**——
 * 我们把值填进真实表单，再点真实的登录按钮，让学校那套逻辑原样跑完。
 * 只有当自动化走不通（验证码、页面改版）时才退化成「显示网页让用户手动登」。
 *
 * ## 两步的来源
 *
 * 统一身份认证（正方 CAS）与教务系统（强智 jsxsd）是两台不同内网主机上的
 * 两个独立系统，密码互不相同，因此必须登录两次。用户名同为学号。
 */
class LoginController(
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /**
     * 用 App 级作用域跑持久化与收尾。
     *
     * ⚠️ 不能用登录页的 `scope`：登录成功后登录页立刻销毁，
     * 它的协程作用域随之取消——写盘写到一半会被中断，
     * 结果就是「每次登录都白登，凭证一个都没存下来」。
     */
    private fun appLaunch(block: suspend () -> Unit) {
        container.appScope.launch { block() }
    }

    private var webView: WebView? = null
    private var settings = AppSettings()

    /** 当前这一步在等什么页面。 */
    private enum class Await { Nothing, SsoResult, JwPage, JwResult }

    private var awaiting = Await.Nothing
    private var formSent = false
    private var pendingCredentials = SavedCredentials()

    private val json = Json { ignoreUnknownKeys = true }

    /** 进入手动兜底之前所处的表单阶段，用于「返回」时回到原处。 */
    private var resumePhase = LoginPhase.SsoForm

    // ---------------- 生命周期 ----------------

    fun bind(webView: WebView) {
        this.webView = webView
        scope.launch { settings = container.store.currentSettings() }
    }

    fun unbind() {
        webView = null
    }

    /** 进入登录页时调用：把上次记住的学号预填出来。 */
    fun prefill(): SavedCredentials = container.session.savedCredentials() ?: SavedCredentials()

    // ---------------- 用户动作 ----------------

    /** 第 1 步提交。 */
    fun submitSso(user: String, password: String, remember: Boolean) {
        if (user.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "请输入学号与密码")
            return
        }
        pendingCredentials = pendingCredentials.copy(
            ssoUser = user.trim(),
            ssoPassword = password,
            remember = remember,
        )
        formSent = false
        awaiting = Await.SsoResult
        _state.value = LoginUiState(phase = LoginPhase.SsoWorking, webVisible = false)

        // 门户登录入口会自动 302 到统一身份认证，并带上 WebVPN 的票据
        webView?.loadUrl(container.webVpn.portalLogin(settings.vpnBase))
            ?: run {
                _state.value = LoginUiState(
                    phase = LoginPhase.SsoForm,
                    error = "登录组件尚未就绪，请重试",
                )
            }
    }

    /** 第 2 步提交。 */
    fun submitJw(user: String, password: String, remember: Boolean) {
        if (user.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "请输入学号与密码")
            return
        }
        pendingCredentials = pendingCredentials.copy(
            jwUser = user.trim(),
            jwPassword = password,
            remember = remember,
        )
        formSent = false
        awaiting = Await.JwResult
        _state.value = LoginUiState(phase = LoginPhase.JwWorking, webVisible = false)

        val page = currentUrl()
        if (page != null && isJwPage(page)) {
            fillAndSubmitJw(page, user.trim(), password)
        } else {
            // 不在登录页上（例如被弹回门户），重新走一次深链入口
            webView?.loadUrl(jwEntryUrl())
        }
    }

    /** 用户主动选择「改用网页登录」。 */
    fun openManual() {
        awaiting = Await.Nothing
        formSent = false
        resumePhase = currentFormPhase()
        _state.value = LoginUiState(
            phase = LoginPhase.Manual,
            webVisible = true,
            hint = "请在网页中完成登录，完成后点右上角「已完成」",
        )
    }

    /** 手动兜底完成后，由用户点「已完成」触发。 */
    fun confirmManual() {
        finishLogin()
    }

    /** 从手动兜底返回表单。 */
    fun backToForm() {
        awaiting = Await.Nothing
        formSent = false
        _state.value = LoginUiState(phase = resumePhase)
    }

    // ---------------- 页面回调 ----------------

    fun onPageFinished(url: String) {
        Log.d(TAG, "onPageFinished awaiting=$awaiting sent=$formSent url=$url")
        when (awaiting) {
            Await.SsoResult -> handleSsoPage(url)
            Await.JwPage -> handleJwLanding(url)
            Await.JwResult -> handleJwResult(url)
            Await.Nothing -> Unit
        }
    }

    fun onPageError(description: String) {
        _state.value = _state.value.copy(
            phase = phaseBeforeWorking(),
            error = "网络错误：$description",
            webVisible = false,
        )
        awaiting = Await.Nothing
    }

    // ---------------- 第 1 步：统一身份认证 ----------------

    private fun handleSsoPage(url: String) {
        if (isCasLoginPage(url)) {
            if (!formSent) {
                fillAndSubmitSso(url)
            } else {
                // 提交过又回到登录页 —— 多半是账号密码不对，或被要求输验证码
                awaiting = Await.Nothing
                _state.value = LoginUiState(
                    phase = LoginPhase.SsoForm,
                    error = "统一身份认证未通过，请检查学号与密码",
                )
            }
            return
        }

        // ⚠️ 只要不是 CAS 登录页，就说明认证已经过了 —— 不能只认「回到门户根路径」。
        // 实测第一步成功后浏览器可能**直接落到教务系统**
        // （`/http/<码>/jsxsd/framework/xsMain.htmlx`）；
        // 旧逻辑只匹配门户根路径，两个分支都不进，状态机就卡死在「正在登录…」。
        appLaunch { container.session.saveCredentials(pendingCredentials) }

        if (isJwPage(url)) {
            // 已经落在教务系统上，直接判定是否还需要第二步
            awaiting = Await.JwPage
            handleJwLanding(url)
            return
        }

        if (isPortalLoggedIn(url, settings.vpnBase)) {
            // 回到门户根路径：同步 Cookie，再深链到教务系统
            container.cookieJar.syncFromWebView(url)
            awaiting = Await.JwPage
            _state.value = LoginUiState(
                phase = LoginPhase.JwWorking,
                hint = "统一身份认证已通过，正在进入教务系统…",
            )
            webView?.loadUrl(jwEntryUrl())
        }
    }

    private fun fillAndSubmitSso(url: String) {
        formSent = true
        val user = pendingCredentials.ssoUser
        val pass = pendingCredentials.ssoPassword
        val js = """
            (function(){
              try{
                var f = document.getElementById('fm1') || document.querySelector('form');
                if(!f) return 'noform';
                var u = f.querySelector('#username') || f.querySelector('input[name=username]');
                var p = f.querySelector('#ppassword') || f.querySelector('input[type=password]');
                var h = f.querySelector('#password');
                function set(el,v){ if(!el) return; el.value=v;
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  el.dispatchEvent(new Event('keyup',{bubbles:true}));
                  el.dispatchEvent(new Event('change',{bubbles:true})); }
                set(u, ${jsString(user)});
                set(p, ${jsString(pass)});
                set(h, ${jsString(pass)});

                var box = document.getElementById('captcha-box') || document.getElementById('kaptcha');
                if(box){
                  var st = window.getComputedStyle(box);
                  if(st.display !== 'none' && st.visibility !== 'hidden' && box.offsetParent !== null){
                    return 'captcha';
                  }
                }
                var btn = document.getElementById('dl')
                       || f.querySelector('button[type=submit],input[type=submit]');
                if(btn){ btn.click(); return 'clicked'; }
                return 'nobutton';
              }catch(e){ return 'err:' + e.message; }
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js) { result ->
            Log.d(TAG, "sso fill result=$result")
            when (result?.trim('"')) {
                "captcha" -> openManualWithHint("登录需要输入验证码，请在网页中完成这一步")
                "noform", "nobutton" -> openManualWithHint("登录页结构已变化，请在网页中手动登录")
                "clicked" -> _state.value = _state.value.copy(hint = "正在验证…")
                else -> openManualWithHint("自动登录未成功，请在网页中手动登录")
            }
        }
    }

    // ---------------- 第 2 步：教务系统 ----------------

    private fun jwHomeUrl(): String {
        val code = settings.hostCode.ifBlank { QzPaths.JSXSD_HOST_CODE }
        return container.webVpn.buildUrl(settings.vpnBase, code, QzPaths.HOME)
    }

    /**
     * 教务系统的**登录入口**。
     *
     * 必须是 `/jsxsd/` 根路径，不能直接用 `xsMain.htmlx` ——
     * 实测未登录时访问 `xsMain.htmlx` 只会返回一句
     * `{"flag1":2,"msgContent":"请先登录系统"}` 的 JSON，根本不是登录页。
     * 已登录时访问根路径会自动进入主页，所以两种情形都能覆盖。
     */
    private fun jwEntryUrl(): String {
        val code = settings.hostCode.ifBlank { QzPaths.JSXSD_HOST_CODE }
        return container.webVpn.buildUrl(settings.vpnBase, code, "${QzPaths.ROOT}/")
    }

    private fun handleJwLanding(url: String) {
        if (!isJwPage(url)) {
            openManualWithHint("未能自动进入教务系统，请在网页中手动完成")
            return
        }
        probeJwPage { probe ->
            when {
                probe == null -> openManualWithHint("未能识别教务系统页面，请在网页中手动登录")

                // 真的登录页：进入第 2 步
                probe.hasPassword -> {
                    awaiting = Await.Nothing
                    _state.value = LoginUiState(phase = LoginPhase.JwForm)
                    dumpJwLoginStructure(url)
                }

                // 会话无效，但当前页不是登录页
                // （典型是 xsMain 直接返回 `{"flag1":2,"msgContent":"请先登录系统"}`）
                // —— 需要跳到真正的登录入口，而不是把这个 JSON 页当成登录页
                probe.needLogin -> {
                    val atEntry = url.trimEnd('/').endsWith("/jsxsd")
                    if (atEntry) {
                        openManualWithHint("教务系统登录页结构异常，请在网页中手动登录")
                    } else {
                        awaiting = Await.JwPage
                        _state.value = LoginUiState(
                            phase = LoginPhase.JwWorking,
                            hint = "正在打开教务系统登录页…",
                        )
                        webView?.loadUrl(jwEntryUrl())
                    }
                }

                else -> finishLogin()
            }
        }
    }

    private fun fillAndSubmitJw(url: String, user: String, pass: String) {
        val js = """
            (function(){
              try{
                var form = document.getElementById('loginForm') || document.querySelector('form');

                // 已知的强智字段（实测结构：loginForm / userAccount / userPassword / encoded）
                var elUser = document.querySelector('#userAccount');
                var elPwd  = document.querySelector('#userPassword');

                var all = Array.prototype.slice.call(document.querySelectorAll('input'));
                function vis(el){ return el.type !== 'hidden' && el.offsetParent !== null && !el.disabled; }

                if(!elPwd){
                  elPwd = all.filter(function(i){ return i.type === 'password' && vis(i); })[0] || null;
                }
                if(!elUser){
                  var texts = all.filter(function(i){
                    return vis(i) && (i.type === 'text' || i.type === '');
                  });
                  for(var i=0;i<texts.length;i++){
                    var tag = (texts[i].id || '') + (texts[i].name || '');
                    if(/account|user|xh|name/i.test(tag)){ elUser = texts[i]; break; }
                  }
                  if(!elUser && elPwd){
                    for(var j=0;j<texts.length;j++){
                      if(texts[j].compareDocumentPosition(elPwd) & Node.DOCUMENT_POSITION_FOLLOWING){
                        elUser = texts[j]; break;
                      }
                    }
                  }
                  if(!elUser) elUser = texts[0] || null;
                }
                if(!elPwd) return 'nopwd';

                function set(el,v){ if(!el) return; el.value=v;
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  el.dispatchEvent(new Event('keyup',{bubbles:true}));
                  el.dispatchEvent(new Event('change',{bubbles:true})); }
                set(elUser, ${jsString(user)});
                set(elPwd, ${jsString(pass)});

                // 必须点真实按钮：密码是页面自己加密后写进 #encoded 的
                var submit = form
                  ? form.querySelector('button[type=submit],input[type=submit],button')
                  : null;
                if(!submit){
                  var cands = Array.prototype.slice.call(
                    document.querySelectorAll('button,input[type=submit],a'));
                  for(var k=0;k<cands.length;k++){
                    var t = (cands[k].innerText || cands[k].value || '').replace(/\s/g,'');
                    if(t.length <= 8 &&
                       (t.indexOf('登录') >= 0 || t.indexOf('登陆') >= 0 || /login/i.test(t))){
                      submit = cands[k]; break;
                    }
                  }
                }
                if(submit){ submit.click(); return 'clicked'; }
                if(form){ form.submit(); return 'submitted'; }
                return 'nobutton';
              }catch(e){ return 'err:' + e.message; }
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js) { result ->
            Log.d(TAG, "jw fill result=$result")
            if (result?.trim('"')?.startsWith("err") == true || result == "null") {
                openManualWithHint("教务系统登录页结构未能识别，请在网页中手动登录")
            }
        }
    }

    private fun handleJwResult(url: String) {
        if (!isJwPage(url)) {
            openManualWithHint("未确认教务系统登录结果，请在网页中确认")
            return
        }
        probeJwPage { probe ->
            when {
                probe == null -> openManualWithHint("未确认教务系统登录结果，请在网页中确认")

                probe.needLogin -> {
                    awaiting = Await.Nothing
                    _state.value = LoginUiState(
                        phase = LoginPhase.JwForm,
                        error = "教务系统未通过，请检查学号与密码",
                    )
                }

                else -> finishLogin()
            }
        }
    }
    /**
     * 探测教务系统页面的真实状态。
     *
     * 判定依据是「有没有**可见的**密码输入框」：有就是登录页，没有就已经进系统了。
     * 不能用 URL 判断——强智未登录时会把登录页直接渲染在目标地址上。
     */
    private fun probeJwPage(onResult: (JwProbe?) -> Unit) {
        val js = """
            (function(){
              try{
                var pw = Array.prototype.filter.call(
                  document.querySelectorAll('input[type=password]'),
                  function(i){ return i.offsetParent !== null && !i.disabled; });
                var body = document.body ? (document.body.innerText || '') : '';
                var t = body.trim();
                var ct = document.contentType || '';
                var html = document.documentElement ? document.documentElement.outerHTML : '';
                // 强智未登录时会直接返回 JSON 信封（如 {"flag1":2,"msgContent":"请先登录系统"}），
                // 那不是页面，同样说明没登录。
                var looksJson = t.length > 0 && t.length < 2000 &&
                                t.charAt(0) === '{' && t.charAt(t.length - 1) === '}';
                var frames = document.querySelectorAll('frameset,iframe').length;
                // 兜底：内容极短又没有框架的页面也不像真的进了系统
                var tooThin = t.length < 120 && frames === 0;
                return JSON.stringify({
                  needLogin: pw.length > 0 || looksJson || tooThin,
                  hasPassword: pw.length > 0,
                  title: (document.title || '').substring(0, 80),
                  textLen: t.length,
                  head: t.replace(/\s+/g, ' ').substring(0, 120),
                  contentType: ct,
                  frames: frames,
                  htmlHead: html.replace(/\s+/g, ' ').substring(0, 200)
                });
              }catch(e){ return 'ERR:' + e.message; }
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js) { raw ->
            val probe = runCatching {
                json.decodeFromString<JwProbe>(json.decodeFromString<String>(raw))
            }.getOrNull()
            Log.d(TAG, "jw probe -> $raw")
            onResult(probe)
        }
    }

    /**
     * 把教务系统登录页的表单结构打到 logcat（tag = JiaowuJw）。
     *
     * 用途：目前对这一步的页面结构只能靠通用规则去猜，
     * 用户跑一次就能拿到真实字段名，据此把选择器收紧。
     */
    private fun dumpJwLoginStructure(url: String) {
        val js = """
            (function(){
              try{
                var out = Array.prototype.slice.call(document.querySelectorAll('form,input,button,select'))
                  .map(function(e){
                    return e.tagName + (e.id ? '#'+e.id : '') +
                           (e.name ? '[name='+e.name+']' : '') +
                           (e.type ? ':'+e.type : '');
                  }).join(' | ');
                return (location.href + ' :: ' + out).substring(0, 1500);
              }catch(e){ return 'err:'+e.message; }
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js) { result ->
            Log.d("JiaowuJw", "登录页结构: $result")
        }
    }

    // ---------------- 收尾 ----------------

    /**
     * 收尾。**必须在主线程调用**——里面要读 `WebView.url` 与 `CookieManager`，
     * 这两个都是 WebView 的 API，在后台线程调会直接抛异常。
     * 真正耗时的持久化再交给 App 级作用域，避免随登录页销毁被取消。
     */
    private fun finishLogin() {
        val url = currentUrl()
        if (url != null) container.cookieJar.syncFromWebView(url)

        val credentials = pendingCredentials
        awaiting = Await.Nothing
        _state.value = LoginUiState(phase = LoginPhase.Done, webVisible = false)

        appLaunch {
            container.session.saveCredentials(credentials)
            val current = container.store.currentSettings()
            if (current.hostCode.isBlank()) {
                container.store.updateSettings { it.copy(hostCode = QzPaths.JSXSD_HOST_CODE) }
            }
            container.session.onLoggedIn(null, container.store.currentSettings())
            Log.d(TAG, "登录收尾完成，凭证已保存")
        }
    }

    private fun openManualWithHint(text: String) {
        if (_state.value.phase != LoginPhase.Manual) {
            resumePhase = currentFormPhase()
        }
        _state.value = LoginUiState(
            phase = LoginPhase.Manual,
            webVisible = true,
            hint = text,
        )
    }

    // ---------------- 工具 ----------------

    private fun currentUrl(): String? = webView?.url

    private fun phaseBeforeWorking(): LoginPhase = when (_state.value.phase) {
        LoginPhase.JwWorking -> LoginPhase.JwForm
        else -> LoginPhase.SsoForm
    }

    /** 当前应回到哪个表单（区分第一步 / 第二步）。 */
    private fun currentFormPhase(): LoginPhase = when (_state.value.phase) {
        LoginPhase.JwForm, LoginPhase.JwWorking -> LoginPhase.JwForm
        else -> LoginPhase.SsoForm
    }

    private fun isCasLoginPage(url: String): Boolean = url.contains("/cas/login")

    private fun isJwPage(url: String): Boolean = url.contains("/jsxsd/")

    /** 认证前所有页面都在 `/http/<宿主码>/` 之下；认证成功后门户会跳回自身根路径。 */
    private fun isPortalLoggedIn(url: String, vpnBase: String): Boolean {
        val base = vpnBase.trimEnd('/')
        if (!url.startsWith(base)) return false
        val path = url.removePrefix(base)
        if (path.startsWith("/http")) return false
        if (path.contains("/cas/")) return false
        if (path.startsWith("/login") && !path.contains("cas_login")) return false
        return true
    }

    private fun jsString(value: String): String =
        "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"

    private companion object {
        const val TAG = "JiaowuLogin"
    }
}
