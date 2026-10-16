package androidx.core.content

import android.content.Context

/**
 * Desktop stand-in for androidx.core's `Context.getSystemService()`, carrying only the members
 * upstream code calls.
 */
inline fun <reified T : Any> Context.getSystemService(): T? = getSystemService(T::class.java)
