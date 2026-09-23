package com.kebiao.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring as ComposeSpring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/* =========================================================================
 * 课刻 · 动效契约 MotionTokens（来源 docs/design-tokens.kt §7 + docs/UIUX.md §5）
 *
 * 全项目硬约束（违反即退回）：
 *  - 时长阶梯只有 100 / 150 / 250 / 350 / 500 ms，150 为收敛基准值。
 *  - 只允许 4 条三次贝塞尔缓动，全部不含过冲（控制点 y 恒在 0..1）。
 *  - 所有弹簧 dampingRatio 恒为 1.0（DampingRatioNoBouncy），数学上不存在回弹。
 *    严禁 Spring.DampingRatioMediumBouncy / HighBouncy，
 *    严禁 cubic-bezier(0.68, -0.55, 0.265, 1.55) 这类下冲回弹的玩具感曲线。
 *  - 只允许动画 alpha / translationX,Y / scaleX,Y / rotationZ（全部走 graphicsLayer）。
 *    禁止动画 width / height / top / left / margin / padding。
 *  - 同时动画元素 <= 3；滚动期间动画数恒为 0；reduced motion 关闭 stagger 与脉冲。
 *
 * UI 层调用 MotionTokens.Recipe.*，不自己写时长与曲线。
 *
 * 页面级配方（①②③）一律带 reducedMotion 入参：为真时**位移与缩放全部去掉**，
 * 只留 <=150ms 的淡入淡出 —— 前庭敏感用户的不适主要来自位移，不是透明度。
 * ========================================================================= */

object MotionTokens {

    /** 时长阶梯（毫秒）。150 是全项目收敛基准值。 */
    object Duration {
        const val Instant = 100
        const val Fast = 150
        const val Medium = 250
        const val Slow = 350
        const val Longer = 500

        /** 列表项入场单卡时长。 */
        const val ListItem = 180

        /** 旧列表退场：比入场快约 2 倍（UIUX §5.3）。 */
        const val StaleExit = 140

        /** 同级页出场，刻意比入场短很多，形成 fade-through 的层次。 */
        const val TabExit = 90

        /** 状态变「进行中」的颜色与边框过渡。 */
        const val StateChange = 300

        /** 骨架屏 shimmer 周期。 */
        const val Shimmer = 1200
    }

    /** 缓动曲线。全部为三次贝塞尔，控制点 y 在 0..1 内，无过冲。 */
    object Ease {
        val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
        val Decelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
        val Accelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

        /** 骨架屏高光扫过，唯一允许的线性缓动。 */
        val Linear: Easing = CubicBezierEasing(0.0f, 0.0f, 1.0f, 1.0f)

        val Default: Easing = Standard
    }

    /** 弹簧参数。dampingRatio 必须为 DampingRatioNoBouncy。刚度三档 1500 / 400 / 200。 */
    object Spring {
        private const val NO_BOUNCY = ComposeSpring.DampingRatioNoBouncy

        /** 1500：点按反馈、滑块位移。 */
        val Snappy: FiniteAnimationSpec<Float> =
            spring(dampingRatio = NO_BOUNCY, stiffness = 1500f)

        /** 400：容器变化与重排。 */
        val Spatial: FiniteAnimationSpec<Float> =
            spring(dampingRatio = NO_BOUNCY, stiffness = 400f)

        /** 200：高度展开折叠。 */
        val Gentle: FiniteAnimationSpec<Float> =
            spring(dampingRatio = NO_BOUNCY, stiffness = 200f)

        /** animateContentSize 需要 IntSize 规格（HeightAnimation 用）。 */
        val SpatialSize: FiniteAnimationSpec<IntSize> =
            spring(dampingRatio = NO_BOUNCY, stiffness = 400f)

        /** 高度展开折叠（卡内「全部时间地点」）用，刚度 200。 */
        val GentleSize: FiniteAnimationSpec<IntSize> =
            spring(dampingRatio = NO_BOUNCY, stiffness = 200f)

