package com.kebiao.app.data.import

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 原始课表载荷的本地留存（"保留现场"）。
 *
 * 为什么要存：AC-04 要求解析失败时"保留现场"。教务改版后，
 * 只有把原始 HTML 留在本地，才能离线定位到底是哪一步变了，而不用重新登录一次。
 *
 * 存储位置与隐私（Spec 第 10 节）：只写 App 私有目录 `filesDir/import-payload/`，
 * **不上传任何第三方**；不写入任何密码字段（App 从不读取密码字段）。
 */
interface PayloadStore {

    /**
     * 保存一份原始载荷。
     * @return 该文件的绝对路径，写入 `import_batches.payload_ref`
     */
    suspend fun save(prefix: String, content: String): String
}

class FilePayloadStore(private val context: Context) : PayloadStore {

    override suspend fun save(prefix: String, content: String): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) {
            throw java.io.IOException("无法创建载荷留存目录: ${dir.absolutePath}")
        }
        val file = File(dir, "$prefix-${System.currentTimeMillis()}.txt")
        file.writeText(content)
        pruneIfNeeded(dir)
        file.absolutePath
    }

    /**
     * 只保留最近 [MAX_FILES] 份载荷。
     * 教务课表页面单份在数百 KB 量级，无上限留存会在几个学期后白占几十 MB。
     */
    private fun pruneIfNeeded(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_FILES).forEach { it.delete() }
    }

    private companion object {
        const val DIR_NAME = "import-payload"
        const val MAX_FILES = 5
    }
}
