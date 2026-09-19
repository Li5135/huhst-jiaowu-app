package com.huhst.jiaowu.ui.screens.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huhst.jiaowu.ui.icons.AppIcons
import com.huhst.jiaowu.di.AppContainer

/**
 * 登录页。
 *
 * ## 为什么是两步
 *
 * 学校的结构是嵌套的：统一身份认证（正方 CAS）与教务系统（强智 jsxsd）
 * 位于**两台不同的内网主机**，是两套独立系统，密码也互不相同。
 * 所以必须先过认证进 WebVPN，再进教务系统登录一次。用户名同为学号。
 *
 * ## 界面与提交的分工
 *
 * 界面是原生的居中表单；提交交给页面自己——一个不可见的 WebView 把值填进
 * 真实表单并点真实的登录按钮，这样学校的密码加密 JS 与跳转链都原样生效。
 * 自动化走不通时（验证码、改版）退化成把网页露出来让用户手动登。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(container: AppContainer) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val controller = remember(container) { LoginController(container, scope) }
    val state by controller.state.collectAsStateWithLifecycle()

    val saved = remember { controller.prefill() }

    var ssoUser by rememberSaveable { mutableStateOf(saved.ssoUser) }
    var ssoPassword by rememberSaveable { mutableStateOf(saved.ssoPassword) }
    var jwUser by rememberSaveable { mutableStateOf(saved.jwUser.ifBlank { saved.ssoUser }) }
    var jwPassword by rememberSaveable { mutableStateOf(saved.jwPassword) }
    var remember by rememberSaveable { mutableStateOf(if (saved.ssoUser.isBlank()) true else saved.remember) }

    var webView by remember { mutableStateOf<WebView?>(null) }

    // 供后台线程回调用：shouldInterceptRequest 不能直接读 WebView.url
    val pageUrlHolder = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }

    // 已记住密码时自动登录，省去每次重登都要手动敲。
    // 各只尝试一次——失败后停回表单显示错误，不会反复重试造成循环。
    var autoSsoTried by rememberSaveable { mutableStateOf(false) }
    var autoJwTried by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            LoginPhase.SsoForm -> {
                if (!autoSsoTried && ssoUser.isNotBlank() && ssoPassword.isNotBlank()) {
                    autoSsoTried = true
                    controller.submitSso(ssoUser, ssoPassword, remember)
                }
            }

            LoginPhase.JwForm -> {
                if (!autoJwTried && jwUser.isNotBlank() && jwPassword.isNotBlank()) {
                    autoJwTried = true
                    controller.submitJw(jwUser, jwPassword, remember)
                }
            }

            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.unbind()
            webView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 后台执行登录的 WebView。表单显示时它被不透明的表单层完全盖住；
        // 只有退化为手动模式时才露出来。
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = false
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.userAgentString = DESKTOP_LIKE_MOBILE_UA

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            // shouldInterceptRequest 在后台线程回调，不能在那里读 WebView.url，
                            // 所以主线程把当前地址存进原子引用供它使用。
                            pageUrlHolder.set(url)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            val current = url ?: return
                            pageUrlHolder.set(current)
                            controller.onPageFinished(current)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            err: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                controller.onPageError(err.description?.toString() ?: "网页加载失败")
                            }
                        }

                        /** 兜底代理内网静态资源（正常情况下 WebVPN 自己会改写，本方法不触发）。 */
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? =
                            container.assetInterceptor.intercept(request, pageUrlHolder.get())
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) = Unit
                    }

                    loadUrl("about:blank")
                }.also {
                    webView = it
                    controller.bind(it)
                }
            },
        )

        if (state.webVisible) {
            ManualOverlay(
                hint = state.hint,
                onDone = { controller.confirmManual() },
                onBack = { controller.backToForm() },
            )
        } else {
            // 不透明表单层盖住 WebView
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                val step = if (state.phase == LoginPhase.JwForm || state.phase == LoginPhase.JwWorking) 2 else 1
                val working = state.phase == LoginPhase.SsoWorking ||
                    state.phase == LoginPhase.JwWorking

                LoginForm(
                    step = step,
                    working = working,
                    error = state.error,
                    hint = state.hint,
                    userId = if (step == 1) ssoUser else jwUser,
                    onUserIdChange = { if (step == 1) ssoUser = it else jwUser = it },
                    password = if (step == 1) ssoPassword else jwPassword,
                    onPasswordChange = {
                        if (step == 1) ssoPassword = it else jwPassword = it
                    },
                    remember = remember,
                    onRememberChange = { remember = it },
                    onSubmit = {
                        if (step == 1) {
                            controller.submitSso(ssoUser, ssoPassword, remember)
                        } else {
                            controller.submitJw(jwUser, jwPassword, remember)
                        }
                    },
                    onManual = { controller.openManual() },
                )
            }
        }
    }
}

@Composable
private fun LoginForm(
    step: Int,
    working: Boolean,
    error: String?,
    hint: String,
    userId: String,
    onUserIdChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    remember: Boolean,
    onRememberChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onManual: () -> Unit,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        // 上下左右都居中：表单落在屏幕正中间
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepIndicator(current = step)

        Spacer(Modifier.height(28.dp))

        Icon(
            imageVector = AppIcons.School,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (step == 1) "统一身份认证" else "教务系统",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (step == 1) {
                "第 1 步，共 2 步 · 用于进入学校 WebVPN"
            } else {
                "第 2 步，共 2 步 · 教务系统与统一身份认证是两套密码"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Column(Modifier.widthIn(max = 360.dp)) {
            OutlinedTextField(
                value = userId,
                onValueChange = onUserIdChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
                singleLine = true,
                label = { Text("学号") },
                placeholder = { Text("请输入学号") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.small,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
                singleLine = true,
                label = { Text("密码") },
                placeholder = { Text("请输入密码") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                AppIcons.VisibilityOff
                            } else {
                                AppIcons.Visibility
                            },
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onSubmit()
                }),
                shape = MaterialTheme.shapes.small,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = remember,
                    onCheckedChange = onRememberChange,
                    enabled = !working,
                )
                Text(
                    text = "记住密码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            } else if (hint.isNotBlank() && working) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    keyboard?.hide()
                    onSubmit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !working,
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("正在登录…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("登录", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onManual,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("改用网页登录")
            }
        }
    }
}

/** 两步进度指示：两个圆点，已过的实心且加宽。 */
@Composable
private fun StepIndicator(current: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(2) { index ->
            val active = index < current
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (active) 24.dp else 10.dp)
                    .clip(CircleShape),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    content = {},
                )
            }
        }
    }
}

/** 手动兜底时的顶部提示条。 */
@Composable
private fun ManualOverlay(
    hint: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDone) { Text("已完成") }
            }
        }
        // 下方露出 WebView（在 Box 底层），这里只占位
        Spacer(Modifier.weight(1f))
    }
}

/** 标准 Chrome for Android UA（不含 WebView 的 "; wv" 标记）。 */
private const val DESKTOP_LIKE_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
