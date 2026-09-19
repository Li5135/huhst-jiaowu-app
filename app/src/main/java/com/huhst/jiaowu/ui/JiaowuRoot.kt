package com.huhst.jiaowu.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huhst.jiaowu.data.SessionState
import com.huhst.jiaowu.di.AppContainer
import com.huhst.jiaowu.ui.components.LoadingPane
import com.huhst.jiaowu.ui.screens.login.LoginScreen
import com.huhst.jiaowu.ui.vm.AppViewModel

/**
 * 应用根节点：先判定登录态，再决定展示登录页还是主界面。
 */
@Composable
fun JiaowuRoot(container: AppContainer) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
    val session by vm.sessionState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (session) {
            SessionState.Unknown -> LoadingPane(label = "正在准备…")
            SessionState.LoggedOut -> LoginScreen(container)
            SessionState.LoggedIn -> MainScaffold(vm)
        }
    }
}
