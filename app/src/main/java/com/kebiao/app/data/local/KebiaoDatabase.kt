package com.kebiao.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.kebiao.app.data.local.converter.Converters
import com.kebiao.app.data.local.dao.ClassSessionDao
import com.kebiao.app.data.local.dao.CourseDao
import com.kebiao.app.data.local.dao.ImportBatchDao
import com.kebiao.app.data.local.dao.SemesterDao
import com.kebiao.app.data.local.entity.ClassSessionEntity
import com.kebiao.app.data.local.entity.CourseEntity
import com.kebiao.app.data.local.entity.ImportBatchEntity
import com.kebiao.app.data.local.entity.SemesterEntity

/**
 * 课刻本地数据库。
 *
 * 配置（Spec 第 6 节）：`exportSchema = true`（schema JSON 进版本管理）、
 * 显式 `Migration`、**不调用 `fallbackToDestructiveMigration`**。
 */
@Database(
    entities = [
        SemesterEntity::class,
        CourseEntity::class,
        ClassSessionEntity::class,
        ImportBatchEntity::class,
    ],
    version = KebiaoDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KebiaoDatabase : RoomDatabase() {

    abstract fun semesterDao(): SemesterDao

    abstract fun courseDao(): CourseDao

    abstract fun classSessionDao(): ClassSessionDao

    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        /**
         * v1 -> v2：为 `class_sessions.import_batch_id` 补外键索引（见 [MIGRATION_1_2]）。
         * 升版必须同时提供显式迁移，**不得**改用 `fallbackToDestructiveMigration`。
         */
        const val VERSION: Int = 2

        const val NAME: String = "kebiao.db"

        fun create(
            context: Context,
            migrations: Array<Migration> = ALL_MIGRATIONS,
        ): KebiaoDatabase =
            Room.databaseBuilder(context.applicationContext, KebiaoDatabase::class.java, NAME)
                .addMigrations(*migrations)
                .addCallback(ForeignKeysCallback)
                // 此处刻意不出现 fallbackToDestructiveMigration（Spec 第 6 节硬约束）
                .build()
    }
}
