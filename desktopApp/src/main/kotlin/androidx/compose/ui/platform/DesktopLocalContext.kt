package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Desktop stand-in for Compose Android's `LocalContext`: the desktop `Context` the root
 * composition provides.
 */
val LocalContext = staticCompositionLocalOf<Context> {
    error("CompositionLocal LocalContext not present")
}
