package com.nendo.argosy.desktop.db

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL

/**
 * The subset of `androidx.sqlite.db.SupportSQLiteDatabase` that upstream `Migrations.kt`
 * calls, with Android's binding rules.
 */
interface SupportSQLiteDatabase {
    fun execSQL(sql: String)
    fun execSQL(sql: String, bindArgs: Array<out Any?>)
    fun query(query: String): Cursor
}

/**
 * The subset of `android.database.Cursor` that upstream `Migrations.kt` calls. Rows are a
 * snapshot taken when the query runs, with Android's column type conversions.
 */
interface Cursor : AutoCloseable {
    fun moveToNext(): Boolean
    fun getColumnIndex(columnName: String): Int
    fun getColumnIndexOrThrow(columnName: String): Int
    fun isNull(columnIndex: Int): Boolean
    fun getLong(columnIndex: Int): Long
    fun getString(columnIndex: Int): String?
}

internal class ConnectionBackedDb(private val connection: SQLiteConnection) : SupportSQLiteDatabase {

    override fun execSQL(sql: String) {
        connection.execSQL(sql)
    }

    override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
        connection.prepare(sql).use { statement ->
            bindArgs.forEachIndexed { index, value -> statement.bindValue(index + 1, value) }
            statement.step()
        }
    }

    override fun query(query: String): Cursor = connection.prepare(query).use { statement ->
        val columns = List(statement.getColumnCount()) { statement.getColumnName(it) }
        val rows = buildList {
            while (statement.step()) add(List(columns.size) { statement.valueAt(it) })
        }
        SnapshotCursor(columns, rows)
    }

    private fun SQLiteStatement.bindValue(index: Int, value: Any?) {
        when (value) {
            null -> bindNull(index)
            is String -> bindText(index, value)
            is Boolean -> bindLong(index, if (value) 1L else 0L)
            is Double -> bindDouble(index, value)
            is Float -> bindDouble(index, value.toDouble())
            is Number -> bindLong(index, value.toLong())
            is ByteArray -> bindBlob(index, value)
            else -> bindText(index, value.toString())
        }
    }

    private fun SQLiteStatement.valueAt(index: Int): Any? = when (getColumnType(index)) {
        SQLITE_DATA_NULL -> null
        SQLITE_DATA_INTEGER -> getLong(index)
        SQLITE_DATA_FLOAT -> getDouble(index)
        SQLITE_DATA_BLOB -> getBlob(index)
        else -> getText(index)
    }
}

internal class SnapshotCursor(
    private val columns: List<String>,
    private val rows: List<List<Any?>>,
) : Cursor {

    private var position = -1

    override fun moveToNext(): Boolean {
        if (position < rows.size) position++
        return position < rows.size
    }

    override fun getColumnIndex(columnName: String): Int {
        val name = columnName.substringAfterLast('.')
        return columns.indexOfFirst { it.equals(name, ignoreCase = true) }
    }

    override fun getColumnIndexOrThrow(columnName: String): Int {
        val index = getColumnIndex(columnName)
        require(index >= 0) { "column '$columnName' does not exist. Available columns: $columns" }
        return index
    }

    override fun isNull(columnIndex: Int): Boolean = valueAt(columnIndex) == null

    override fun getLong(columnIndex: Int): Long = when (val value = valueAt(columnIndex)) {
        null -> 0L
        is Long -> value
        is Double -> value.toLong()
        is String -> value.trim().toLongOrNull() ?: 0L
        else -> throw IllegalStateException("cannot read a BLOB as Long at column $columnIndex")
    }

    override fun getString(columnIndex: Int): String? = when (val value = valueAt(columnIndex)) {
        null -> null
        is String -> value
        is Long, is Double -> value.toString()
        else -> throw IllegalStateException("cannot read a BLOB as String at column $columnIndex")
    }

    override fun close() = Unit

    private fun valueAt(columnIndex: Int): Any? {
        check(position in rows.indices) { "cursor is not on a row (position $position of ${rows.size})" }
        return rows[position][columnIndex]
    }
}
