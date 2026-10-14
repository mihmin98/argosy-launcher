package com.nendo.argosy.desktop.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.PlatformEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class ALauncherDatabaseJvmTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("argosy-db").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun open(): ALauncherDatabase =
        Room.databaseBuilder<ALauncherDatabase>(name = File(dir, "alauncher.db").path)
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun `upstream database opens on the jvm and round-trips an entity across reopen`() = runBlocking {
        val platform = PlatformEntity(
            id = 7,
            slug = "snes",
            name = "Super Nintendo",
            shortName = "SNES",
            romExtensions = "sfc,smc",
            lastScanned = Instant.parse("2026-10-14T12:00:00Z"),
        )

        val first = open()
        first.platformDao().insert(platform)
        first.close()

        val reopened = open()
        assertEquals(listOf(platform), reopened.platformDao().getAllBySlug("snes"))
        reopened.close()
    }
}
