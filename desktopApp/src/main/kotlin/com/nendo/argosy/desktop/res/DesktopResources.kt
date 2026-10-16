package com.nendo.argosy.desktop.res

import android.content.res.Resources

/**
 * The desktop `Resources`: text from [table], formatted with `String.format` in the table's
 * locale, as Android formats resource strings.
 */
class DesktopResources(private val table: StringTable) : Resources() {

    override fun getString(id: Int): String = table.text(id)

    override fun getString(id: Int, vararg formatArgs: Any?): String =
        String.format(table.locale, table.text(id), *formatArgs)

    override fun getQuantityString(id: Int, quantity: Int): String = table.quantityText(id, quantity)

    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String =
        String.format(table.locale, table.quantityText(id, quantity), *formatArgs)
}
