package com.kebiao.app.data.import

import android.content.ContentResolver
import android.net.Uri
import com.kebiao.app.core.AppError
import com.kebiao.app.core.AppResult
import com.kebiao.app.domain.model.ImportSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * JSON 文件导入源（兜底路径 b，ADR-003）。
 *
 * 用 SAF 拿到的 `content://` Uri 读文件，不需要任何存储权限。
 * 与 WebView 路径共用 [ScheduleJsonCodec]，因此"校验、周次解析、核对视图"只有一份实现。
 */
class JsonFileImportSource(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val payloadStore: PayloadStore,
) : ImportSource {

    override val kind: ImportSourceKind = ImportSourceKind.JSON_FILE

    override suspend fun fetch(): AppResult<RawSchedule> = withContext(Dispatchers.IO) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: FileNotFoundException) {
            return@withContext AppResult.Failure(AppError.NotFound("导入文件（可能已被移动或删除）", e))
        } catch (e: SecurityException) {
            return@withContext AppResult.Failure(AppError.Storage(e))
        } catch (e: IOException) {
            return@withContext AppResult.Failure(AppError.Storage(e))
        }

        if (text.isNullOrBlank()) {
            return@withContext AppResult.Failure(
                AppError.Parse(
                    com.kebiao.app.core.ParseStage.JSON_SCHEMA,
                    "导入文件为空，未读取到任何内容",
                )
            )
        }

        val payloadRef = try {
            payloadStore.save(prefix = "schedule-json", content = text)
        } catch (e: IOException) {
            // 留存失败不阻断导入本身，但必须让用户能看见（写进核对提示的 message 由上层决定）。
            null
        }

        ScheduleJsonCodec.decode(text = text, kind = kind, payloadRef = payloadRef)
    }
}
