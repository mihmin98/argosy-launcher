package com.nendo.argosy.desktop.data.local

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.migrations.MigrationRegistry
import com.nendo.argosy.util.SearchNormalizer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MigrationReplayTest {

    private lateinit var dir: File
    private val schemaRoot = File(System.getProperty("argosy.schemaRoot"))

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("argosy-migrations").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun schema(packageDir: String, version: Int): JSONObject =
        JSONObject(File(schemaRoot, "$packageDir/$version.json").readText()).getJSONObject("database")

    private fun latestSchema(): JSONObject {
        val dir = File(schemaRoot, "com.nendo.argosy.data.local.ALauncherDatabase")
        val latest = dir.listFiles { file -> file.extension == "json" }.orEmpty().maxOf { it.nameWithoutExtension.toInt() }
        return schema(dir.name, latest)
    }

    private fun JSONObject.objects(key: String): List<JSONObject> =
        optJSONArray(key)?.let { array -> List(array.length()) { array.getJSONObject(it) } }.orEmpty()

    private fun JSONObject.strings(key: String): List<String> =
        optJSONArray(key)?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty()

    private fun SQLiteConnection.createFrom(schema: JSONObject) {
        schema.objects("entities").forEach { entity ->
            val table = entity.getString("tableName")
            execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            entity.objects("indices").forEach { execSQL(it.getString("createSql").replace("\${TABLE_NAME}", table)) }
        }
        schema.objects("views").forEach { view ->
            execSQL(view.getString("createSql").replace("\${VIEW_NAME}", view.getString("viewName")))
        }
        schema.strings("setupQueries").forEach { execSQL(it) }
        execSQL("PRAGMA user_version = ${schema.getInt("version")}")
    }

    private fun SQLiteConnection.insert(schema: JSONObject, table: String, values: Map<String, Any?>) {
        val fields = schema.objects("entities").single { it.getString("tableName") == table }.objects("fields")
        val row = fields.associate { field ->
            val column = field.getString("columnName")
            val placeholder = when (field.getString("affinity")) {
                "INTEGER" -> 0L
                "REAL" -> 0.0
                else -> ""
            }
            val required = field.getBoolean("notNull") && !field.has("defaultValue")
            column to values.getOrElse(column) { if (required) placeholder else null }
        }
        val sql = "INSERT INTO `$table` (${row.keys.joinToString { "`$it`" }}) VALUES (${row.keys.joinToString { "?" }})"
        prepare(sql).use { statement ->
            row.values.forEachIndexed { index, value ->
                when (value) {
                    null -> statement.bindNull(index + 1)
                    is Long -> statement.bindLong(index + 1, value)
                    is Double -> statement.bindDouble(index + 1, value)
                    else -> statement.bindText(index + 1, value.toString())
                }
            }
            statement.step()
        }
    }

    private suspend fun ALauncherDatabase.rows(sql: String): List<List<String?>> = useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            buildList {
                while (statement.step()) {
                    add(List(statement.getColumnCount()) { if (statement.isNull(it)) null else statement.getText(it) })
                }
            }
        }
    }

    @Test
    fun `upstream migrations replay from the first argosy schema to the latest committed schema`() = runBlocking {
        val v6 = schema("com.nendo.argosy.data.local.ALauncherDatabase", 6)
        val file = File(dir, "alauncher.db")
        BundledSQLiteDriver().open(file.path).use { connection ->
            connection.createFrom(v6)
            connection.insert(v6, "platforms", mapOf("id" to "snes", "name" to "Super Nintendo"))
            connection.insert(
                v6, "games",
                mapOf(
                    "id" to 1L,
                    "platformId" to "snes",
                    "title" to "Chrono Trigger",
                    "sortTitle" to "chrono trigger",
                    "screenshotPaths" to "https://img/1.png,https://img/2.png",
                ),
            )
        }

        val db = Room.databaseBuilder<ALauncherDatabase>(name = file.path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*MigrationRegistry.ARRAY)
            .build()
        try {
            val latest = latestSchema()
            assertEquals(listOf(listOf(latest.getInt("version").toString())), db.rows("PRAGMA user_version"))

            val tables = db.rows(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                    "AND name NOT IN ('room_master_table', 'android_metadata')"
            ).map { it.single() }.toSet()
            assertEquals(latest.objects("entities").map { it.getString("tableName") }.toSet(), tables)

            assertEquals(
                listOf(listOf("Chrono Trigger", SearchNormalizer.normalize("Chrono Trigger"))),
                db.rows("SELECT title, searchTitle FROM games"),
            )
            assertEquals(
                listOf(listOf("1", "0", "https://img/1.png", null), listOf("1", "1", "https://img/2.png", null)),
                db.rows("SELECT gameId, position, sourceUrl, cachedPath FROM game_screenshots ORDER BY position"),
            )
        } finally {
            db.close()
        }
    }
}
