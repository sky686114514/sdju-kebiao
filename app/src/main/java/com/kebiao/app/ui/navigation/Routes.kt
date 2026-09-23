package com.kebiao.app.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.serialization.Serializable

/* =========================================================================
 * 类型安全路由（Navigation Compose 2.10.1 + kotlinx-serialization）
 *
 * 顶层目的地：today / week / settings（底部导航承载）
 * 层级目的地：semester / import / courseDetail（全屏子流程，shared-axis X 推入）
 * ========================================================================= */

@Serializable
data object TodayRoute

@Serializable
data object WeekRoute

@Serializable
data object SettingsRoute

@Serializable
data object SemesterRoute

@Serializable
data object ImportRoute

@Serializable
data class CourseDetailRoute(val sessionId: Long)

/** 底部导航的三个同级页。同级页之间用 fade-through，不做水平位移。 */
enum class TopLevelTab(
    val label: String,
    val route: Any
) {
    TODAY("今天", TodayRoute),
    WEEK("周视图", WeekRoute),
    SETTINGS("设置", SettingsRoute)
}

/** 该目的地是否为底部导航的同级页。 */
fun NavDestination.isTopLevelTab(): Boolean =
    hasRoute(TodayRoute::class) || hasRoute(WeekRoute::class) || hasRoute(SettingsRoute::class)
