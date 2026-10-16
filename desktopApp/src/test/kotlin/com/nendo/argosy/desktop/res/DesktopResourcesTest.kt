package com.nendo.argosy.desktop.res

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.nendo.argosy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

@OptIn(ExperimentalTestApi::class)
class DesktopResourcesTest {

    private class ResourcesContext(private val resources: Resources) : Context() {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = throw UnsupportedOperationException()
        override fun getResources(): Resources = resources
        override fun <T> getSystemService(serviceClass: Class<T>): T? = null
    }

    private fun context(languageTag: String) =
        ResourcesContext(DesktopResources(StringTable(Locale.forLanguageTag(languageTag))))

    @Test
    fun `stringResource renders the german and simplified chinese translations`() = runComposeUiTest {
        val rendered = mutableMapOf<String, String>()

        setContent {
            for (tag in listOf("de-DE", "zh-CN")) {
                CompositionLocalProvider(LocalContext provides context(tag)) {
                    rendered[tag] = stringResource(R.string.collections_options_remove_game, "Zelda")
                }
            }
        }

        waitForIdle()
        assertEquals("\"Zelda\" entfernen", rendered["de-DE"])
        assertEquals("移除“Zelda”", rendered["zh-CN"])
    }

    @Test
    fun `pluralStringResource picks the russian one few and many forms`() = runComposeUiTest {
        val counts = listOf(1, 2, 5, 11, 21, 22, 25)
        val rendered = mutableMapOf<Int, String>()

        setContent {
            CompositionLocalProvider(LocalContext provides context("ru")) {
                for (count in counts) {
                    rendered[count] = pluralStringResource(R.plurals.collections_game_count, count, count)
                }
            }
        }

        waitForIdle()
        assertEquals(
            listOf("1 игра", "2 игры", "5 игр", "11 игр", "21 игра", "22 игры", "25 игр"),
            counts.map { rendered.getValue(it) },
        )
    }

    @Test
    fun `painterResource draws an upstream vector drawable`() = runComposeUiTest {
        var painter: Painter? = null

        setContent {
            CompositionLocalProvider(LocalContext provides context("en")) {
                painter = painterResource(R.drawable.ic_check)
            }
        }

        waitForIdle()
        assertNotEquals(Size.Unspecified, painter!!.intrinsicSize)
        assertTrue(painter!!.intrinsicSize.width > 0f)
    }

    @Test
    fun `every drawable upstream code references loads as a vector`() {
        val upstream = File(System.getProperty("argosy.upstreamRoot"))
        val reference = Regex("""\bR\.drawable\.(\w+)""")
        val names = upstream.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> reference.findAll(file.readText()).map { it.groupValues[1] } }
            .toSortedSet()

        assertTrue(names.isNotEmpty())
        for (name in names) {
            val id = R.drawable::class.java.getField(name).getInt(null)
            val vector = DesktopDrawables.vector(id, Density(1f))
            assertTrue(name, vector.defaultWidth.value > 0f)
        }
    }

    @Test
    fun `locales without a table fall back to the base strings`() {
        val resources = DesktopResources(StringTable(Locale.JAPANESE))

        assertEquals("Remove \"Zelda\"", resources.getString(R.string.collections_options_remove_game, "Zelda"))
    }

    @Test
    fun `plural rules follow the requested locale even when the base table serves it`() {
        val japanese = DesktopResources(StringTable(Locale.JAPANESE))
        val polish = DesktopResources(StringTable(Locale.forLanguageTag("pl")))

        assertEquals("1 games", japanese.getQuantityString(R.plurals.collections_game_count, 1, 1))
        assertEquals("1 game", polish.getQuantityString(R.plurals.collections_game_count, 1, 1))
        assertEquals("2 games", polish.getQuantityString(R.plurals.collections_game_count, 2, 2))
    }

    @Test
    fun `strings are formatted only when arguments are passed`() {
        val resources = DesktopResources(StringTable(Locale.ENGLISH))

        assertEquals("%1\$d%%", resources.getString(R.string.downloads_file_state_percent))
        assertEquals("42%", resources.getString(R.string.downloads_file_state_percent, 42))
        assertEquals("%d game", resources.getQuantityString(R.plurals.collections_game_count, 1))
    }

    @Test
    fun `an unknown id fails with its hex value`() {
        val table = StringTable(Locale.ENGLISH)

        val error = assertThrows(IllegalArgumentException::class.java) { table.text(0x7f04ffff) }

        assertTrue(error.message!!.contains("0x7f04ffff"))
    }

    @Test
    fun `table resolution matches language and likely script`() {
        val available = GeneratedResources.locales

        assertEquals("de", StringTable.tableTag(Locale.forLanguageTag("de-AT"), available))
        assertEquals("zh-Hans", StringTable.tableTag(Locale.forLanguageTag("zh-CN"), available))
        assertEquals("zh-Hans", StringTable.tableTag(Locale.forLanguageTag("zh"), available))
        assertEquals("zh-Hant", StringTable.tableTag(Locale.forLanguageTag("zh-TW"), available))
        assertEquals("zh-Hant", StringTable.tableTag(Locale.forLanguageTag("zh-HK"), available))
        assertNull(StringTable.tableTag(Locale.forLanguageTag("en-US"), available))
        assertNull(StringTable.tableTag(Locale.forLanguageTag("sr-Latn"), listOf("sr")))
        assertEquals("sr", StringTable.tableTag(Locale.forLanguageTag("sr-RS"), listOf("sr")))
    }
}
