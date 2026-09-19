package com.huhst.jiaowu.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.huhst.jiaowu.ui.nav.Routes
import com.huhst.jiaowu.ui.nav.TopDestination
import com.huhst.jiaowu.ui.screens.grades.GradesScreen
import com.huhst.jiaowu.ui.screens.more.MoreScreen
import com.huhst.jiaowu.ui.components.AppBackground
import com.huhst.jiaowu.ui.screens.profile.AboutScreen
import com.huhst.jiaowu.ui.screens.profile.PreferencesScreen
import com.huhst.jiaowu.ui.screens.profile.ProfileScreen
import com.huhst.jiaowu.ui.screens.progress.ProgressDetailScreen
import com.huhst.jiaowu.ui.screens.progress.ProgressScreen
import com.huhst.jiaowu.ui.screens.schedule.CourseDetailScreen
import com.huhst.jiaowu.ui.screens.schedule.ScheduleScreen
import com.huhst.jiaowu.ui.vm.AppViewModel
import com.huhst.jiaowu.ui.vm.SyncUi

/**
 * 主界面：底部导航 + 路由。
 *
 * 过渡动效统一走 [tween] 缓动，**不使用弹簧**——
 * 对应 MotionScheme.standard() 的「平滑、不回弹」。
 */
@Composable
fun MainScaffold(vm: AppViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val syncState by vm.sync.collectAsStateWithLifecycle()

    LaunchedEffect(syncState) {
        when (val s = syncState) {
            is SyncUi.Done -> {
                snackbarHostState.showSnackbar(s.message)
                vm.dismissSyncMessage()
            }

            is SyncUi.Error -> {
                snackbarHostState.showSnackbar(s.message)
                vm.dismissSyncMessage()
            }

            else -> Unit
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        // 容器透明，让自定义背景能透上来；未设置背景时 AppBackground 会铺 surface
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in Routes.topLevel) {
                JiaowuNavigationBar(
                    currentRoute = currentRoute,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            AppBackground(settings.scheduleBackground)

            NavHost(
                navController = navController,
                startDestination = Routes.MORE,
                modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 10 }
            },
            popExitTransition = {
                fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 10 }
            },
        ) {
            composable(Routes.MORE) { MoreScreen(vm, navController) }
            composable(Routes.SCHEDULE) { ScheduleScreen(vm, navController) }
            composable(Routes.PROFILE) { ProfileScreen(vm, navController) }

            // 「其他功能」下的二级页面
            composable(Routes.GRADES) { GradesScreen(vm, navController) }
            composable(Routes.PROGRESS) { ProgressScreen(vm, navController) }

            composable(Routes.SETTINGS) { PreferencesScreen(vm, navController) }
            composable(Routes.ABOUT) { AboutScreen(vm, navController) }

            composable(
                route = Routes.PROGRESS_DETAIL,
                arguments = listOf(navArgument("courseId") { type = NavType.StringType }),
            ) { entry ->
                ProgressDetailScreen(
                    vm = vm,
                    courseId = entry.arguments?.getString("courseId").orEmpty(),
                    navController = navController,
                )
            }

            composable(
                route = Routes.COURSE_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                CourseDetailScreen(
                    vm = vm,
                    sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                    navController = navController,
                )
            }
            }
        }
    }
}

@Composable
private fun JiaowuNavigationBar(
    currentRoute: String?,
    onSelect: (TopDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
