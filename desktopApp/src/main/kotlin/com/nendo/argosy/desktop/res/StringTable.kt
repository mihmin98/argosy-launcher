package com.nendo.argosy.desktop.res

import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.ULocale
import java.io.DataInputStream
import java.io.InputStream
import java.util.Locale

/**
 * Upstream string and plural resources for [locale]: its translation table when one matches,
 * else the base table, resolved and pluralised as Android resolves them.
 */
class StringTable(
    val locale: Locale,
    open: (String) -> InputStream? = { StringTable::class.java.classLoader.getResourceAsStream(it) },
) {
    private val base = Table.read(open, BASE) ?: throw IllegalStateException("desktop: missing $TABLE_DIR/$BASE.bin")
    private val translated = tableTag(locale, GeneratedResources.locales)?.let { Table.read(open, it) }
    private val pluralRules = PluralRules.forLocale(locale)

    fun text(id: Int): String =
        translated?.strings?.get(id) ?: base.strings[id] ?: throw notFound("string", id)

    fun quantityText(id: Int, quantity: Int): String {
        val forms = translated?.plurals?.get(id) ?: base.plurals[id] ?: throw notFound("plurals", id)
        return forms[pluralRules.select(quantity.toDouble())] ?: forms[OTHER] ?: throw notFound("plurals", id)
    }

    private fun notFound(kind: String, id: Int) =
        IllegalArgumentException("desktop: no $kind resource #0x%08x".format(id))

    private class Table(val strings: Map<Int, String>, val plurals: Map<Int, Map<String, String>>) {
        companion object {
            fun read(open: (String) -> InputStream?, tag: String): Table? =
                open("$TABLE_DIR/$tag.bin")?.use { DataInputStream(it.buffered()).readTable() }

            private fun DataInputStream.readTable(): Table {
                val magic = ByteArray(4).also(::readFully)
                check(magic.decodeToString() == MAGIC && readInt() == VERSION) { "desktop: bad string table" }
                val strings = HashMap<Int, String>()
                val plurals = HashMap<Int, Map<String, String>>()
                repeat(readInt()) {
                    val id = readInt()
                    if (readByte().toInt() == KIND_STRING) {
                        strings[id] = readText()
                    } else {
                        plurals[id] = (0 until readByte()).associate { QUANTITIES[readByte().toInt()] to readText() }
                    }
                }
                return Table(strings, plurals)
            }

            private fun DataInputStream.readText(): String =
                ByteArray(readInt()).also(::readFully).decodeToString()
        }
    }

    companion object {
        private const val TABLE_DIR = "argosy/strings"
        private const val BASE = "base"
        private const val MAGIC = "ARGS"
        private const val VERSION = 1
        private const val KIND_STRING = 0
        private const val OTHER = "other"
        private val QUANTITIES = listOf("zero", "one", "two", "few", "many", OTHER)

        /**
         * The translation table serving [locale]: its exact tag, else its language and likely
         * script, else its language when that table's likely script is the same. Null means the
         * base table.
         */
        fun tableTag(locale: Locale, available: Collection<String>): String? {
            val script = likelyScript(ULocale.forLocale(locale))
            return listOf(locale.toLanguageTag(), "${locale.language}-$script", locale.language)
                .firstOrNull { it in available && likelyScript(ULocale.forLanguageTag(it)) == script }
        }

        private fun likelyScript(locale: ULocale): String = ULocale.addLikelySubtags(locale).script
    }
}
