package com.kebiao.app.feature.import

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.components.EmptyState
import com.kebiao.app.ui.components.ErrorState
import com.kebiao.app.ui.components.KebiaoGlassTopAppBar
import com.kebiao.app.ui.icons.KebiaoIcon
import com.kebiao.app.ui.icons.KebiaoIcons
import com.kebiao.app.ui.theme.KebiaoSpacing
import com.kebiao.app.ui.theme.LocalMotionEnvironment
import com.kebiao.app.ui.theme.LocalSemanticColors
import com.kebiao.app.ui.theme.MotionTokens
import com.kebiao.app.ui.theme.dialogBackdropBlur

/* =========================================================================
 * 页面五：导入 / WebView 登录（Spec §7 + UIUX §8）
 *
 * Idle -> LoggingIn -> Fetching -> Verifying -> Committing -> Success / Failed
 *
 * WebView 是「中继」：真人在系统 WebView 完成 SSO（含短信验证码），
 * 课刻不读密码字段、不注册 @JavascriptInterface（Spec §4.3）。
 * ========================================================================= */

/* 弹窗只可能在 Idle（未开始登录）时打开，因此把 blur 严格限制在 Idle：
 * 一旦进到 LoggingIn，这一层里是 WebView（AndroidView），给 AndroidView 套
 * RenderEffect 是全项目最贵的一次合成，surface 合成路径下还可能出黑块（KebiaoNavHost
 * 里导入页刻意不加 pageExitBlur 是同一个理由）。 */
private fun ImportUiState.canBlurForDialog(): Boolean = this is ImportUiState.Idle

