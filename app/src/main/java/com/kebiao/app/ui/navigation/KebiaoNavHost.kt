package com.kebiao.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kebiao.app.data.import.ImportStateMachine
import com.kebiao.app.di.AppContainer
import com.kebiao.app.feature.import.ImportLoginBridge
import com.kebiao.app.feature.import.ImportScreen
import com.kebiao.app.feature.import.ImportViewModel
import com.kebiao.app.feature.semester.SemesterScreen
import com.kebiao.app.feature.semester.SemesterViewModel
import com.kebiao.app.feature.settings.SettingsScreen
import com.kebiao.app.feature.settings.SettingsViewModel
import com.kebiao.app.feature.today.CourseDetailScreen
import com.kebiao.app.feature.today.CourseDetailViewModel
import com.kebiao.app.feature.today.TodayScreen
import com.kebiao.app.feature.today.TodayViewModel
import com.kebiao.app.feature.week.WeekScreen
import com.kebiao.app.feature.week.WeekViewModel
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.MotionTokens
import com.kebiao.app.ui.theme.PredictivePopTransitions
import com.kebiao.app.ui.theme.pageExitBlur

/* =========================================================================
 * 导航宿主 + 过渡动画接线（Spec §8.1 / UIUX §5.2）
 *
 * 两套转场，按「目标目的地类型」自动择一（详情见 MotionTokens.Recipe 的 ①②）：
 *  - 同级页（今天 / 周视图 / 设置）：横向滑动，位移刻意小于层级页。
 *  - 层级页（学期管理 / 导入 / 课程详情）：层级推入，退出的那页再叠一层景深模糊。
 *
 * sizeTransform 全关（不做尺寸动画，避免额外开销）。
 * 预测性返回由 Navigation Compose 2.10 在 manifest 开启
 * android:enableOnBackInvokedCallback="true" 后自动接入（manifest 归后端）。
 * ========================================================================= */

