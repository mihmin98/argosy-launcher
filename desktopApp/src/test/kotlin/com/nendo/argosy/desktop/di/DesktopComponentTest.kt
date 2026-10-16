package com.nendo.argosy.desktop.di

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.SavedStateHandle
import com.nendo.argosy.data.repository.DocumentHighlight
import com.nendo.argosy.data.repository.DocumentHighlightStore
import com.nendo.argosy.desktop.platform.AppDirs
import com.nendo.argosy.ui.components.LaunchOverlayViewModel
import dagger.BindsInstance
import dagger.Component
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.inject.Singleton

class DesktopComponentTest {

    @Singleton
    @Component(modules = [DesktopDatabaseModule::class])
    interface UpstreamInjectionComponent {
        fun documentHighlightStore(): DocumentHighlightStore

        @Component.Factory
        interface Factory {
            fun create(
                @BindsInstance @ApplicationContext context: Context,
                @BindsInstance appDirs: AppDirs,
            ): UpstreamInjectionComponent
        }
    }

    private class FilesDirContext(private val filesDir: File) : Context() {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = filesDir
        override fun <T> getSystemService(serviceClass: Class<T>): T? = null
        override fun getResources(): Resources = throw UnsupportedOperationException()
    }

    private lateinit var root: File
    private lateinit var appDirs: AppDirs
    private lateinit var context: Context

    @Before
    fun setUp() {
        root = Files.createTempDirectory("argosy-di").toFile()
        appDirs = AppDirs(
            data = File(root, "data"),
            config = File(root, "config"),
            cache = File(root, "cache"),
            logs = File(root, "logs"),
            databases = File(root, "data/databases").apply { mkdirs() },
        )
        context = FilesDirContext(File(root, "files"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `desktop component serves a working game dao backed by the app dirs database`() = runBlocking {
        val component = DaggerDesktopComponent.factory().create(context, appDirs)

        assertNull(component.gameDao().getById(1L))
        assertTrue(appDirs.db("alauncher.db").isFile)
    }

    @Test
    fun `desktop component binds every compiled hilt view model`() {
        val component = DaggerDesktopComponent.factory().create(context, appDirs)

        val viewModels = component.viewModelComponentFactory().create(SavedStateHandle()).viewModels()

        assertEquals(setOf(LaunchOverlayViewModel::class.java), viewModels.keys)
    }

    @Test
    fun `upstream inject constructor receives the application context binding as a singleton`() = runBlocking {
        val component = DaggerDesktopComponentTest_UpstreamInjectionComponent.factory().create(context, appDirs)
        val store = component.documentHighlightStore()
        val highlights = listOf(DocumentHighlight(3..5, colorIndex = 2))

        store.save("manual", highlights)

        assertSame(store, component.documentHighlightStore())
        assertEquals(highlights, store.load("manual"))
        assertTrue(File(root, "files/document_highlights/manual.txt").isFile)
    }
}
