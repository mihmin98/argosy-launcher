package androidx.compose.ui.res

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import com.nendo.argosy.desktop.res.DesktopDrawables

/**
 * Desktop stand-in for Compose Android's `painterResource(id)`. Vector drawables only, as on
 * Android for XML resources.
 */
@Composable
fun painterResource(@DrawableRes id: Int): Painter {
    val density = LocalDensity.current
    val vector = remember(id, density) { DesktopDrawables.vector(id, density) }
    return rememberVectorPainter(vector)
}
