package com.kebiao.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移登记点 —— **唯一登记处**。
 *
 * Spec 第 6 节的硬约束：迁移用 `Migration` 显式声明，**禁止 `fallbackToDestructiveMigration`**
 * （个人项目丢了课表比崩溃更痛）。
 *
 * 当前 [KebiaoDatabase.VERSION] = 2，已登记 [MIGRATION_1_2]。
 *
 * 下次升版照抄下面模板：
 *
 * ```
 * private val MIGRATION_2_3 = object : Migration(2, 3) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE class_sessions ADD COLUMN modified_by TEXT")
 *     }
 * }
 * ```
 *
 * **回滚（down）说明**：Room 的 `Migration` 只描述 up。按 Spec 第 6 节"迁移必须可回滚"，
 * 各步 down 的等价脚本登记在此，回滚时手工执行：
 *
 * ```
 * -- 2 -> 1 的 down：
 * DROP INDEX IF EXISTS idx_sessions_batch;
 * ```
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 为 class_sessions.import_batch_id 补外键索引。
        //
        // 起因：Room 编译期警告 "import_batch_id column references a foreign key but it is
        // not part of an index" —— FK 子列无索引时，父行（import_batches）删除/更新
        // 会全表扫描 class_sessions。
        //
        // 约束：本 SQL 生成的对象必须与 Room 由实体生成的目标 schema 一致
        // （索引名 idx_sessions_batch / 非唯一 / 列 import_batch_id，见 v2 schema JSON 的
        // indices[].createSql），否则打开旧库时 schema 校验会失败并抛错。
        // 用 IF NOT EXISTS 保证迁移幂等（重复执行不炸）。
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_batch ON class_sessions(import_batch_id)")
    }
}

/** 全部迁移，按版本升序登记。 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

/**
 * 打开数据库时显式开启外键约束。
 *
 * `ON DELETE CASCADE` / `SET NULL`（Spec 第 6 节）只有在外键约束打开时才生效；
 * 不依赖 Room 的隐式行为，显式 `PRAGMA` 一次，避免"删了学期但课次还在"的脏数据。
 */
val ForeignKeysCallback: androidx.room.RoomDatabase.Callback = object : androidx.room.RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