@Composable
fun KebiaoNavHost(
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab = when {
        destination?.hasRoute(WeekRoute::class) == true -> TopLevelTab.WEEK
        destination?.hasRoute(SettingsRoute::class) == true -> TopLevelTab.SETTINGS
        else -> TopLevelTab.TODAY
    }
    val showBottomBar = destination?.isTopLevelTab() == true
    val env = LocalMotionEnvironment.current

    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = MotionTokens.Recipe.sheetEnter(),
                exit = MotionTokens.Recipe.sheetExit()
            ) {
                KebiaoBottomBar(
                    selected = selectedTab,
                    onSelect = { tab -> navController.navigateToTab(tab) }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            enterTransition = {
                if (targetState.destination.isTopLevelTab()) MotionTokens.Recipe.fadeThroughEnter(env.reducedMotion)
                else MotionTokens.Recipe.pushEnter(env.reducedMotion)
            },
            exitTransition = {
                if (initialState.destination.isTopLevelTab() && targetState.destination.isTopLevelTab()) {
                    MotionTokens.Recipe.fadeThroughExit(env.reducedMotion)
                } else {
                    MotionTokens.Recipe.pushExit(env.reducedMotion)
                }
            },
            popEnterTransition = { MotionTokens.Recipe.popEnter(env.reducedMotion) },
            popExitTransition = { MotionTokens.Recipe.popExit(env.reducedMotion) },
            // 库自带的侧滑返回会把当前页 scaleOut 到 0.7（朝中心收缩），这里整套换成纯平移，
            // 并按起手边缘决定滑出方向（从左起手向右走，从右起手向左走）。
            predictivePopEnterTransition = { swipeEdge -> PredictivePopTransitions.enter(swipeEdge, env.reducedMotion) },
            predictivePopExitTransition = { swipeEdge -> PredictivePopTransitions.exit(swipeEdge, env.reducedMotion) },
            sizeTransform = null
        ) {
            composable<TodayRoute> {
                val viewModel: TodayViewModel = viewModel(factory = todayFactory(container))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                val viewingTomorrow by viewModel.viewingTomorrow.collectAsStateWithLifecycle()
                Box(modifier = pageExitBlur()) {
                    TodayScreen(
                        state = state,
                        isRefreshing = refreshing,
                        viewingTomorrow = viewingTomorrow,
                        onRefresh = viewModel::refresh,
                        onToggleTomorrow = viewModel::toggleTomorrow,
                        onOpenWeek = { navController.navigateToTab(TopLevelTab.WEEK) },
                        onOpenImport = { navController.navigate(ImportRoute) },
                        onOpenSemesters = { navController.navigate(SemesterRoute) },
                        onCourseClick = { sessionId ->
                            navController.navigate(CourseDetailRoute(sessionId))
                        },
                        contentPadding = padding
                    )
                }
            }

            composable<WeekRoute> {
                val viewModel: WeekViewModel = viewModel(factory = weekFactory(container))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Box(modifier = pageExitBlur()) {
                    WeekScreen(
                        state = state,
                        onStepWeek = { delta ->
                            val total = (state as? com.kebiao.app.feature.week.WeekUiState.Content)
                                ?.totalWeeks ?: 1
                            viewModel.stepWeek(delta, total)
                        },
                        onGoToCurrentWeek = viewModel::goToCurrentWeek,
                        onOpenImport = { navController.navigate(ImportRoute) },
                        onCourseClick = { sessionId ->
                            navController.navigate(CourseDetailRoute(sessionId))
                        },
                        contentPadding = padding
                    )
                }
            }

            composable<SettingsRoute> {
                val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
                Box(modifier = pageExitBlur()) {
                    SettingsScreen(
                        state = state,
                        onReminderEnabledChange = settingsViewModel::setReminderEnabled,
                        onLeadMinutesChange = settingsViewModel::setReminderLeadMinutes,
                        onThemeModeChange = settingsViewModel::setThemeMode,
                        onShowNonCurrentWeekChange = settingsViewModel::setShowNonCurrentWeekCourses,
                        onOpenImport = { navController.navigate(ImportRoute) },
                        onOpenSemesters = { navController.navigate(SemesterRoute) },
                        contentPadding = padding
                    )
                }
            }

            composable<SemesterRoute> {
                val viewModel: SemesterViewModel = viewModel(factory = semesterFactory(container))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Box(modifier = pageExitBlur()) {
                    SemesterScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onSwitch = { id ->
                            viewModel.switchTo(id) { navController.popBackStack() }
                        },
                        onImportNew = { navController.navigate(ImportRoute) },
                        contentPadding = padding
                    )
                }
            }

            // 导入页刻意不加 pageExitBlur：页面主体是 WebView（AndroidView），
            // 给它套一层 RenderEffect 是全项目最贵的一次合成，且 surface 合成路径下
            // 可能出黑块。它的退出动效仍然有滑动 + scaleOut(0.96) 的景深近似。
            composable<ImportRoute> {
                val viewModel: ImportViewModel = viewModel(factory = importFactory(container))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ImportScreen(
                    state = state,
                    loginUrl = viewModel.loginUrl,
                    extractionScript = viewModel.extractionScript,
                    onBack = { navController.popBackStack() },
                    onStartLogin = viewModel::startLogin,
                    onPayloadExtracted = viewModel::onPayloadExtracted,
                    onConfirmImport = viewModel::confirmImport,
                    onRestart = viewModel::restart,
                    onDone = { navController.popBackStack() },
                    loginBridge = ImportLoginBridge(
                        onUrlChanged = viewModel::onPageUrlChanged,
                        onProbe = viewModel::onProbeResult,
                        onScheduleEntry = viewModel::onScheduleEntryFound,
                        onEntryClick = viewModel::onEntryClicked,
                        onExtractNow = viewModel::requestManualExtract,
                        onOpenSchedule = viewModel::openScheduleEntry,
                        onEntryLoaded = viewModel::onScheduleEntryLoaded,
                        onCopyStructure = viewModel::requestStructureDump,
                        onStructureDumped = viewModel::onStructureDumped,
                        onStructureDumpConsumed = viewModel::onStructureDumpConsumed,
                        onExtractFailure = viewModel::onExtractFailure,
                        onPollExhausted = viewModel::onPollExhausted
                    ),
                    contentPadding = padding
                )
            }

            composable<CourseDetailRoute> { entry ->
                val route = entry.toRoute<CourseDetailRoute>()
                val viewModel: CourseDetailViewModel =
                    viewModel(factory = courseDetailFactory(container, route.sessionId))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Box(modifier = pageExitBlur()) {
                    CourseDetailScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        contentPadding = padding
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * ViewModel 工厂。全部在这里装配，页面只拿现成的状态。
 * ------------------------------------------------------------------ */

private fun todayFactory(container: AppContainer) = viewModelFactory {
    initializer {
        TodayViewModel(
            semesterRepository = container.semesterRepository,
            scheduleRepository = container.scheduleRepository,
            timeProvider = container.timeProvider
        )
    }
}

private fun weekFactory(container: AppContainer) = viewModelFactory {
    initializer {
        WeekViewModel(
            semesterRepository = container.semesterRepository,
            scheduleRepository = container.scheduleRepository,
            timeProvider = container.timeProvider
        )
    }
}

private fun semesterFactory(container: AppContainer) = viewModelFactory {
    initializer { SemesterViewModel(container.semesterRepository) }
}

/**
 * 导入页工厂：每次进入本页都新建一个 WebView 数据源（登录会话是「一次性」的，
 * 不能跨导入复用），并交给数据层的 [ImportStateMachine] 编排
 * 「取数 -> 解析 -> 核对 -> 单事务提交」。流程实现只有这一份，UI 层不再自建状态机。
 */
private fun importFactory(container: AppContainer) = viewModelFactory {
    initializer {
        val source = container.newWebViewImportSource()
        val machine = ImportStateMachine(
            source = source,
            mapper = container.importMapper,
            committer = container.importCommitter,
            eventLog = container.localEventLog
        )
        ImportViewModel(machine = machine, webViewSource = source)
    }
}

private fun courseDetailFactory(container: AppContainer, sessionId: Long) = viewModelFactory {
    initializer {
        CourseDetailViewModel(
            semesterRepository = container.semesterRepository,
            scheduleRepository = container.scheduleRepository,
            timeProvider = container.timeProvider,
            sessionId = sessionId
        )
    }
}

/** 同级页切换：保持单实例 + 恢复各自滚动位置。 */
private fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
