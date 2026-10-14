package com.nendo.argosy.desktop.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomSchemaParityTest {

    private fun schemaDir(property: String) = File(System.getProperty(property))

    @Test
    fun `desktop exports the same schema as upstream's newest committed version`() {
        val upstream = schemaDir("argosy.upstreamSchemas").listFiles { file -> file.extension == "json" }
            .orEmpty()
            .maxBy { it.nameWithoutExtension.toInt() }
        val desktop = File(schemaDir("argosy.desktopSchemas"), upstream.name)

        assertTrue("desktop did not export ${upstream.name}", desktop.isFile)
        assertEquals(upstream.readText(), desktop.readText())
    }
}