@Composable
fun ImportScreen(
    state: ImportUiState,
    loginUrl: String,
    extractionScript: String,
    onBack: () -> Unit,
    onStartLogin: () -> Unit,
    onPayloadExtracted: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onRestart: () -> Unit,
    onDone: () -> Unit,
    /** 登录 WebView 的诊断上报与一次性指令通道（真机首验 H1/H2/H3 的产物）。 */
    loginBridge: ImportLoginBridge,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** P1 手动录入：仅在 caller 提供了写入通道时显示入口，避免出现点了没反应的假按钮。 */
    manualEditEnabled: Boolean = false,
    onSaveManualSession: (ManualSessionInput) -> Unit = {}
) {
    val env = LocalMotionEnvironment.current
    var showEditDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // 内容层：弹窗打开时给自己加一层高斯模糊 —— Modifier.blur 模糊的正是本节点的
        // 绘制内容，所以这就是真正的「弹窗背后的背景模糊」，不是近似。
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .dialogBackdropBlur(showEditDialog && state.canBlurForDialog())
        ) {
            KebiaoGlassTopAppBar(
                title = {
                    Text(
                        text = "导入课表",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        KebiaoIcon(
                            res = KebiaoIcons.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (manualEditEnabled && state is ImportUiState.Idle) {
                        IconButton(onClick = { showEditDialog = true }) {
                            KebiaoIcon(
                                res = KebiaoIcons.Add,
                                contentDescription = "手动新增课程",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    if (env.reducedMotion) {
                        (EnterTransition.None togetherWith ExitTransition.None).using(null)
                    } else {
                        (fadeIn(tween(200, easing = MotionTokens.Ease.Decelerate)) togetherWith
                            fadeOut(tween(150, easing = MotionTokens.Ease.Accelerate))).using(null)
                    }
                },
                label = "importStep",
                // LoggingIn 携带会频繁变化的诊断字段（当前 URL / 探针结论 / 指令计数）。
                // 若让它参与 contentKey，每次诊断更新都会被判定为"换了内容" ——
                // WebView 会被拆掉重建，SSO 会话当场丢失。登录态因此固定用一个字符串 key。
                contentKey = { current -> if (current is ImportUiState.LoggingIn) KEY_LOGGING_IN else current },
                modifier = Modifier.fillMaxSize()
            ) { current ->
                when (current) {
                    ImportUiState.Idle -> Column(Modifier.fillMaxSize()) {
                        PrivacyNotice()
                        EmptyState(
                            icon = KebiaoIcons.Lock,
                            title = "登录教务系统",
                            subtitle = "首次登录需要在教务系统绑定手机号并输入短信验证码。" +
                                "请在即将打开的页面里自己完成登录，课刻不会读取也不会保存你的密码。",
                            actionLabel = "开始登录",
                            onAction = onStartLogin
                        )
                    }

                    // 登录阶段内容全部在 ImportLoginStage：六个步骤分支已经把本文件
                    // 顶到 300 行门限，登录阶段的诊断/引导/终态自成一体更清楚。
                    is ImportUiState.LoggingIn -> ImportLoginStage(
                        state = current,
                        loginUrl = loginUrl,
                        extractionScript = extractionScript,
                        onPayloadExtracted = onPayloadExtracted,
                        bridge = loginBridge,
                        modifier = Modifier.fillMaxSize()
                    )

                    is ImportUiState.Fetching -> StepProgress(
                        title = current.step,
                        detail = "正在把页面里的课程逐条读出来，并解析周次与教室",
                        progress = current.progress
                    )

                    is ImportUiState.Verifying -> Column(Modifier.fillMaxSize()) {
                        ImportVerifyView(
                            preview = current.preview,
                            modifier = Modifier.weight(1f)
                        )
                        ImportVerifyActions(
                            onConfirm = onConfirmImport,
                            onBack = onRestart
                        )
                    }

                    ImportUiState.Committing -> StepProgress(
                        title = "正在写入本地课表",
                        detail = "整批一次事务提交，任一条失败都会整体回滚",
                        progress = null
                    )

                    is ImportUiState.Success -> EmptyState(
                        icon = KebiaoIcons.CheckCircle,
                        title = "导入完成",
                        subtitle = "已写入 ${current.courseCount} 门课、${current.sessionCount} 条上课安排",
                        actionLabel = "查看今天",
                        onAction = onDone
                    )

                    is ImportUiState.Failed -> ErrorState(
                        error = current.error,
                        retryable = current.retryable,
                        onRetry = onRestart
                    )
                }
            }
        }

        if (showEditDialog) {
            CourseEditDialog(
                onDismiss = { showEditDialog = false },
                onConfirm = { input ->
                    showEditDialog = false
                    onSaveManualSession(input)
                }
            )
        }
    }
}

/** 隐私说明：把「不读密码」讲清楚，这是本产品与竞品注册流程的核心差异。 */
@Composable
private fun PrivacyNotice() {
    val semantic = LocalSemanticColors.current
    Column(
        modifier = Modifier.padding(
            start = KebiaoSpacing.ScreenGutter,
            end = KebiaoSpacing.ScreenGutter,
            top = KebiaoSpacing.X3
        )
    ) {
        Text(
            text = "接下来会发生什么",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(KebiaoSpacing.X1))
        listOf(
            "1. 打开上海电机学院统一身份认证页面",
            "2. 你自己输入学号与密码，课刻不接触密码字段",
            "3. 登录成功后自己点进「我的课表」，再点一次「读取课表」",
            "4. 先给你核对，确认后才写入本地"
        ).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(KebiaoSpacing.X2))
        Text(
            text = "课表只存在本机数据库，不上传任何第三方服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = semantic.success
        )
    }
}

/** 登录态在 `AnimatedContent` 里的固定 key：见上方 contentKey 的注释。 */
private const val KEY_LOGGING_IN = "import-step-logging-in"

/** 抓取 / 提交中的进度屏。进度未知时用不确定进度条，但步数文案一定给。 */
@Composable
private fun StepProgress(title: String, detail: String, progress: Float?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = KebiaoSpacing.ScreenGutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(KebiaoSpacing.X2))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(KebiaoSpacing.X5))
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }
    }
}