        /**
         * `Modifier.animateItem(placementSpec = ...)` 需要 `FiniteAnimationSpec<IntOffset>`
         * （LazyItemScope 的位置动画以像素偏移为量纲），与 [SpatialSize]（IntSize）不可互换。
         * 刚度与 [Spatial] 保持一致：400，NoBouncy。
         */
        val SpatialOffset: FiniteAnimationSpec<IntOffset> =
            spring(dampingRatio = NO_BOUNCY, stiffness = 400f)
    }

    /** 全局硬约束。 */
    object Rules {
        /** 同时运行的动画元素上限。 */
        const val MaxConcurrentAnimatedElements = 3
        /** 卡片入场只在首屏静止时播一次，滚动期间 0 动画。 */
        const val ListItemStaggerDelayMs = 60
        const val ListItemStaggerCap = 6
        const val ListItemStaggerDurationMs = 180
        /** 脉冲周期（进行中状态）。 */
        const val PulsePeriodMs = 1600
        /** 空状态总时长（图标 260 延时 160）。 */
        const val EmptyStateTotalMs = 420
        /** 学期切换编排总预算。 */
        const val SemesterSwitchBudgetMs = 500
        /** 骨架卡数量与 shimmer 高光带宽度比例。 */
        const val SkeletonCardCount = 3
        const val ShimmerHighlightFraction = 0.24f
    }

    /** 编排好的动画配方。UI 层直接调用。 */
    object Recipe {

        /* 位移比例的分母：同级页进出不对称（进 1/4 出 1/8），层级推进退对称（各 1/3）。 */
        private const val TabSlideIn = 4
        private const val TabSlideOut = 8
        private const val PushSlide = 3

        /** 景深：退出 / 返回再现的一页缩到的比例。离 1 越远越"远"，超过 0.9 就开始像弹跳。 */
        private const val DepthScale = 0.96f

        /* 共享规格：同一条（时长 + 缓动）组合只写一处，泛型自变量决定它只能套在对应量纲上。 */
        private val fastSlide = tween<IntOffset>(Duration.Fast, easing = Ease.Accelerate)
        private val medSlide = tween<IntOffset>(Duration.Medium, easing = Ease.Decelerate)
        private val slowSlide = tween<IntOffset>(Duration.Slow, easing = Ease.Emphasized)
        private val fastIn = tween<Float>(Duration.Fast, easing = Ease.Decelerate)
        private val fastOut = tween<Float>(Duration.Fast, easing = Ease.Accelerate)
        private val medIn = tween<Float>(Duration.Medium, easing = Ease.Decelerate)
        private val slowIn = tween<Float>(Duration.Slow, easing = Ease.Decelerate)
        private val slowScale = tween<Float>(Duration.Slow, easing = Ease.Emphasized)

        /** 与 [pushEnter] 的 fadeIn 参数完全相同（350ms Decelerate），不是巧合而是镜像要求：
         *  前景页退出必须和它进入时同快慢。单独起名而不用 slowIn，是因为它服务于淡出。 */
        private val slowFadeMirror = tween<Float>(Duration.Slow, easing = Ease.Decelerate)

        /* --- ① 页面切换：底部导航同级页（今日课程 / 周视图 / 设置） ---
         * 横向滑动（A1）：新页从右进 1/4 宽，旧页向左退 1/8 宽并更快消失。
         * 位移量刻意小于层级推入（1/3） —— 这三个页是平等的，位移过大会被误读成换层。
         * 去掉了原来的 scaleIn(0.94)：同一帧叠加两个轴的运动量，眼睛读成"脏/糊"而非"层次"。
         */
        fun fadeThroughEnter(reducedMotion: Boolean = false): EnterTransition =
            if (reducedMotion) fadeIn(fastIn)
            else slideInHorizontally(medSlide) { it / TabSlideIn } + fadeIn(medIn)

        fun fadeThroughExit(reducedMotion: Boolean = false): ExitTransition =
            if (reducedMotion) fadeOut(fastOut)
            else slideOutHorizontally(fastSlide) { -it / TabSlideOut } + fadeOut(fastOut)

