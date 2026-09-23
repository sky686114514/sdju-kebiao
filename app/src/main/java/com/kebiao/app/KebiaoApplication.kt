package com.kebiao.app

import android.app.Application
import com.kebiao.app.di.AppContainer

/**
 * 应用入口 —— **只装配，零业务**（Spec 第 10 节 / code-organization 第 4 条）。
 *
 * 全部实际工作交给 [AppContainer]（提供依赖）与 `AppBootstrap`（启动副作用）。
 * 这里刻意不写任何 `if`/查库/网络逻辑，使"入口文件行数 < 20"这条自检项天然成立。
 */
class KebiaoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.from(this).bootstrap()
    }
}
