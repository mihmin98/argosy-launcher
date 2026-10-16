package dagger.hilt.android

import android.content.Context
import com.nendo.argosy.desktop.di.DesktopComponentOwner

/**
 * Desktop stand-in for Hilt's `EntryPointAccessors`, carrying only the members upstream code
 * calls. The application context is a [DesktopComponentOwner]; its component implements every
 * compiled `@EntryPoint` through the generated `DesktopEntryPoints`.
 */
object EntryPointAccessors {

    @JvmStatic
    fun <T : Any> fromApplication(context: Context, entryPoint: Class<T>): T {
        val owner = context.applicationContext as? DesktopComponentOwner
            ?: throw IllegalStateException("desktop: ${context.applicationContext} is not a DesktopComponentOwner")
        val component = owner.component
        check(entryPoint.isInstance(component)) {
            "desktop: ${component.javaClass.name} does not implement entry point ${entryPoint.name}"
        }
        return entryPoint.cast(component)
    }
}
