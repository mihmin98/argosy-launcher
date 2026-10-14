import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(
            project.extensions.getByType<VersionCatalogsExtension>()
                .named("desktopLibs").findLibrary("tomlj").get()
        )
    }
}

fun parseToml(name: String): TomlParseResult? {
    val source = file(name)
    if (!source.exists()) return null
    val result = Toml.parse(source.toPath())
    if (result.hasErrors()) {
        throw GradleException("desktopApp/$name: ${result.errors().joinToString("; ")}")
    }
    return result
}

fun TomlArray?.strings(): List<String> =
    this?.let { array -> (0 until array.size()).map { array.getString(it) } }.orEmpty()

val manifest = parseToml("overlay.toml")
val shadows = parseToml("shadows.toml")

val excludedDirs = manifest?.getArray("exclude.dirs").strings().toSet()
val includedBack = manifest?.getArray("include_back.files").strings().toSet()
val replacedFiles = shadows?.getArray("shadow")?.let { entries ->
    (0 until entries.size()).map { entries.getTable(it).getString("upstream")!! }
}.orEmpty()
val excludedFiles = (manifest?.getArray("exclude.files").strings().orEmpty() + replacedFiles).toSet()

val isExcluded: (String) -> Boolean = { path ->
    when {
        path in includedBack -> false
        includedBack.any { it.startsWith("$path/") } -> false
        path in excludedFiles -> true
        else -> excludedDirs.any { path == it || path.startsWith("$it/") }
    }
}

extra["overlay.isExcluded"] = isExcluded
