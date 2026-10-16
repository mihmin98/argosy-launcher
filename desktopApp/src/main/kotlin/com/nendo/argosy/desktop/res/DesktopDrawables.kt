package com.nendo.argosy.desktop.res

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import org.xml.sax.InputSource

object DesktopDrawables {

    fun vector(id: Int, density: Density): ImageVector {
        val path = GeneratedResources.drawablePath(id)
            ?: throw IllegalArgumentException("desktop: no drawable resource #0x%08x".format(id))
        val stream = DesktopDrawables::class.java.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("desktop: $path is not on the classpath")
        return stream.use { loadXmlImageVector(InputSource(it), density) }
    }
}
