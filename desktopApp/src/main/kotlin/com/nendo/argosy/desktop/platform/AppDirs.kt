package com.nendo.argosy.desktop.platform

import java.io.File

class AppDirs(
    val data: File,
    val config: File,
    val cache: File,
    val logs: File,
    val databases: File,
) {
    fun db(name: String): File = File(databases, name)
}