        /* --- ② 页面切换：层级推入（课程详情 / 学期管理 / 导入） ---
         * A2：位移从原来的 1/4 与 1/6 统一加到 1/3 —— 1/4 以下与 Android 自身的手势
         * 边界混在一起，用户分不清"App 在换页"还是"自己没拖到位"。
         * 退出的一页额外 scaleOut 到 0.96：位移负责"走了"，缩放负责"退到后面去"。
         * 推入的一页刻意不做 scaleIn —— 它本来就在最上层，先缩再放大会读成回弹。
         */
        fun pushEnter(reducedMotion: Boolean = false): EnterTransition =
            if (reducedMotion) fadeIn(fastIn)
            else slideInHorizontally(slowSlide) { it / PushSlide } + fadeIn(slowIn)

        fun pushExit(reducedMotion: Boolean = false): ExitTransition =
            if (reducedMotion) fadeOut(fastOut)
            else slideOutHorizontally(slowSlide) { -it / PushSlide } + fadeOut(fastOut) +
                scaleOut(slowScale, targetScale = DepthScale)

        /* --- 返回：必须是进入的严格镜像 ---
         * 用户真机反馈「返回时动画太丑」，成因是返回的前景页多做了两件事：
         * 它加了 scaleOut(0.96)（被读成"被按扁/被吸走"而不是"滑回去"），且只用 150ms
         * 就淡掉了（页面才滑到 1/3 就快透明，两页半透明叠加 = 糊）。
         * 规则：popExit 是 pushEnter 的镜像，popEnter 是 pushExit 的镜像 ——
         * 位移取反，其余（时长、缓动、有无缩放）逐项对齐，不许额外加戏。
         */
        fun popEnter(reducedMotion: Boolean = false): EnterTransition =
            if (reducedMotion) fadeIn(fastIn)
            else slideInHorizontally(slowSlide) { -it / PushSlide } + fadeIn(fastIn) +
                scaleIn(slowScale, initialScale = DepthScale)

        fun popExit(reducedMotion: Boolean = false): ExitTransition =
            if (reducedMotion) fadeOut(fastOut)
            else slideOutHorizontally(slowSlide) { it / PushSlide } + fadeOut(slowFadeMirror)

        /* --- ②b 今天 <-> 明天：同一份列表换数据，横向滑动（A3） ---
         * 位移方向由 [DaySwitchMotion] 给定（time flows rightward），这里只负责时长与曲线。
         */
        fun daySwitchEnter(goingForward: Boolean, reducedMotion: Boolean = false): EnterTransition =
            if (reducedMotion) fadeIn(fastIn)
            else slideInHorizontally(medSlide) { DaySwitchMotion.enterOffset(goingForward, it).x } + fadeIn(medIn)

        fun daySwitchExit(goingForward: Boolean, reducedMotion: Boolean = false): ExitTransition =
            if (reducedMotion) fadeOut(fastOut)
            else slideOutHorizontally(fastSlide) { DaySwitchMotion.exitOffset(goingForward, it).x } + fadeOut(fastOut)

        /* --- ③ 内容更新：切换学期时旧列表退场（140ms，比入场快约 2 倍） --- */
        fun staleListExit(): ExitTransition =
            fadeOut(tween(Duration.StaleExit, easing = Ease.Accelerate)) +
                slideOutVertically(tween(Duration.StaleExit, easing = Ease.Accelerate)) { -it / 24 }

        /** 学期 Chip 竖向翻页：旧的向上走，新的从下进（UIUX §5.3，150ms）。 */
        fun semesterChipIn(): EnterTransition =
            slideInVertically(tween(Duration.Fast, easing = Ease.Decelerate)) { it } +
                fadeIn(tween(Duration.Fast, easing = Ease.Decelerate))

        fun semesterChipOut(): ExitTransition =
            slideOutVertically(tween(Duration.StaleExit, easing = Ease.Accelerate)) { -it } +
                fadeOut(tween(Duration.StaleExit, easing = Ease.Accelerate))

        /** 新课程列表容器整体入场（UIUX §5.3 的 AnimatedContent 分支）。 */
        fun semesterListIn(): EnterTransition =
            slideInVertically(tween(Duration.Fast, easing = Ease.Decelerate)) { it / 8 } +
                fadeIn(tween(Duration.Fast, easing = Ease.Decelerate))

        /* --- ④ 列表项入场（配合 stagger 延迟使用） --- */
        fun listItemEnter(staggerDelayMs: Int = 0): EnterTransition =
            fadeIn(
                tween(Rules.ListItemStaggerDurationMs, easing = Ease.Decelerate, delayMillis = staggerDelayMs)
            ) + slideInVertically(
                tween(Rules.ListItemStaggerDurationMs, easing = Ease.Decelerate, delayMillis = staggerDelayMs)
            ) { it / 6 }

        /** 封顶后的错峰延迟：index 超过 6 之后不再累加。 */
        fun staggerDelayFor(index: Int): Int =
            index.coerceAtMost(Rules.ListItemStaggerCap) * Rules.ListItemStaggerDelayMs

        /* --- ⑤ 空状态：从有课变今天没课 --- */
        fun emptyStateIconEnter(): EnterTransition =
            scaleIn(tween(260, easing = Ease.Emphasized), initialScale = 0.88f) +
                fadeIn(tween(260, easing = Ease.Decelerate))

        fun emptyStateTextEnter(): EnterTransition =
            slideInVertically(tween(260, easing = Ease.Decelerate, delayMillis = 160)) { it / 8 } +
                fadeIn(tween(260, easing = Ease.Decelerate, delayMillis = 160))

        /* --- ⑥ 周视图：回到本周按钮 --- */
        fun backToThisWeekEnter(): EnterTransition =
            scaleIn(tween(200, easing = Ease.Emphasized), initialScale = 0.8f) +
                fadeIn(tween(200, easing = Ease.Decelerate))

        fun backToThisWeekExit(): ExitTransition =
            scaleOut(tween(120, easing = Ease.Accelerate), targetScale = 0.8f) +
                fadeOut(tween(120, easing = Ease.Accelerate))

        /* --- ⑦ 状态 pill：进行中标签横向展开，不抢视线 --- */
        fun statusPillEnter(): EnterTransition =
            expandHorizontally(tween(200, easing = Ease.Emphasized)) +
                fadeIn(tween(200, easing = Ease.Decelerate, delayMillis = 100))

        /* --- ⑧ BottomSheet --- */
        fun sheetEnter(): EnterTransition =
            slideInVertically(tween(Duration.Slow, easing = Ease.Emphasized)) { it }

        fun sheetExit(): ExitTransition =
            slideOutVertically(tween(200, easing = Ease.Accelerate)) { it }

        /* --- ⑨ 刷新完成：新出现的卡片 ---
         * 【已登记缺口 · team-lead 裁决 2】token 已定义，但**当前无接线点**（刻意如此）。
         *   - 为什么没接线：本 App 的下拉刷新只是"重新订阅同一条 Room 查询"，几乎不产生新项，
         *     触发条件不成立；列表插入/删除/重排的真实动效已由 `Modifier.animateItem()` 覆盖。
         *   - 唯一会新增项的场景是手动编辑课程，属低频操作，可由 `source = MANUAL` 直接识别。
         *   - 如需启用：在 TodayCourseList 引入跨 emission 的 sessionId 差集，用差集标记"新出现"的
         *     卡片并给它本配方。切勿在 ViewModel 里缓存原始数据来比对 —— 那会制造第二真相源。
         */
        fun refreshedItemEnter(): EnterTransition =
            fadeIn(tween(Duration.ListItem, easing = Ease.Decelerate)) +
                scaleIn(tween(Duration.ListItem, easing = Ease.Decelerate), initialScale = 0.96f)

        /* --- ⑩ 周视图网格落位后淡入（掩盖重算白闪） --- */
        fun weekGridIn(): EnterTransition =
            fadeIn(tween(160, easing = Ease.Decelerate))

        /* --- ⑪ 状态切换：颜色过渡（即将开始 -> 进行中） --- */
        fun <T> stateChange(): FiniteAnimationSpec<T> = tween(Duration.StateChange, easing = Ease.Standard)

        fun <T> quickColorChange(): FiniteAnimationSpec<T> = tween(200, easing = Ease.Standard)

        /* --- ⑫ 骨架屏 -> 真实内容的交叉淡入 --- */
        fun <T> skeletonCrossfade(): FiniteAnimationSpec<T> = tween(200, easing = Ease.Decelerate)
    }
}
